# Translation Token Cost Optimization Plan

> Status: PLANNED — not yet implemented. Review before acting.

---

## Background

The two-pass translation pipeline sends significant redundant data to the AI provider on every chunk.
For an 8,000-char Japanese chapter, roughly **~12,900 tokens per chunk are redundant** — meaning you
pay for them without meaningful quality gain. For a 4-segment chapter this compounds to ~51,600 wasted
tokens, roughly equal to the cost of the translation output itself.

---

## Identified Wastages

### Wastage 1 — Source Text Sent Twice (LARGEST, ~12,000 tokens/chunk)

**Where:** `TranslationService.buildLiteraryUserPrompt()` line ~311–312

**What happens:**
- Pass 1 receives the full source chunk (~12,000 tokens for 8,000 Japanese chars)
- Pass 2 also receives the same source chunk again under `## Source text` — as a reference for "author intent"
- The faithful draft already encodes that intent accurately (that is Pass 1's entire job)
- In practice Pass 2 primarily rewrites the draft, rarely needing to re-read the original

**Wasted tokens per chunk:** ~12,000
**Wasted tokens for a 4-segment chapter:** ~48,000

---

### Wastage 2 — Glossary Sent to Both Passes (~300 tokens/chunk)

**Where:** `buildFaithfulSystemPrompt()` and `buildLiterarySystemPrompt()` both include `ctx.glossaryBlock()`

**What happens:**
- RAG retrieves up to 10 glossary terms per chunk
- Both passes independently receive the full glossary block
- Pass 1's job is to enforce terminology — it needs the glossary
- Pass 2's job is literary polish — it should respect enforced terms but doesn't need to re-verify them

**Wasted tokens per chunk:** ~300
**Wasted tokens for a 4-segment chapter:** ~1,200

---

### Wastage 3 — Characters Sent to Both Passes (~600–900 tokens/chunk)

**Where:** `buildFaithfulSystemPrompt()` and `buildLiterarySystemPrompt()` both include `ctx.characterBlock()`

**What happens:**
- RAG retrieves up to 6 characters, each with description, traits, voice example, and temporal state
- Both passes independently receive the full character block
- Pass 1 needs character names/voice for accuracy
- Pass 2 needs voice examples and state for literary register — but the description and traits are largely redundant if the faithful draft already captured the voice

**Wasted tokens per chunk:** ~600–900
**Wasted tokens for a 4-segment chapter:** ~2,400–3,600

---

## Proposed Optimizations

---

### Option A — Remove Source Text from Pass 2 User Prompt
**Impact: HIGH | Risk: LOW | Effort: TRIVIAL**

Remove `ctx.textToTranslate()` from `buildLiteraryUserPrompt()`. Pass 2 receives only the
faithful draft as its raw material.

```
CURRENT Pass 2 user prompt:
  ## Source text (original Japanese — for author intent):
  [~12,000 tokens of Japanese]

  ## Faithful draft:
  [~6,000 tokens of English]

PROPOSED:
  ## Faithful draft:
  [~6,000 tokens of English]
```

**Savings:** ~12,000 tokens per chunk (~35% of total Pass 2 input)

**Quality trade-off:**
- Pass 2 can no longer catch cases where Pass 1 silently dropped a nuance
- In practice, Pass 1 is instructed to be exhaustive ("Translate every sentence. Miss nothing.") so
  silent drops should be rare
- If quality degradation is observed, implement Option B instead

**File to change:** `TranslationService.buildLiteraryUserPrompt()` — remove the
`## Source text` section and its `ctx.textToTranslate()` argument from the format string.

---

### Option B — Compressed Source Anchor in Pass 2 (Alternative to A)
**Impact: HIGH | Risk: VERY LOW | Effort: SMALL**

Instead of removing the source entirely, send only the first ~300 chars of the source chunk
as a tonal/intent anchor, truncated with an ellipsis.

```
## Source text anchor (first 300 chars — for tonal reference only):
「……そうか、俺はもう死ぬのか」鈴木健一は、トラックに轢かれながら…[truncated]
```

**Savings:** ~11,600 tokens per chunk (saves ~97% of the wastage from Option A, with minimal
quality loss since the intent signal is preserved)

**File to change:** `TranslationService.buildLiteraryUserPrompt()` — add a `sourceAnchor`
parameter: `ctx.textToTranslate().substring(0, Math.min(300, ctx.textToTranslate().length()))`

---

### Option C — Strip Characters/Glossary from Pass 1 System Prompt
**Impact: LOW-MEDIUM | Risk: LOW | Effort: SMALL**

Pass 1 is accuracy-focused. The glossary is critical for it (enforced terms). Characters are
less critical — Pass 1 mainly needs them for name consistency, not literary voice.

Proposed split:
- **Pass 1 keeps:** glossary (enforced terms enforcement), character names only (stripped down)
- **Pass 2 keeps:** full character block with voice examples, traits, temporal state

This means building a lighter character block for Pass 1 — just names and roles, no descriptions
or voice examples (~15 tokens/char vs ~80 tokens/char).

```
CURRENT Pass 1 characters block (6 chars × 80 tokens): ~480 tokens
PROPOSED Pass 1 characters block (6 chars × 15 tokens): ~90 tokens
Savings: ~390 tokens/chunk
```

**Files to change:**
- `ContextBuilderService` — add `formatCharacterNamesOnly(List<Character>)` method
- `TranslationService.buildFaithfulSystemPrompt()` — use the slim character block

---

### Option D — Auto Single-Pass for Short Segments
**Impact: MEDIUM | Risk: VERY LOW | Effort: SMALL**

For text chunks under a configurable character threshold (e.g., 1,500 chars), a single literary
pass is sufficient. Short segments are typically scene transitions, chapter headers, brief
exchanges — not the dense interior monologue that benefits from two-pass processing.

```
CURRENT: ALL chunks go through Pass 1 + Pass 2 (2 AI calls each)
PROPOSED: chunks < SHORT_SEGMENT_THRESHOLD → single Pass 2 only (1 AI call each)
```

**Savings:** Halves the cost for short segments. In a typical chapter, 1–2 segments may
qualify (transition/header segments), saving ~6,000–25,000 tokens.

**Configuration:** Add `ssp.ai.translation.single-pass-threshold-chars: 1500` to
`application.yaml`. Default to 0 (disabled) to avoid changing behaviour without opt-in.

**Files to change:**
- `TranslationService` — add `@Value("${ssp.ai.translation.single-pass-threshold-chars:0}")`
  and check in `translateText()`: if `text.length() < threshold`, call `singlePassTranslation()`

---

### Option E — Cache Context Blocks Across Segments of the Same Chapter
**Impact: LOW | Risk: LOW | Effort: MEDIUM**

Currently `contextBuilder.buildTranslationContext()` is called once per segment, triggering
3 RAG embedding queries per segment. For a 4-segment chapter, that is 12 embedding calls, all
on the same project. The glossary and character sets don't change between segments of the same
chapter.

**Savings:** Eliminates 9 of 12 embedding API calls for a 4-segment chapter. Embedding is cheap
($0.02/1M tokens) so monetary savings are minimal, but latency savings are real — each
`similaritySearch()` call has network round-trip overhead.

**Files to change:**
- `TranslationService.translateChapterAsync()` — build context once, store in a local variable,
  pass pre-built context into `translateText()` instead of rebuilding per segment
- Requires adding a `translateText(Chapter, String, LlmProvider, TranslationContext)` overload

---

## Recommended Implementation Order

If you decide to implement, do it in this order (highest impact + lowest risk first):

| Priority | Option | Tokens saved/chunk | Risk | Effort |
|---|---|---|---|---|
| 1 | **B** (compressed source anchor) | ~11,600 | Very Low | 30 min |
| 2 | **D** (auto single-pass for short segments) | ~25,000 for qualifying segments | Very Low | 1 hour |
| 3 | **C** (slim Pass 1 character block) | ~390 | Low | 1 hour |
| 4 | **E** (cache context across segments) | latency only | Low | 2 hours |
| — | **A** (remove source entirely) | ~12,000 | Low-Medium | 15 min |

Option A is listed last not because of effort, but because Option B achieves nearly the same
saving with less quality risk. Choose A only if you are confident in Pass 1's reliability.

---

## Combined Savings Projection

If Options B + C + D are all implemented (realistic scenario):

| Scenario | Current tokens/chunk | Optimized tokens/chunk | Saving |
|---|---|---|---|
| Min (1,000 chars, no context) | ~6,500 | ~4,200 | ~35% |
| Avg (8,000 chars, moderate context) | ~42,400 | ~27,000 | ~36% |
| Max (8,000 chars, full context) | ~49,900 | ~32,000 | ~36% |
| Avg × 4-segment chapter | ~169,600 | ~108,000 | ~36% |

At GPT-4o pricing ($2.50/1M input, $10/1M output), a 4-segment average chapter drops from
**~$1.14 → ~$0.73** per chapter. At scale (1,000 chapters), that is ~$410 saved.

---

## Files Affected (if all options implemented)

| File | Change |
|---|---|
| `TranslationService.java` | Options A/B, C (Pass 1 char block), D (threshold check) |
| `ContextBuilderService.java` | Option C (slim character formatter), Option E (context reuse) |
| `application.yaml` | Option D (threshold config property) |