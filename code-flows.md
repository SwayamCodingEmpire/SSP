# SSP Code Flows

Three primary user-triggered operations and every class/method they touch.

---

## Flow 1 — Upload Chapter

**Trigger**: `POST /api/chapters/upload` (multipart file) or `/process` (JSON) or `/process-text` (plain text)

```
HTTP Request
    │
    ▼
ChapterProcessingController
    ├─ [upload]   detectFormat(filename) → FileFormat enum
    │             documentTextExtractor.extractText(inputStream, format)
    │                 └─► TikaDocumentExtractor.extractText()
    │                         AutoDetectParser (Apache Tika)
    │                         BodyContentHandler → plain text
    │
    └─ [all paths] chapterProcessingService.processChapter(projectId, chapterNumber, title, text)
                        │
                        ▼
                   ChapterProcessingService.processChapter()
                        ├─ projectRepository.findById()            → verify project exists
                        ├─ new Chapter()
                        │       .setOriginalText(text)
                        │       .setStatus(PARSED)
                        │       .setTranslationStatus(PENDING)
                        │       .setAnalysisStatus(PENDING)        ← state machine starts here
                        ├─ chapterRepository.save(chapter)         → INSERT chapters row
                        │
                        ├─ [if ssp.ai.analysis.enabled=true]
                        │       chapterAnalysisOrchestrator
                        │           .analyzeChapterAsync(chapter.id)   ← fires async, does NOT block
                        │
                        └─ buildResponse(chapter)
                                └─ new ChapterProcessingResponse(
                                        id, chapterNumber, title, status,
                                        translationStatus, analysisStatus,
                                        preview, fullOriginalText)

HTTP Response: 200 OK — ChapterProcessingResponse
```

**What is persisted**: One `Chapter` row with `analysisStatus=PENDING`.
**What is NOT done yet**: No AI calls, no characters, no embeddings.

---

## Flow 2 — Analyze Chapter

**Trigger**: `POST /api/analysis/chapters/{id}` (manual) or auto-triggered on upload if `enabled=true`

```
HTTP Request (or auto-trigger from ChapterProcessingService)
    │
    ▼
AnalysisController.triggerAnalysis()
    └─ chapterAnalysisOrchestrator.analyzeChapterAsync(chapterId)
            │  [runs on aiTaskExecutor thread pool — async]
            │
            ▼
    ChapterAnalysisOrchestrator.analyzeChapterAsync()
            ├─ chapter.setAnalysisStatus(ANALYZING) + save
            │
            ├─ ═══════════════════════════════════════
            │  STEP 1: Character Extraction
            │  ═══════════════════════════════════════
            │
            ├─ characterExtractionService.extractCharacters(chapter)
            │       │
            │       ├─ contextBuilder.buildCharacterExtractionContext(chapter)
            │       │       ├─ characterRepository.findByProjectId()   → all known characters
            │       │       └─ Builds prompt:
            │       │               "## Chapter N: Title"
            │       │               "## Previously known characters:"
            │       │               "- Name [also known as: X,Y] (ROLE): description"
            │       │               "## Chapter text: <full text>"
            │       │
            │       ├─ BeanOutputConverter<CharacterExtractionResult>
            │       │
            │       ├─ providerRegistry.resolve(null)                  → OpenAI or Anthropic
            │       │       └─ LlmProvider.generate(SYSTEM_PROMPT, context + format)
            │       │               Returns JSON: { characters: [...], relationships: [...] }
            │       │
            │       ├─ converter.convert(response)  → CharacterExtractionResult
            │       │
            │       ├─ FOR EACH ExtractedCharacter:
            │       │       │
            │       │       ├─ upsertCharacter(chapter, extracted)
            │       │       │       ├─ characterRepository.findByProjectIdAndName()
            │       │       │       ├─ [exists] update description, traits, translatedName
            │       │       │       │           mergeAlias() for each new alias
            │       │       │       └─ [new]    INSERT Character (name, aliases, role, traits,
            │       │       │                   translatedName, voiceExample, firstAppearanceChapter)
            │       │       │
            │       │       ├─ createCharacterState(chapter, character, extracted)
            │       │       │       └─ INSERT CharacterState (
            │       │       │               character_id, chapter_id, chapterNumber,
            │       │       │               emotionalState, currentGoal, arcStage,
            │       │       │               affiliation, loyalty)
            │       │       │
            │       │       └─ embeddingService.embedCharacter(character)  [async]
            │       │               ├─ buildCharacterText():
            │       │               │       "Character: Name (also known as: X,Y) | Role: ... |
            │       │               │        Description: ... | Traits: ... | Voice: ..."
            │       │               └─ vectorStore.add(Document(uuid, text, {
            │       │                       type:"character", project_id, entity_id, name}))
            │       │
            │       └─ FOR EACH ExtractedRelationship:
            │               │
            │               ├─ resolveCharacter(name) for both sides
            │               │       → checks chapterCharacters list first, then DB
            │               │
            │               ├─ relationshipRepository.findByCharacterPair(c1, c2)
            │               │
            │               ├─ [exists] UPDATE CharacterRelationship (type, description, affinity)
            │               └─ [new]    INSERT CharacterRelationship (c1, c2, type, desc, affinity,
            │                                                          establishedAtChapter)
            │               │
            │               ├─ INSERT RelationshipState (
            │               │       relationship_id, chapter_id, chapterNumber,
            │               │       type, description, affinity, dynamicsNote)
            │               │
            │               └─ embeddingService.embedRelationshipState(state)  [async]
            │                       ├─ buildRelationshipStateText():
            │                       │       "Relationship: A <-> B at ch.N | Type: ALLY |
            │                       │        Affinity: 0.8 | Description: ... | Dynamics: ..."
            │                       └─ vectorStore.add(Document(uuid, text, {
            │                               type:"relationship_state", project_id,
            │                               relationship_id, chapter_number}))
            │
            ├─ ═══════════════════════════════════════
            │  STEP 2: Speaker Detection
            │  ═══════════════════════════════════════
            │
            ├─ speakerDetectionService.detectSpeakers(chapter)
            │       │
            │       ├─ characterRepository.findByProjectId()  → all known characters
            │       │
            │       ├─ contextBuilder.buildSpeakerDetectionContext(chapter, characters)
            │       │       Builds: "## Known characters:\n- Name | Role | Traits\n
            │       │                ## Chapter N text: <full text>"
            │       │
            │       ├─ LlmProvider.generate(SYSTEM_PROMPT, context)
            │       │       Returns JSON: { characterDialogues: [
            │       │               { characterName, emotionType, emotionIntensity, dialogueSummary }
            │       │       ]}
            │       │
            │       └─ persistDialogueData(chapter, result)
            │               FOR EACH CharacterDialogue:
            │               ├─ characterRepository.findByProjectIdAndName()
            │               ├─ characterStateRepository.findByCharacterIdAndChapterId()
            │               │       → finds the state created in Step 1
            │               ├─ state.setDialogueEmotionType(...)
            │               │   state.setDialogueEmotionIntensity(...)
            │               │   state.setDialogueSummary(...)
            │               ├─ characterStateRepository.save(state)  → UPDATE
            │               └─ embeddingService.embedCharacterState(state)  [async]
            │                       ├─ buildCharacterStateText():
            │                       │       "Character: Name at ch.N | Emotional: ... | Goal: ... |
            │                       │        Arc: ... | Affiliation: ... | Loyalty: ... |
            │                       │        Dialogue emotion: ANGRY (0.9) | Voice: ..."
            │                       └─ vectorStore.delete(old) + add(new Document)
            │
            ├─ ═══════════════════════════════════════
            │  STEP 3: Scene Analysis
            │  ═══════════════════════════════════════
            │
            └─ sceneAnalysisService.analyzeScenes(chapter)
                    │
                    ├─ contextBuilder.buildSceneAnalysisContext(chapter)
                    │       → "## Chapter N: Title\n\n<full text>"
                    │
                    ├─ LlmProvider.generate(SYSTEM_PROMPT, context)
                    │       Returns JSON: { scenes: [
                    │               { summary, type, location, tensionLevel, pace, tone,
                    │                 continuedFromPrevious, continuesInNext,
                    │                 narrativeTimeType, flashbackToChapter }
                    │       ]}
                    │
                    └─ FOR EACH DetectedScene:
                            ├─ [continuedFromPrevious=true]
                            │       findExistingScene() — match by type + location
                            │       scene.getChapters().add(chapter) + save
                            │
                            └─ [new] INSERT Scene (
                                        project_id, summary, type, location,
                                        tensionLevel, pace, tone,
                                        narrativeTimeType, flashbackToChapter)
                                     embeddingService.embedScene(scene)  [async]
                                         vectorStore.add(Document(uuid,
                                             "Scene type:... | Tone:... | Location:... | Summary:...",
                                             {type:"scene", project_id, entity_id, scene_type}))

            After all steps:
            └─ chapter.setAnalysisStatus(ANALYZED) + save
               [on exception] chapter.setAnalysisStatus(FAILED) + save

HTTP Response (if manual trigger): 202 Accepted
```

**What is persisted**: Characters (upserted), CharacterStates (per character), CharacterRelationships (upserted), RelationshipStates (one per pair per chapter), Scenes. All embedded in pgvector.

---

## Flow 3 — Translate Chapter

**Trigger**: `POST /api/translation/chapters/{id}?provider=openai` (optional provider override)

```
HTTP Request
    │
    ▼
TranslationController.translateChapter()
    ├─ chapterRepository.findById()   → verify exists
    └─ translationService.translateChapterAsync(chapterId, providerOverride)
            │  [returns CompletableFuture — fires async immediately]
            │
            ▼
    TranslationService.translateChapterAsync()
            ├─ chapterRepository.findById()
            ├─ chapter.setStatus(TRANSLATING) + save
            ├─ providerRegistry.resolve(providerOverride)  → LlmProvider
            │
            ├─ [text.length <= 8000 chars]  ── SINGLE CHUNK PATH
            │       translateText(chapter, text, provider)
            │           └─ (see two-pass pipeline below)
            │       chapter.setTranslatedText(result)
            │       chapter.setChunked(false)
            │
            └─ [text.length > 8000 chars]   ── CHUNKED PATH
                    splitIntoSegments(text, 8000, chapter)
                    │   └─ splits at newline boundaries
                    │       INSERT Segment rows (sequenceNumber, originalText, status=PENDING)
                    │
                    FOR EACH Segment:
                    ├─ translateText(chapter, segment.originalText, provider)
                    │   └─ (see two-pass pipeline below)
                    ├─ segment.setTranslatedText(result)
                    ├─ segment.setStatus(AI_TRANSLATED)
                    └─ segmentRepository.save(segment)
                    │
                    └─ chapter.setTranslatedText(join all segment.translatedText)
                       chapter.setTotalSegments / setTranslatedSegments

            chapter.setTranslationStatus(AI_TRANSLATED)
            chapter.setStatus(COMPLETED)
            chapterRepository.save()

HTTP Response: 202 Accepted (fires immediately; translation runs in background)
```

### Two-Pass Translation Pipeline (called per chunk)

```
translateText(chapter, text, provider)
    │
    └─ contextBuilder.buildTranslationContext(chapter, text)
            │
            ├─ sceneRepository.findByChapterId()        → get scenes for this chapter
            │
            ├─ resolveStateChapterNumber(chapter, scenes)
            │       [FLASHBACK detected] → use flashbackToChapter number
            │       [PRESENT]            → use current chapter number
            │       (this controls which CharacterState snapshot is retrieved — KEY for flashbacks)
            │
            ├─ buildRagCharacterBlock(text, projectId, stateChapterNumber)
            │       ├─ embeddingService.findRelevantCharacters(text, projectId, topK=6)
            │       │       └─ vectorStore.similaritySearch(SearchRequest{
            │       │               query: text,
            │       │               topK: 6,
            │       │               filter: type=="character" AND project_id=="X"})
            │       │
            │       ├─ [fallback if vector empty] characterRepository.findByProjectId()
            │       │
            │       └─ FOR EACH character doc:
            │               └─ characterStateRepository
            │                       .findLatestStateAtOrBefore(charId, stateChapterNumber)
            │                   → enriches each character entry with:
            │                       "[At ch.N] State: grief-stricken | Goal: ... | Arc: ..."
            │
            ├─ buildRagGlossaryBlock(text, projectId)
            │       └─ embeddingService.findRelevantGlossaryTerms(text, projectId, topK=10)
            │               vectorStore.similaritySearch(filter: type=="glossary" AND project_id=="X")
            │
            └─ buildRagStyleExamplesBlock(text, projectId, sceneType)
                    └─ embeddingService.findStyleExamples(text, projectId, sceneType, topK=2)
                            vectorStore.similaritySearch(
                                filter: type=="style_example" AND project_id=="X"
                                        AND scene_type=="BATTLE")

    Returns TranslationContext(sourceLanguage, targetLanguage, glossaryBlock,
                               characterBlock, sceneContext, styleGuide,
                               styleExamplesBlock, textToTranslate)

    ════════════════════════════════════════
    PASS 1 — Faithful Draft
    ════════════════════════════════════════

    provider.generate(
        SYSTEM: "You are a professional translator from {src} to {tgt}.
                 Your ONLY goal is ACCURACY and COMPLETENESS.
                 Do NOT beautify or restructure.
                 ## Enforced glossary: {glossaryBlock}
                 ## Characters: {characterBlock}",
        USER:   "Translate faithfully from {src} to {tgt}.
                 ## Text: {originalText}"
    )
    → faithfulDraft (plain text, accurate but unpolished)

    ════════════════════════════════════════
    PASS 2 — Literary Elevation
    ════════════════════════════════════════

    provider.generate(
        SYSTEM: "You are a literary translator and co-author.
                 Goal: EFFECT EQUIVALENCE — reader should FEEL what the original felt.
                 ## Scene context: {sceneContext}
                 ## Scene-type music guide: BATTLE→fragment/compress, ROMANCE→slow rhythm...
                 ## Tone modifiers: TENSE→physicalize dread, MELANCHOLIC→trailing sentences...
                 ## Project prose style: {styleGuide}
                 ## Approved translation examples: {styleExamplesBlock}
                 ## Enforced glossary: {glossaryBlock}
                 ## Characters: {characterBlock}",
        USER:   "## Source text (for author intent):
                 {originalText}
                 ## Faithful draft (your raw material):
                 {faithfulDraft}
                 Rewrite as literary prose in {targetLanguage}.
                 Provide contextNotes on significant creative choices."
    )
    → JSON { translatedText, contextNotes }
    → BeanOutputConverter extracts translatedText

    Returns: final translated string
```

### Translation Review Flow (user action after translation)

```
PUT /api/translation/chapters/{id}/save
    Body: { accepted: true }  OR  { accepted: false, editedText: "..." }
        │
        ▼
TranslationController.saveReview()
    ├─ [accepted=true]
    │       chapter.setUserAccepted(true)
    │       chapter.setUserEditedText(null)   ← no duplicate storage
    │
    └─ [accepted=false]
            chapter.setUserEditedText(editedText)
            chapter.setUserAccepted(false)

    chapter.setReviewedAt(now())
    chapterRepository.save()

Response: TranslatedTextResponse {
    translatedText,      ← always the AI version
    userEditedText,      ← null if accepted, or the user's edit
    userAccepted,        ← null=not reviewed, true=accepted, false=modified
    reviewedAt
}
```

---

## Data Written Per Flow (summary)

| Flow | Tables Written | pgvector Written |
|------|---------------|-----------------|
| Upload | `chapters` (1 row) | Nothing |
| Analyze | `characters` (upsert), `character_states`, `character_relationships` (upsert), `relationship_states`, `scenes` | character, character_state, relationship_state, scene docs |
| Translate | `chapters` (translatedText), `segments` (if chunked) | Nothing |
| Review | `chapters` (userEditedText, userAccepted, reviewedAt) | Nothing |
