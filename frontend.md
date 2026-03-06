# SSP — Frontend Specification for Angular

## What Is SSP?

**SSP (Story Simulation Platform)** is a context-aware novel localization platform. It is NOT a simple translation tool. It understands the narrative structure of a story — characters, relationships, scene types, emotional tone, and temporal positioning (present / flashback / flash-forward) — and uses that context to produce literary-quality translations that preserve each character's unique voice.

**Primary use case**: A translator or author uploads chapters of a novel (Japanese → English, Korean → English, etc.). The system automatically extracts characters, analyzes scenes, and produces a context-aware translation that reads like real literature, not machine output.

---

## Visual Theme & Design Language

- **Aesthetic**: Dark literary / editorial — think a premium digital book editor. Deep navy/charcoal backgrounds, off-white text, warm amber/gold accents for active states.
- **Tone**: Professional but atmospheric. This is a tool for storytellers, so it should feel like a writing studio.
- **Typography**: Monospace or serif fonts for chapter/translation text panels. Sans-serif for UI chrome.
- **Layout style**: Two-panel or three-panel workspace (original | analysis | translation). Sidebar navigation per project.
- **Status indicators**: Subtle animated dots or progress bars for async AI operations (translation, analysis run in the background).

---

## Application Structure (Pages / Routes)

```
/                          → Dashboard (project list + quick stats)
/projects/new              → Create project form
/projects/:id              → Project workspace (hub for chapters, characters, scenes)
/projects/:id/chapters     → Chapter list for the project
/projects/:id/chapters/new → Add chapter (upload file or paste text)
/projects/:id/chapters/:cid → Chapter detail (original text | translation | analysis)
/projects/:id/characters   → Character registry (cards + relationship graph)
/projects/:id/scenes       → Scene timeline / scene cards
```

---

## Features

### 1. Project Management
- Create, view, edit, delete projects
- Each project has: title, source language, target language, description, translation style (free-text prose style guide — e.g. "dark fantasy, Dostoevsky-like inner monologue")
- Project status badge: `DRAFT` | `PARSING` | `IN_PROGRESS` | `REVIEW` | `COMPLETED` | `ARCHIVED`
- Project card shows: chapter count, character count, last updated

### 2. Chapter Ingestion
- Upload a file (PDF, EPUB, DOCX, TXT) — multipart form upload
- Or paste text directly into a textarea
- Or submit as structured JSON
- After submission the chapter enters `PENDING` → `PARSED` status
- Async AI analysis triggers automatically in the background

### 3. Chapter Workspace
- **Original text panel**: displays the raw source text
- **Translation panel**: shows translated text when available; triggers translation on demand
- **Status bar**: shows chapter status (`PENDING`, `PARSED`, `TRANSLATING`, `COMPLETED`) and translation status
- Translation progress for chunked chapters: e.g. "12 / 30 segments translated"
- "Trigger Translation" button with optional AI provider selector (OpenAI / Anthropic)
- "Trigger Analysis" button (if auto-analysis is disabled)

### 4. Analysis Results
- After analysis completes, show inline on the chapter view:
  - Characters found (count + names)
  - Scenes detected (count + preview cards)
  - Each scene card: type badge, summary, tension level bar (0–1), pace, tone, narrative time (PRESENT / FLASHBACK / FLASH_FORWARD)

### 5. Character Registry
- List all characters discovered across chapters in this project
- Character card: name, translated name, role badge (`PROTAGONIST` / `ANTAGONIST` / `SUPPORTING` / `MINOR`), personality traits, first appearance chapter, voice example snippet
- Relationship graph (optional): visualize character connections (ally, enemy, family, romantic, mentor, rival) using a force-directed graph or similar

### 6. Scene Timeline
- List or timeline view of all scenes across the project
- Filter by: scene type, emotional tone, narrative pace, narrative time type
- Scene card: location, summary, tension level, scene type, time type badge

### 7. Translation Viewer
- Side-by-side: original text (left) | translated text (right)
- For chunked translations: segment-level progress indicator
- Copy-to-clipboard for translated text
- Download translated text as .txt

---

## API Reference

**Base URL**: `http://localhost:8080`
**Swagger UI**: `http://localhost:8080/swagger-ui.html`
**Content-Type**: `application/json` unless specified otherwise

---

### Projects

#### Create Project
```
POST /api/projects
Content-Type: application/json

Request body:
{
  "title": "string",
  "sourceLanguage": "ja",         // BCP-47 code
  "targetLanguage": "en",
  "description": "string",
  "translationStyle": "string"    // e.g. "dark fantasy, lyrical Dostoevsky-like monologue"
}

Response 201:
{
  "id": 1,
  "title": "string",
  "sourceLanguage": "ja",
  "targetLanguage": "en",
  "status": "DRAFT",              // DRAFT | PARSING | IN_PROGRESS | REVIEW | COMPLETED | ARCHIVED
  "description": "string",
  "originalFileName": null,
  "fileFormat": null,             // PDF | EPUB | DOCX | TXT
  "translationStyle": "string",
  "chapterCount": 0,
  "characterCount": 0,
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-01-01T00:00:00"
}
```

#### List All Projects
```
GET /api/projects

Response 200: ProjectResponse[]
```

#### Get Project by ID
```
GET /api/projects/{id}

Response 200: ProjectResponse
```

#### Update Project
```
PUT /api/projects/{id}
Content-Type: application/json
Body: same as Create

Response 200: ProjectResponse
```

#### Delete Project
```
DELETE /api/projects/{id}

Response 204: (no body)
```

---

### Chapters

#### Upload Chapter File
```
POST /api/chapters/upload
Content-Type: multipart/form-data

Form fields:
  file           (required) — PDF, EPUB, DOCX, or TXT file
  projectId      (required) — Long
  chapterNumber  (required) — Integer
  title          (optional) — String

Response 200:
{
  "chapterId": 1,
  "chapterNumber": 1,
  "title": "Chapter 1",
  "status": "PARSED",             // PENDING | PARSING | PARSED | TRANSLATING | COMPLETED
  "translationStatus": null,
  "originalTextPreview": "first 200 chars..."
}
```

#### Submit Chapter as JSON
```
POST /api/chapters/process
Content-Type: application/json

{
  "projectId": 1,
  "chapterNumber": 1,
  "title": "Chapter 1",
  "chapterText": "full chapter text..."
}

Response 200: ChapterProcessingResponse (same as above)
```

#### Submit Chapter as Plain Text
```
POST /api/chapters/process-text
Content-Type: text/plain
Query params: projectId, chapterNumber, title (optional)
Body: raw chapter text

Response 200: ChapterProcessingResponse
```

#### Get Chapter Status
```
GET /api/chapters/{chapterId}

Response 200: ChapterProcessingResponse
```

---

### Translation

#### Trigger Translation
```
POST /api/translation/chapters/{chapterId}
Query param: provider (optional) — "openai" | "anthropic"

Response 202: (no body — async, poll status)
```

#### Get Translation Status
```
GET /api/translation/chapters/{chapterId}

Response 200:
{
  "chapterId": 1,
  "status": "TRANSLATING",        // PENDING | TRANSLATING | COMPLETED | PARTIAL
  "provider": "openai"
}
```

#### Get Translated Text
```
GET /api/translation/chapters/{chapterId}/text

Response 200:
{
  "chapterId": 1,
  "chapterNumber": 1,
  "title": "Chapter 1",
  "translationStatus": "COMPLETED",  // enum TranslationStatus
  "translatedText": "full translated text...",
  "chunked": false,                  // true if chapter was split into segments
  "totalSegments": 0,                // null/0 if not chunked
  "translatedSegments": 0
}

Response 404: if translation not yet available
```

---

### Analysis

#### Trigger Analysis
```
POST /api/analysis/chapters/{chapterId}

Response 202: (no body — async, poll status)
```

#### Get Analysis Status
```
GET /api/analysis/chapters/{chapterId}

Response 200:
{
  "chapterId": 1,
  "status": "ANALYZED",           // PENDING | ANALYZING | ANALYZED
  "charactersFound": 4,
  "scenesDetected": 3,
  "characterNames": ["Shirou", "Rin", "Saber"],
  "scenes": [
    {
      "type": "BATTLE",           // DIALOGUE | ACTION | BATTLE | INTROSPECTION | ROMANCE | EXPOSITION | TRANSITION
      "summary": "Shirou confronts the shadow...",
      "tensionLevel": 0.87
    }
  ]
}
```

---

### Narrative Data (scoped to project)

#### Get All Chapters
```
GET /api/projects/{projectId}/chapters

Response 200:
[
  {
    "chapterId": 1,
    "chapterNumber": 1,
    "title": "Chapter 1",
    "status": "COMPLETED",          // PENDING | PARSING | PARSED | TRANSLATING | COMPLETED
    "translationStatus": "COMPLETED", // null if not yet translated
    "originalTextPreview": "first 200 chars of source text..."
  }
]
```
Ordered by chapterNumber ascending. Use `chapterId` for all subsequent chapter API calls.

---

#### Get All Characters
```
GET /api/projects/{projectId}/characters

Response 200:
[
  {
    "id": 1,
    "projectId": 1,
    "name": "衛宮士郎",
    "translatedName": "Emiya Shirou",
    "description": "string",
    "personalityTraits": "selfless, stubborn, idealistic",
    "role": "PROTAGONIST",        // PROTAGONIST | ANTAGONIST | SUPPORTING | MINOR
    "voiceExample": "I am the bone of my sword...",
    "firstAppearanceChapter": 1,
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

#### Get Character Relationships
```
GET /api/projects/{projectId}/relationships

Response 200:
[
  {
    "id": 1,
    "character1Id": 1,
    "character1Name": "Shirou",
    "character2Id": 2,
    "character2Name": "Rin",
    "type": "ALLY",               // ALLY | ENEMY | FAMILY | ROMANTIC | NEUTRAL | MENTOR | RIVAL
    "description": "reluctant partners",
    "affinity": 0.65,             // 0.0 (hostile) – 1.0 (close)
    "establishedAtChapter": 2,
    "createdAt": "..."
  }
]
```

#### Get All Scenes
```
GET /api/projects/{projectId}/scenes

Response 200:
[
  {
    "id": 1,
    "projectId": 1,
    "summary": "string",
    "type": "BATTLE",             // DIALOGUE | ACTION | BATTLE | INTROSPECTION | ROMANCE | EXPOSITION | TRANSITION
    "location": "Fuyuki City",
    "tensionLevel": 0.9,          // 0.0 – 1.0
    "pace": "FRANTIC",            // SLOW | MODERATE | FAST | FRANTIC
    "tone": "TENSE",              // SERIOUS | HUMOROUS | MELANCHOLIC | TRIUMPHANT | MYSTERIOUS | TENSE
    "narrativeTimeType": "PRESENT", // PRESENT | FLASHBACK | FLASH_FORWARD
    "flashbackToChapter": null,   // chapter number if FLASHBACK
    "chapterIds": [3, 4],
    "createdAt": "..."
  }
]
```

---

## Enum Reference

| Enum | Values |
|------|--------|
| `ProjectStatus` | `DRAFT`, `PARSING`, `IN_PROGRESS`, `REVIEW`, `COMPLETED`, `ARCHIVED` |
| `ChapterStatus` | `PENDING`, `PARSING`, `PARSED`, `TRANSLATING`, `COMPLETED` |
| `TranslationStatus` | see `TranslationStatus` enum |
| `FileFormat` | `PDF`, `EPUB`, `DOCX`, `TXT` |
| `CharacterRole` | `PROTAGONIST`, `ANTAGONIST`, `SUPPORTING`, `MINOR` |
| `RelationshipType` | `ALLY`, `ENEMY`, `FAMILY`, `ROMANTIC`, `NEUTRAL`, `MENTOR`, `RIVAL` |
| `SceneType` | `DIALOGUE`, `ACTION`, `BATTLE`, `INTROSPECTION`, `ROMANCE`, `EXPOSITION`, `TRANSITION` |
| `EmotionalTone` | `SERIOUS`, `HUMOROUS`, `MELANCHOLIC`, `TRIUMPHANT`, `MYSTERIOUS`, `TENSE` |
| `NarrativePace` | `SLOW`, `MODERATE`, `FAST`, `FRANTIC` |
| `NarrativeTimeType` | `PRESENT`, `FLASHBACK`, `FLASH_FORWARD` |

---

## Async Polling Pattern

Several operations are async (translation, analysis). The Angular client should poll status endpoints after triggering:

1. `POST /api/translation/chapters/{id}` → `202 Accepted`
2. Poll `GET /api/translation/chapters/{id}` every 3–5 seconds
3. Stop polling when `status === "COMPLETED"` or `status === "PARTIAL"`
4. Fetch `GET /api/translation/chapters/{id}/text` to display result

Same pattern for analysis:
1. `POST /api/analysis/chapters/{id}` → `202 Accepted`
2. Poll `GET /api/analysis/chapters/{id}` every 3–5 seconds
3. Stop when `status === "ANALYZED"`

---

## Development Phases for UI

**Phase 1 (MVP)**
- Project CRUD (list, create, edit, delete)
- Chapter ingestion (file upload + text paste)
- Translation trigger + status polling + result display
- Analysis trigger + status + character/scene summary

**Phase 2**
- Character registry page with relationship visualization
- Scene timeline view with filters
- Side-by-side original/translation reader
- Per-chapter analysis detail panel

**Phase 3**
- Glossary management UI
- Style example editor (few-shot examples per scene type)
- Export/download translated chapters
- Dark/light theme toggle
