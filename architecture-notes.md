# SSP Architecture Notes

## CharacterState — SQL vs RAG Fields

### Fields stored in SQL AND embedded in pgvector
| Field | Reason for SQL | Reason for RAG |
|-------|---------------|----------------|
| `emotionalState` | Read by `ContextBuilderService` SQL enrichment path | Semantic similarity search |
| `physicalState` | Read by `ContextBuilderService` SQL enrichment path | Semantic similarity search |
| `currentGoal` | Read by `ContextBuilderService` SQL enrichment path | Semantic similarity search |
| `arcStage` | Read by `ContextBuilderService` SQL enrichment path | Semantic similarity search |
| `narrativeNotes` | Free-form notes | Semantic similarity search |
| `dialogueSummary` | Read by `ContextBuilderService` SQL enrichment path | Semantic similarity search |

### Fields stored in SQL but value is primarily in pgvector
| Field | Note |
|-------|------|
| `dialogueEmotionType` | Enum value captured in embedded text. SQL value is redundant now — only becomes useful when a UI emotional arc graph is built (plot character emotion across chapters). Do NOT add to `ContextBuilderService` enrichment — the string label adds less than the text description already in `emotionalState`. |
| `dialogueEmotionIntensity` | Float captured in embedded text. Same as above — useful for future UI analytics (emotional intensity curve per character), not needed in SQL queries today. |

### Decision: keep `dialogueEmotionType` + `dialogueEmotionIntensity` in SQL
They are kept as SQL columns for future UI use (emotional arc graph across chapters).
When that feature is built, add a query endpoint like:
`GET /api/projects/{projectId}/characters/{characterId}/emotion-arc`
returning `(chapterNumber, dialogueEmotionType, dialogueEmotionIntensity)` ordered by chapter.

---

## ContextBuilderService — Two Paths for CharacterState

```
buildRagCharacterBlock()
  ├── Path 1: pgvector semantic search → finds relevant characters by text similarity
  └── Path 2: SQL enrichment → for each character found, reads CharacterState
               via findLatestStateAtOrBefore() to get temporal state at the right chapter
               Reads: emotionalState, currentGoal, arcStage, dialogueSummary
               Used for: flashback-aware context (ch.30 flashback → retrieves ch.5 state)
```

---

## SpeakerDetectionService — What It Saves

After the AI call, `persistDialogueData()` writes back to the CharacterState created in Step 1
(CharacterExtraction) of the same chapter:
- `dialogueEmotionType` — dominant emotion enum
- `dialogueEmotionIntensity` — 0.0–1.0
- `dialogueSummary` — how the character speaks in this chapter

Then **re-embeds** the CharacterState so pgvector gets the enriched vector.

This is what makes flashback translation accurate at scale:
- Chapter 5 CharacterState vector: "naive, hopeful, speaks openly and enthusiastically"
- Chapter 30 CharacterState vector: "battle-hardened, clipped speech, deflects personal questions"
- When ch.30 has a flashback to ch.5 → RAG retrieves ch.5 state → translator uses the RIGHT voice