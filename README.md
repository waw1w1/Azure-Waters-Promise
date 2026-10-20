<p align="right">
  <strong>English</strong> · <a href="./README.zh-CN.md">简体中文</a>
</p>

<div align="center">
  <img src="./IMG_20260815_002825..jpg" width="180" alt="Azure Waters Promise app icon" />
  <h1>Azure Waters Promise</h1>
  <p><strong>A local-first Android AI writing tool built for long-form fiction</strong></p>
  <p>Let AI offer possibilities. Let the author decide the soul of the story.</p>
  <p>
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/Data-Local%20First-0E7490" alt="Local First" />
  </p>

  <p>
    <a href="#how-to-use">How to Use</a>
    ·
    <a href="#data-and-privacy">Data &amp; Privacy</a>
    ·
    <a href="#local-build">Local Build</a>
  </p>
</div>

---

## What Is It?

**Azure Waters Promise** (绀海之约) is an Android app for writing long-form fiction.

It is a lightweight AI novel-writing tool inspired by SillyTavern-style context management. It was made for one very practical reason: to write a few lines of your own story while slacking off at work.

This is not just another chat wrapper, and AI is never allowed to silently rewrite your manuscript. Novels, chapters, lore, summaries, candidates, and generation history are built around local storage. AI output first becomes a candidate; it enters the actual story only after you review, edit, and accept it.

> The manuscript belongs to the author. AI only brings new possibilities.

## Why Azure Waters Promise?

| What you may care about | How the app handles it |
| --- | --- |
| Can AI overwrite my manuscript? | No. Model output becomes a candidate and is appended only after you accept it. |
| How does a long story keep its continuity? | Through story memories, presets, lorebooks, and layered summaries. |
| How can I compare multiple generations? | Candidates can be switched, edited, regenerated, deleted individually, or cleared as a group. |
| Can model reasoning leak into the novel? | No. Reasoning and final content are displayed and stored separately. |
| Can I see what is actually sent? | Every generation can be inspected before sending, including memory order, messages, and the redacted request. |
| Where is my writing stored? | Project data is stored locally on the Android device first. |
| What if I change phones or make a mistake? | Password-protected full backups, restoration, and pre-restore rollback are supported. |

## Core Features

### 📚 One Work, One Independent Book

- Manage multiple works from a top-level bookshelf.
- Each book has its own chapters, models, lore, summaries, history, and assistant conversation.
- Pick a cover from the system gallery, then drag and pinch to crop it to a fixed 2:3 ratio.

### ✍️ Focused and Reliable Manuscript Editing

- Continuous manuscript text is saved automatically on the device.
- Chapter details stay collapsed by default, leaving more room for the story.
- Create, reorder, recycle, and restore chapters.
- Keep the manuscript visible and editable when the mobile keyboard opens.

### ✨ AI Candidates, Not Automatic Ghostwriting

- Describe the direction of the next passage and inspect exactly what the model will receive.
- Stream body-text or summary candidates.
- Switch between candidates, edit them, regenerate them, or delete them.
- Content enters the manuscript only after you explicitly accept it.
- If generation is stopped or the connection breaks, partial content can still be preserved and used.

### 🧠 Memory That Can Stay With a Long Story

- Choose which chapters and summaries will participate in the next generation.
- Use presets and lorebooks to maintain writing rules, character information, and worldbuilding.
- Summaries track their sources and ask to be refreshed when the source text changes.
- Global inspiration material is never sent unless it is copied into the current work and explicitly enabled.

### 💬 Creative Dialogue

- Each work has one persistent, independent creative conversation.
- Discuss character motivation, narrative conflict, and possible directions.
- The assistant cannot silently edit the manuscript or carry model reasoning into the next turn.

### 🔎 A Visible Creative Trail

- Review story memory, final messages, and the full redacted request before sending.
- Revisit model drafts, author edits, candidate states, and actual usage in the creative history.
- See context usage as a simple progress ring; exact Token values appear only after tapping it.
- Safely undo accepted text when possible while keeping the historical trail intact.

## Writing Flow

> **Bookshelf** → **Edit Manuscript** → **Describe This Direction** → **Review Before Sending** → **Compare Candidates** → **Edit and Accept** → **Keep the Creative Trail**

If a result does not fit, regenerate it, delete it, or return to the prompt and adjust the direction. Unaccepted content never slips into the manuscript.

## How to Use

The current app interface is in Simplified Chinese. Original button labels are included below for clarity.

### 1. Start a Story

1. Open the app and tap “开始一个故事” (Start a Story) on the bookshelf.
2. Name the story; its first chapter will be created automatically.
3. Tap the photo button on the cover to choose and crop an image.
4. You can write locally without configuring any AI model.

### 2. Configure an AI Model

1. Open a work, then enter “设置” (Settings).
2. Choose a model provider.
3. Enter the Base URL and API Key, then validate the connection.
4. Select models for manuscript generation, summaries, and creative dialogue.
5. Adjust temperature, maximum output, streaming, and reasoning strength as needed.

### 3. Generate and Choose Text

1. Describe how you want the story to continue below the manuscript.
2. Tap “检查并生成” (Review and Generate).
3. Confirm story memory, outgoing messages, and model parameters.
4. Browse and edit the candidates until one feels right.
5. Tap “接受并追加” (Accept and Append) to add it to the end of the chapter.

### 4. Maintain Story Memory

Open “记忆” (Memory) to:

- Select the chapters and summaries needed for the next generation.
- Create a new story summary.
- Manage presets and lorebooks for the current work.
- Save reusable inspiration in the global material library.

### 5. Back Up Regularly

Create a password-protected <code>.ainovelbackup</code> full backup from Settings. It can include works, covers, chapters, model configuration, candidates, history, and encrypted connection information.

Keep both the backup file and its password safe. The password is never uploaded and cannot be recovered by the app.

## Supported Model Providers

<details>
<summary><strong>Expand the provider list</strong></summary>

<br />

- DeepSeek
- OpenAI
- Anthropic Claude
- Google Gemini
- Google Cloud Vertex AI
- Kimi / Moonshot
- Alibaba Cloud Model Studio / Qwen
- Zhipu GLM
- MiniMax
- xAI
- OpenRouter
- SiliconFlow
- Generic OpenAI-compatible endpoints
- Generic Anthropic-compatible endpoints

Google Gemini and Google Cloud Vertex AI support both standard API keys and Google Cloud Service Account JSON credentials with automatic OAuth2 token issuance. Provider capabilities and API availability may change. Validate the connection with your own account, model, and network environment. If a provider does not expose a model list, you can enter a model ID manually and verify it with a low-cost real request.

</details>

## Data and Privacy

“Local first” does not mean “fully offline.” Local writing does not require a network connection. When you explicitly generate with an online model, the content confirmed in the pre-send review is sent directly to your chosen model provider.

| Data | How it is handled |
| --- | --- |
| Novels, chapters, summaries, candidates, and history | Stored in the local database and app-private storage |
| API Keys | Protected using Android Keystore-backed encryption |
| Request history | Stored in redacted form without plaintext keys or authorization headers |
| Unaccepted candidates and model reasoning | Never added to the manuscript or later story memory |
| Global material | Never sent automatically; it must be copied into a work and explicitly enabled |
| Full backups | Authenticated encryption using a user-provided password |

Before using an online model, also review that provider’s privacy policy and data-retention rules.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Client | Kotlin · Native Android |
| UI | Jetpack Compose · Material 3 |
| Local data | Room |
| Configuration | DataStore |
| Networking | OkHttp · Streaming responses |
| Key protection | Android Keystore · AES-GCM |
| Backup | Password-protected authenticated single-file backup |
| Minimum OS | Android 8.0 / API 26 |

## Local Build

Requirements:

- JDK 17 or later
- Android SDK 36
- A working Android build toolchain

Windows:

<pre><code>./gradlew.bat :app:assembleDebug</code></pre>

macOS / Linux:

<pre><code>./gradlew :app:assembleDebug</code></pre>

Build output:

<pre><code>app/build/outputs/apk/debug/app-debug.apk</code></pre>

---

<div align="center">
  <p><strong>Azure Waters Promise</strong></p>
</div>
