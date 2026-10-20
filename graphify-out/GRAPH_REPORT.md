# Graph Report - Azure-Waters-Promise  (2026-10-19)

## Corpus Check
- 66 files · ~135,212 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: .xml 11, (none) 2, .properties 2)

## Summary
- 1420 nodes · 3964 edges · 70 communities (37 shown, 33 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 86 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `12fb2671`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SettingsScreen.kt
- Generation DAO and Candidate Queries
- Novel DAO and Chapter Queries
- Backup DAO and State Clearing
- Backup and Generation Entity Models
- MainActivity.kt
- Anthropic and DeepSeek Remote Adapters
- Room Database Type Converters
- ContextScreen.kt
- Novel Library UI Screen
- Text
- Main App Navigation and Shell
- Destination and Entity Scope Enums
- EPUB Document Exporter
- End-to-End App UI Tests
- Context Assembly and Provider Protocol
- CoreWorkflowTest.kt
- App ViewModel State Actions
- Application Container and UI State
- Novel Repository Data Layer
- README.zh-CN.md
- Generation Repository and Execution
- Provider Testing Fixtures and Fakes
- Novel Cover Image Storage
- Vertex AI Service Account Auth
- RequestSnapshotEntity
- README.md
- Gemini Adapter and Integration Tests
- AcceptanceRecordEntity
- Backup Repository File Management
- Novel Database Entities and Operations
- ViewModel Candidate and Context Actions
- Model Configuration DAO
- Dependency Injection App Container
- CandidateEntity
- Room Database Migration Tests
- Candidate Deletion and History Cleanup
- Candidate State and Transition Tests
- SummaryMemoryWorkflowTest.kt
- TaskType
- Generation Progress State Machine
- Candidate Group Status Enums
- Acceptance Undo Result Types
- AppDestination
- UI Status Tone Enums
- App Artwork and Visual Motifs
- Launcher Icon Design Theme
- WorkspaceScreen
- Agent skills
- AppContainer.kt
- Issue tracker: Local Markdown
- AppLanguage
- Domain Docs
- triage-labels.md

## God Nodes (most connected - your core abstractions)
1. `AppViewModel` - 106 edges
2. `NovelRepository` - 82 edges
3. `Text()` - 70 edges
4. `NovelDao` - 64 edges
5. `GenerationDao` - 61 edges
6. `ChapterEntity` - 58 edges
7. `BackupDao` - 50 edges
8. `TaskType` - 50 edges
9. `GenerationSettings` - 50 edges
10. `NovelEntity` - 46 edges

## Surprising Connections (you probably didn't know these)
- `roll()` --calls--> `PreparedRequest`  [EXTRACTED]
  app/src/main/java/com/ainovel/local/domain/GenerationRepository.kt → app/src/main/java/com/ainovel/local/data/remote/ProviderModels.kt
- `MemorySecretStore` --implements--> `SecretStore`  [EXTRACTED]
  app/src/androidTest/java/com/ainovel/local/TestFixtures.kt → app/src/main/java/com/ainovel/local/data/security/SecureSecretStore.kt
- `FakeResponse` --references--> `NormalizedUsage`  [EXTRACTED]
  app/src/androidTest/java/com/ainovel/local/TestFixtures.kt → app/src/main/java/com/ainovel/local/data/remote/ProviderModels.kt
- `FakeProviderAdapter` --implements--> `ProviderAdapter`  [EXTRACTED]
  app/src/androidTest/java/com/ainovel/local/TestFixtures.kt → app/src/main/java/com/ainovel/local/data/remote/ProviderAdapter.kt
- `RepositoryFixture` --calls--> `ContextAssembler`  [EXTRACTED]
  app/src/androidTest/java/com/ainovel/local/TestFixtures.kt → app/src/main/java/com/ainovel/local/domain/ContextAssembler.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **App Launcher Visual Branding System** — app_src_main_res_drawable_nodpi_ic_launcher_art_art, app_src_main_res_drawable_nodpi_ic_launcher_art_app_brand_visual, app_src_main_res_drawable_nodpi_ic_launcher_art_celestial_night_theme [INFERRED 0.75]
- **Azure Waters Promise Visual Identity** — img_20260815_002825_app_icon, img_20260815_002825_seele_character, img_20260815_002825_azure_waters_celestial_theme, img_20260815_002825_visual_design_motifs [INFERRED 0.85]

## Communities (70 total, 33 thin omitted)

### Community 0 - "SettingsScreen.kt"
Cohesion: 0.08
Nodes (23): ProviderCatalog, ProviderDefinition, CrossfadeNumber(), PrimaryAction(), SecondaryAction(), SectionHeader(), StatusPill(), AdaptiveActionPair() (+15 more)

### Community 5 - "Backup and Generation Entity Models"
Cohesion: 0.12
Nodes (16): NovelDatabase, AppSettingsEntity, AssistantSessionEntity, ContextNodeType, CHAPTER, SUMMARY, DefaultInstructions, Ids (+8 more)

### Community 7 - "Anthropic and DeepSeek Remote Adapters"
Cohesion: 0.07
Nodes (5): AnthropicCompatibleAdapter, orEmpty(), DeepSeekAdapter, orEmpty(), orEmpty()

### Community 8 - "Room Database Type Converters"
Cohesion: 0.14
Nodes (5): Converters, ReasoningKind, NONE, REASONING_CONTENT, REASONING_SUMMARY

### Community 9 - "ContextScreen.kt"
Cohesion: 0.09
Nodes (16): ChapterTitleEditDialog(), ContextEditorState, ContextEntryDialog(), ContextScreen(), EmptyPanel(), EntryAction(), EntryCard(), EntryList() (+8 more)

### Community 10 - "Novel Library UI Screen"
Cohesion: 0.05
Nodes (4): BookOnShelf(), CoverImage(), CropControlButton(), LibraryScreen()

### Community 12 - "Text"
Cohesion: 0.13
Nodes (7): Text(), translateDynamicText(), AssistantScreen(), LiveAssistantEntry(), SaveAssistantIdeaDialog(), TranscriptEntry(), UseAsRequirementDialog()

### Community 13 - "Main App Navigation and Shell"
Cohesion: 0.05
Nodes (8): AppLanguageStore, LanguageSelectionScreen(), LocalNovelApp(), LocalNovelContent(), PreparedRequestSheet(), roleLabel(), sourceLabel(), taskLabel()

### Community 14 - "Destination and Entity Scope Enums"
Cohesion: 0.28
Nodes (4): buildAssistantIdeaEntry(), buildAssistantIdeaInfoMessage(), localized(), AssistantIdeaWorkflowTest

### Community 15 - "EPUB Document Exporter"
Cohesion: 0.15
Nodes (5): ChapterEntity, NovelEntity, CoverImageInfo, EpubExporter, EpubExporterTest

### Community 17 - "Context Assembly and Provider Protocol"
Cohesion: 0.17
Nodes (6): ProviderAdapter, ContextAssembler, CoverageOverlap, OrderedNode, OrderedSource, SummarySourceSelection

### Community 18 - "CoreWorkflowTest.kt"
Cohesion: 0.07
Nodes (9): BackupAndAssistantTest, CoreWorkflowTest, CoverCropTest, FakeResponse, MemorySecretStore, RepositoryFixture, ProviderException, CoverCropScreen() (+1 more)

### Community 20 - "Application Container and UI State"
Cohesion: 0.08
Nodes (4): BackupRestorePreview, LocalNovelApplication, Error, UiMessage

### Community 22 - "README.zh-CN.md"
Cohesion: 0.10
Nodes (20): 1. 开始一个故事, 2. 配置 AI 模型, 3. 生成并选择正文, 4. 维护故事记忆, 5. 定期备份, ✨ AI 候选，而不是自动代写, 📚 一部作品，一本独立的书, ✍️ 专注而可靠的正文编辑 (+12 more)

### Community 23 - "Generation Repository and Execution"
Cohesion: 0.14
Nodes (11): ProviderModel, editCandidate(), executeNewGroup(), generateSummary(), manualModelCapabilities(), ProviderValidationResult, refreshModels(), roll() (+3 more)

### Community 24 - "Provider Testing Fixtures and Fakes"
Cohesion: 0.18
Nodes (11): FakeProviderAdapter, Delta, Finished, NormalizedUsage, ReasoningStrength, MEDIUM, STRONG, UNSUPPORTED (+3 more)

### Community 26 - "Vertex AI Service Account Auth"
Cohesion: 0.19
Nodes (3): CachedToken, ServiceAccountCredentials, VertexAiServiceAccountAuth

### Community 27 - "RequestSnapshotEntity"
Cohesion: 0.15
Nodes (14): RequestSnapshotEntity, ResponseStatus, FAILED, PARTIAL, PREPARED, STREAMING, SUCCEEDED, formatTime() (+6 more)

### Community 28 - "README.md"
Cohesion: 0.10
Nodes (20): 1. Start a Story, 2. Configure an AI Model, 3. Generate and Choose Text, 4. Maintain Story Memory, 5. Back Up Regularly, 🔎 A Visible Creative Trail, ✨ AI Candidates, Not Automatic Ghostwriting, Core Features (+12 more)

### Community 31 - "AcceptanceRecordEntity"
Cohesion: 0.22
Nodes (4): BackupEnvelope, BackupPayload, AcceptanceRecordEntity, ChapterRevisionEntity

### Community 33 - "Novel Database Entities and Operations"
Cohesion: 0.13
Nodes (11): ContextEntryEntity, EntryScope, GLOBAL, NOVEL, EntryType, PRESET, WORLDBOOK, SummaryStatus (+3 more)

### Community 38 - "CandidateEntity"
Cohesion: 0.19
Nodes (3): CandidateEntity, CandidateGroupEntity, WorkspaceUiState

### Community 41 - "Candidate Deletion and History Cleanup"
Cohesion: 0.23
Nodes (3): CandidateDeletionResult, HistoryCleanupPreview, HistoryCleanupResult

### Community 45 - "SummaryMemoryWorkflowTest.kt"
Cohesion: 0.28
Nodes (3): preparePermanentChapterDeletion(), prepareSummaryUpdate(), SummaryMemoryWorkflowTest

### Community 46 - "TaskType"
Cohesion: 0.17
Nodes (15): DeepSeekLiveTest, TaskType, ASSISTANT, BODY, MODEL_VALIDATION, SUMMARY, GenerationSettings, LogicalContextItem (+7 more)

### Community 47 - "Generation Progress State Machine"
Cohesion: 0.25
Nodes (8): AssistantCompleted, AssistantDelta, AssistantStarted, Completed, Delta, Failed, GenerationProgress, Started

### Community 48 - "Candidate Group Status Enums"
Cohesion: 0.16
Nodes (11): CandidateGroupStatus, ACCEPTED, DISCARDED, FAILED, GENERATING, PENDING, RevisionType, ACCEPT_UNDO (+3 more)

### Community 49 - "Acceptance Undo Result Types"
Cohesion: 0.40
Nodes (5): AlreadyUndone, Conflict, NotFound, Success, UndoAcceptResult

### Community 51 - "AppDestination"
Cohesion: 0.18
Nodes (9): AppDestination, ASSISTANT, CONTEXT, HISTORY, SETTINGS, WORKSPACE, AppSurface, DestinationItem (+1 more)

### Community 53 - "UI Status Tone Enums"
Cohesion: 0.40
Nodes (5): StatusTone, ERROR, NEUTRAL, SAVED, WARNING

### Community 56 - "App Artwork and Visual Motifs"
Cohesion: 0.83
Nodes (4): Azure Waters Promise App Icon Artwork, Azure Waters Celestial Motif, Seele Character Visual Concept, Starry Night and Floral Design Elements

### Community 57 - "Launcher Icon Design Theme"
Cohesion: 1.00
Nodes (3): App Brand Visual Identity, Launcher Artwork Image, Celestial Night Theme

### Community 62 - "WorkspaceScreen"
Cohesion: 0.50
Nodes (5): CandidatePanel(), ChapterRail(), ContextUsageRing(), ManuscriptPane(), WorkspaceScreen()

### Community 63 - "Agent skills"
Cohesion: 0.29
Nodes (6): Agent skills, Azure Waters Promise (绀海之约), Domain docs, graphify, Issue tracker, Triage labels

### Community 66 - "Issue tracker: Local Markdown"
Cohesion: 0.33
Nodes (5): Conventions, Issue tracker: Local Markdown, Wayfinding operations, When a skill says "fetch the relevant ticket", When a skill says "publish to the issue tracker"

### Community 67 - "AppLanguage"
Cohesion: 0.40
Nodes (4): AppLanguage, ENGLISH, SIMPLIFIED_CHINESE, AppLanguageRuntime

### Community 68 - "Domain Docs"
Cohesion: 0.50
Nodes (3): Before exploring, read these, Domain Docs, File structure

## Knowledge Gaps
- **49 isolated node(s):** `PRESET`, `WORLDBOOK`, `GLOBAL`, `NOVEL`, `VALID` (+44 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 340 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **33 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppViewModel` connect `App ViewModel State Actions` to `SettingsScreen.kt`, `Generation DAO and Candidate Queries`, `WorkspaceScreen.kt`, `Backup and Generation Entity Models`, `MainActivity.kt`, `ContextScreen.kt`, `Novel Library UI Screen`, `Text`, `Main App Navigation and Shell`, `EPUB Document Exporter`, `Application Container and UI State`, `RequestSnapshotEntity`, `AcceptanceRecordEntity`, `Novel Database Entities and Operations`, `ViewModel Candidate and Context Actions`, `ViewModel Novel and History Actions`, `CandidateEntity`, `Candidate State and Transition Tests`, `Backup, Cover, and EPUB Export Handlers`, `TaskType`, `AppDestination`, `.flushAutosaveNow`, `WorkspaceScreen`?**
  _High betweenness centrality (0.155) - this node is a cross-community bridge._
- **What connects `PRESET`, `WORLDBOOK`, `GLOBAL` to the rest of the system?**
  _49 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SettingsScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.0841799709724238 - nodes in this community are weakly interconnected._
- **Why does `TaskType` connect `TaskType` to `SettingsScreen.kt`, `Generation DAO and Candidate Queries`, `WorkspaceScreen.kt`, `Backup and Generation Entity Models`, `Anthropic and DeepSeek Remote Adapters`, `Room Database Type Converters`, `VertexAiAndGeminiAdapterTest.kt`, `Main App Navigation and Shell`, `End-to-End App UI Tests`, `Context Assembly and Provider Protocol`, `CoreWorkflowTest.kt`, `App ViewModel State Actions`, `Application Container and UI State`, `Novel Repository Data Layer`, `Generation Repository and Execution`, `Provider Testing Fixtures and Fakes`, `RequestSnapshotEntity`, `Novel Database Entities and Operations`, `ViewModel Candidate and Context Actions`, `ViewModel Novel and History Actions`, `CandidateEntity`, `Candidate Deletion and History Cleanup`, `Candidate State and Transition Tests`?**
  _High betweenness centrality (0.092) - this node is a cross-community bridge._
- **Should `Generation DAO and Candidate Queries` be split into smaller, more focused modules?**
  _Cohesion score 0.11411411411411411 - nodes in this community are weakly interconnected._
- **Why does `NovelDatabase` connect `Backup and Generation Entity Models` to `AppContainer.kt`, `Backup Repository File Management`, `Novel Database Entities and Operations`, `Generation DAO and Candidate Queries`, `Model Configuration DAO`, `CandidateEntity`, `Room Database Migration Tests`, `Room Database Type Converters`, `EPUB Document Exporter`, `CoreWorkflowTest.kt`, `Novel Repository Data Layer`, `Generation Repository and Execution`, `Provider Testing Fixtures and Fakes`, `RequestSnapshotEntity`, `Encrypted Backup Crypto and Storage`, `AcceptanceRecordEntity`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **Should `Novel DAO and Chapter Queries` be split into smaller, more focused modules?**
  _Cohesion score 0.09082125603864734 - nodes in this community are weakly interconnected._