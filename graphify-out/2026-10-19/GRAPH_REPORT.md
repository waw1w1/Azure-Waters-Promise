# Graph Report - Azure-Waters-Promise  (2026-10-19)

## Corpus Check
- 62 files · ~132,734 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: .xml 11, .properties 2, (none) 1)

## Summary
- 1357 nodes · 3864 edges · 62 communities (27 shown, 35 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 100 edges (avg confidence: 0.86)
- Token cost: 151,382 input · 37,844 output

## Community Hubs (Navigation)
- Provider Catalog and UI Definitions
- Generation DAO and Candidate Queries
- Novel DAO and Chapter Queries
- Backup DAO and State Clearing
- Backup and Generation Entity Models
- Cover Image Cropping and UI Tests
- Anthropic and DeepSeek Remote Adapters
- Room Database Type Converters
- Backup and Workflow Integration Tests
- EPUB Export and App Settings
- Main App Navigation and Shell
- Destination and Entity Scope Enums
- EPUB Document Exporter
- End-to-End App UI Tests
- Context Assembly and Provider Protocol
- Core Workflow Business Logic Tests
- App ViewModel State Actions
- Application Container and UI State
- Novel Repository Data Layer
- Generation Repository and Execution
- Provider Testing Fixtures and Fakes
- Novel Cover Image Storage
- Vertex AI Service Account Auth
- Prepared Request Inspection Sheet
- Architecture and Product Documentation
- Gemini Adapter and Integration Tests
- Backup Repository File Management
- Novel Database Entities and Operations
- ViewModel Candidate and Context Actions
- Model Configuration DAO
- Dependency Injection App Container
- DeepSeek API Adapter
- Room Database Migration Tests
- Candidate Deletion and History Cleanup
- Candidate State and Transition Tests
- Anthropic Remote API Adapter
- Task Type and Generation Settings
- Generation Progress State Machine
- Candidate Group Status Enums
- Acceptance Undo Result Types
- Provider Config and Backup Tests
- Manuscript Revision Type Enums
- UI Status Tone Enums
- Secure Secret Key Storage Interface
- App Artwork and Visual Motifs
- Launcher Icon Design Theme

## God Nodes (most connected - your core abstractions)
1. `AppViewModel` - 104 edges
2. `NovelRepository` - 79 edges
3. `Text()` - 69 edges
4. `NovelDao` - 61 edges
5. `GenerationDao` - 60 edges
6. `ChapterEntity` - 54 edges
7. `BackupDao` - 50 edges
8. `TaskType` - 50 edges
9. `GenerationSettings` - 49 edges
10. `NovelEntity` - 46 edges

## Surprising Connections (you probably didn't know these)
- `Azure Waters Promise` --semantically_similar_to--> `绀海之约`  [INFERRED] [semantically similar]
  README.md → README.zh-CN.md
- `Local-First Architecture` --semantically_similar_to--> `本地优先架构`  [INFERRED] [semantically similar]
  README.md → README.zh-CN.md
- `Focused Manuscript Editing` --semantically_similar_to--> `专注正文编辑`  [INFERRED] [semantically similar]
  README.md → README.zh-CN.md
- `AI Candidates Workflow` --semantically_similar_to--> `AI 候选工作流`  [INFERRED] [semantically similar]
  README.md → README.zh-CN.md
- `Story Memory and Lorebook System` --semantically_similar_to--> `故事记忆与设定系统`  [INFERRED] [semantically similar]
  README.md → README.zh-CN.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Novel Writing and Candidate Adoption Flow** — readme_manuscript_editing, readme_story_memory_system, readme_ai_candidates_workflow, readme_creative_trail [EXTRACTED 1.00]
- **小说创作与候选采纳流程** — readme_zh_cn_manuscript_editing, readme_zh_cn_story_memory_system, readme_zh_cn_ai_candidates_workflow, readme_zh_cn_creative_trail [EXTRACTED 1.00]
- **Local-First Privacy and Data Protection Foundation** — readme_local_first_architecture, readme_encrypted_backup, readme_creative_trail [INFERRED 0.85]
- **Azure Waters Promise Visual Identity** — img_20260815_002825_app_icon, img_20260815_002825_seele_character, img_20260815_002825_azure_waters_celestial_theme, img_20260815_002825_visual_design_motifs [INFERRED 0.85]
- **App Launcher Visual Branding System** — app_src_main_res_drawable_nodpi_ic_launcher_art_art, app_src_main_res_drawable_nodpi_ic_launcher_art_app_brand_visual, app_src_main_res_drawable_nodpi_ic_launcher_art_celestial_night_theme [INFERRED 0.75]

## Communities (62 total, 35 thin omitted)

### Community 0 - "Provider Catalog and UI Definitions"
Cohesion: 0.05
Nodes (59): ProviderCatalog, ProviderDefinition, CrossfadeNumber(), PrimaryAction(), SecondaryAction(), SectionHeader(), StatusPill(), AppLanguage (+51 more)

### Community 5 - "Backup and Generation Entity Models"
Cohesion: 0.21
Nodes (22): BackupEnvelope, BackupPayload, NovelDatabase, AcceptanceRecordEntity, AppSettingsEntity, AssistantSessionEntity, CandidateEntity, CandidateGroupEntity (+14 more)

### Community 6 - "Cover Image Cropping and UI Tests"
Cohesion: 0.07
Nodes (9): CoverCropTest, MainActivity, BookOnShelf(), CoverCropScreen(), CoverImage(), CropControlButton(), cropCover(), LibraryScreen() (+1 more)

### Community 7 - "Anthropic and DeepSeek Remote Adapters"
Cohesion: 0.09
Nodes (3): orEmpty(), orEmpty(), orEmpty()

### Community 8 - "Room Database Type Converters"
Cohesion: 0.08
Nodes (17): Converters, ReasoningKind, NONE, REASONING_CONTENT, REASONING_SUMMARY, ResponseStatus, FAILED, PARTIAL (+9 more)

### Community 11 - "Backup and Workflow Integration Tests"
Cohesion: 0.08
Nodes (3): BackupAndAssistantTest, DeepSeekLiveTest, MemorySecretStore

### Community 14 - "Destination and Entity Scope Enums"
Cohesion: 0.11
Nodes (18): EntryType, PRESET, WORLDBOOK, AppDestination, ASSISTANT, CONTEXT, HISTORY, SETTINGS (+10 more)

### Community 15 - "EPUB Document Exporter"
Cohesion: 0.18
Nodes (3): CoverImageInfo, EpubExporter, EpubExporterTest

### Community 17 - "Context Assembly and Provider Protocol"
Cohesion: 0.15
Nodes (7): ProviderAdapter, LogicalContextItem, ContextAssembler, CoverageOverlap, OrderedNode, OrderedSource, SummarySourceSelection

### Community 18 - "Core Workflow Business Logic Tests"
Cohesion: 0.21
Nodes (5): CoreWorkflowTest, FakeResponse, RepositoryFixture, GenerationSettings, ProviderException

### Community 20 - "Application Container and UI State"
Cohesion: 0.08
Nodes (4): BackupRestorePreview, LocalNovelApplication, Error, UiMessage

### Community 23 - "Generation Repository and Execution"
Cohesion: 0.14
Nodes (16): PreparedRequest, AssistantDelta, editCandidate(), executeAssistant(), executeNewGroup(), generateAssistantReply(), generateBody(), generateSummary() (+8 more)

### Community 24 - "Provider Testing Fixtures and Fakes"
Cohesion: 0.14
Nodes (13): FakeProviderAdapter, Delta, Finished, NormalizedUsage, ProviderModel, ReasoningStrength, MEDIUM, STRONG (+5 more)

### Community 26 - "Vertex AI Service Account Auth"
Cohesion: 0.20
Nodes (3): CachedToken, ServiceAccountCredentials, VertexAiServiceAccountAuth

### Community 27 - "Prepared Request Inspection Sheet"
Cohesion: 0.11
Nodes (4): PreparedRequestSheet(), roleLabel(), sourceLabel(), taskLabel()

### Community 28 - "Architecture and Product Documentation"
Cohesion: 0.16
Nodes (18): AI Candidates Workflow, Azure Waters Promise, Creative Dialogue Assistant, Creative Trail and Pre-Send Inspection, Password-Protected Encrypted Backup, Focused Manuscript Editing, Multi-Provider Model Integration, Story Memory and Lorebook System (+10 more)

### Community 30 - "Gemini Adapter and Integration Tests"
Cohesion: 0.25
Nodes (3): GeminiAdapter, ProviderMessage, VertexAiAndGeminiAdapterTest

### Community 33 - "Novel Database Entities and Operations"
Cohesion: 0.14
Nodes (8): ContextEntryEntity, EntryScope, GLOBAL, NOVEL, SummaryStatus, INVALID, STALE, VALID

### Community 41 - "Candidate Deletion and History Cleanup"
Cohesion: 0.23
Nodes (3): CandidateDeletionResult, HistoryCleanupPreview, HistoryCleanupResult

### Community 46 - "Task Type and Generation Settings"
Cohesion: 0.25
Nodes (5): TaskType, ASSISTANT, BODY, MODEL_VALIDATION, SUMMARY

### Community 47 - "Generation Progress State Machine"
Cohesion: 0.29
Nodes (7): AssistantCompleted, AssistantStarted, Completed, Delta, Failed, GenerationProgress, Started

### Community 48 - "Candidate Group Status Enums"
Cohesion: 0.33
Nodes (6): CandidateGroupStatus, ACCEPTED, DISCARDED, FAILED, GENERATING, PENDING

### Community 49 - "Acceptance Undo Result Types"
Cohesion: 0.40
Nodes (5): AlreadyUndone, Conflict, NotFound, Success, UndoAcceptResult

### Community 51 - "Manuscript Revision Type Enums"
Cohesion: 0.40
Nodes (5): RevisionType, ACCEPT_UNDO, AUTOSAVE, CANDIDATE_ACCEPT, RESTORE

### Community 53 - "UI Status Tone Enums"
Cohesion: 0.40
Nodes (5): StatusTone, ERROR, NEUTRAL, SAVED, WARNING

### Community 56 - "App Artwork and Visual Motifs"
Cohesion: 0.83
Nodes (4): Azure Waters Promise App Icon Artwork, Azure Waters Celestial Motif, Seele Character Visual Concept, Starry Night and Floral Design Elements

### Community 57 - "Launcher Icon Design Theme"
Cohesion: 1.00
Nodes (3): App Brand Visual Identity, Launcher Artwork Image, Celestial Night Theme

## Knowledge Gaps
- **49 isolated node(s):** `PRESET`, `WORLDBOOK`, `GLOBAL`, `NOVEL`, `VALID` (+44 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 288 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **35 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppViewModel` connect `App ViewModel State Actions` to `Provider Catalog and UI Definitions`, `Novel Database Entities and Operations`, `ViewModel Candidate and Context Actions`, `ViewModel Novel and History Actions`, `Assistant and Workspace UI Screens`, `Backup and Generation Entity Models`, `Cover Image Cropping and UI Tests`, `Context and History UI Screens`, `Candidate State and Transition Tests`, `Backup, Cover, and EPUB Export Handlers`, `EPUB Export and App Settings`, `Destination and Entity Scope Enums`, `Task Type and Generation Settings`, `Context Assembly and Provider Protocol`, `Provider Config and Backup Tests`, `Core Workflow Business Logic Tests`, `Application Container and UI State`, `Gemini Adapter and Integration Tests`?**
  _High betweenness centrality (0.137) - this node is a cross-community bridge._
- **What connects `PRESET`, `WORLDBOOK`, `GLOBAL` to the rest of the system?**
  _49 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Provider Catalog and UI Definitions` be split into smaller, more focused modules?**
  _Cohesion score 0.050087361677344205 - nodes in this community are weakly interconnected._
- **Why does `TaskType` connect `Task Type and Generation Settings` to `Provider Catalog and UI Definitions`, `Generation DAO and Candidate Queries`, `Assistant and Workspace UI Screens`, `Backup and Generation Entity Models`, `Anthropic and DeepSeek Remote Adapters`, `Room Database Type Converters`, `Context and History UI Screens`, `Backup and Workflow Integration Tests`, `EPUB Export and App Settings`, `End-to-End App UI Tests`, `Context Assembly and Provider Protocol`, `Application Container and UI State`, `Generation Repository and Execution`, `Provider Testing Fixtures and Fakes`, `Prepared Request Inspection Sheet`, `Gemini Adapter and Integration Tests`, `Vertex AI Test Suite`, `ViewModel Novel and History Actions`, `DeepSeek API Adapter`, `Candidate Deletion and History Cleanup`, `Candidate State and Transition Tests`?**
  _High betweenness centrality (0.100) - this node is a cross-community bridge._
- **Should `Generation DAO and Candidate Queries` be split into smaller, more focused modules?**
  _Cohesion score 0.06779661016949153 - nodes in this community are weakly interconnected._
- **Why does `NovelEntity` connect `Backup and Generation Entity Models` to `Novel Database Entities and Operations`, `Novel DAO and Chapter Queries`, `Backup DAO and State Clearing`, `Cover Image Cropping and UI Tests`, `Novel Library UI Screen`, `Repository Flow State Observers`, `EPUB Export and App Settings`, `Backup and Workflow Integration Tests`, `EPUB Document Exporter`, `App ViewModel State Actions`, `Application Container and UI State`?**
  _High betweenness centrality (0.086) - this node is a cross-community bridge._
- **Should `Novel DAO and Chapter Queries` be split into smaller, more focused modules?**
  _Cohesion score 0.07692307692307693 - nodes in this community are weakly interconnected._