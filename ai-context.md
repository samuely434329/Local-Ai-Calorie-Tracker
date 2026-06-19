# ImageCalTracker — AI Context

## Current Goal

Hand-drawn-sketch Android calorie tracker (Jetpack Compose) with an in-app
local-AI assistant. The assistant has two tabs:
- **Chat** — short diet/nutrition Q&A.
- **Scan** — take a photo, write a *mandatory* description, get estimated macros,
  optionally push them into the day's food log.

The assistant runs Qwen3 0.6B locally via LiteRT-LM, with an in-app downloader
for first-run model fetch and a heuristic fallback if the model is missing.

## Status

All assistant code paths compile structurally. The repo still ships without a
Gradle wrapper, so the very first build must be done in Android Studio (it
will offer to generate the wrapper on import — accept).

## Completed (2026-06-20)

- **Animated "thinking" indicator.** While the assistant is generating a
  reply, the chat tab now shows a dedicated `ThinkingBubble` whose text
  color smoothly lerps between `InkLight` and `InkDark` on a 1.4 s
  reverse loop (Compose `rememberInfiniteTransition` + `animateFloat`).
  Replaces the previous static "…thinking" placeholder, which looked
  identical to a stuck UI on slower devices.
- **Strip Qwen3 reasoning trace from output.** Qwen3 0.6B emits an
  internal `<think>…</think>` block before its real answer. Added
  `AssistantEngine.stripThinking()`, called inside `runLlm()`, which:
    - Removes all properly closed `<think>…</think>` blocks (regex,
      DOTALL + case-insensitive).
    - If a stray opening `<think` tag has no closing tag, drops everything
      from that tag onward.
    - Returns input unchanged when no `<think` token is present.
  Result: chat replies and the macro JSON parser only see the model's
  final user-visible answer.

## Completed (2026-06-09)

- **Qwen download bug fixed** — `AssistantViewModel.downloadModel()` was
  pointing at `qwen3-0.6b-it-litert.bin` on Hugging Face, which 404s. Repaired:
  - URL now points at the real asset
    `https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm`
    (~614 MB).
  - Saves to `<external-files>/llm/Qwen3-0.6B.litertlm` via a `.part` temp file
    + atomic rename so a half-written file never masquerades as a real model.
  - Real failure reason is surfaced into `statusMessage` AND a `Toast`, instead
    of the stale "Downloading model..." string.
  - Progress updates are throttled to integer-percent boundaries (was firing
    per 16 KB buffer ≈ 38k recompositions for a 614 MB download).
- **Removed dead dependency** `com.google.ai.client.generativeai:generativeai:0.9.0`
  from `app/build.gradle.kts`. It's the cloud Gemini SDK and was never imported.

## Completed (2026-05-25)

- **`AndroidManifest.xml`** — declared optional `android.hardware.camera`
  feature; added `androidx.core.content.FileProvider` with authority
  `${applicationId}.fileprovider`; declared `INTERNET` permission for the
  assistant's model download.
- **`res/xml/file_paths.xml`** — exposes
  `<external-files-path>/assistant_photos/` to the FileProvider.
- **`assistant/AssistantEngine.kt`** — wraps LiteRT-LM `Engine`. Lazy-loads any
  `*.litertlm` (or `*.task`) from `<external-files>/llm/` if present; falls
  back to a deterministic responder when missing or load fails. Exposes
  `chat(prompt)` and `estimateMacros(description)` as `suspend` functions.
  Defines `MacroEstimate` and `MacroSource { Llm, Heuristic }`.
- **`assistant/AssistantViewModel.kt`** — `AndroidViewModel` holding a single
  `AssistantUiState` flow (messages, sending, photoUri, description,
  estimating, estimate, usingFallback, downloading, downloadProgress,
  statusMessage). Owns one `AssistantEngine`, closed in `onCleared()`.
  `newPhotoUri()` builds a FileProvider Uri for the camera. `downloadModel()`
  fetches the LiteRT-LM model into local storage (see fix above).
- **`assistant/AssistantDialog.kt`** — Compose `Dialog` with the sketch
  aesthetic. Header (offline status + download progress, **Download Model**
  button when in fallback, **Close** button), Chat/Scan tab pills, Chat tab
  (LazyColumn of bubbles + input row + auto-scroll on new messages), Scan tab
  (photo preview via Coil, take/retake, mandatory multi-line description,
  **Estimate Macros** disabled until both photo + description are present,
  result card with macro chips and **Add to Today's Log**). Camera launched
  via `ActivityResultContracts.TakePicture`.
- **`ui/MainScreen.kt`** — `Icons.Default.AutoAwesome` `IconButton` in the
  right cluster of `TopBar`, immediately to the LEFT of the existing
  `MoreVert` overflow menu. Tapping it shows `AssistantDialog`, whose
  `onAddToLog` callback forwards estimated macros to `viewModel.addEntry(...)`.

## Key Decisions

- **LiteRT-LM, not MediaPipe.** Earlier iterations used MediaPipe
  `tasks-genai` for Gemma; the project switched to LiteRT-LM
  (`com.google.ai.edge.litertlm:litertlm-android:0.12.0`) so it could run
  Qwen3 0.6B. The engine prompt format uses Qwen's `<|im_start|>...<|im_end|>`
  ChatML markers.
- **Model is downloaded at runtime, not bundled.** ~614 MB is too big to ship
  in an APK. Without it, the assistant uses a heuristic responder so the UI
  still works.
- **Text-only model, photo is decorative + UX gate.** The user's mandatory
  description is the actual source of truth for the macro estimate. This
  matches the user's stated requirement: "MANDATORY DESCRIBE IMAGE USING TEXT,
  ai will determine macros of image with the text given by user."
- **Coil for image rendering.** ~250 KB, standard Compose-friendly choice.
- **Manual JSON parsing.** No Gson/Moshi dependency added — two regexes
  pull `name`/`calories`/`proteinG`/`carbsG`/`fatsG` out of the model's
  reply, robust to surrounding prose / code fences.
- **Self-contained ViewModel.** `AssistantViewModel` is created via the
  default `viewModel()` from inside the dialog, so it lives only as long
  as the dialog is mounted, and the LLM handle is released on dismiss.

## Known Issues / Caveats

- **No real Gradle build was performed** — the repo ships without a Gradle
  wrapper. Final build must be done in Android Studio.
- **`com.google.ai.edge.litertlm:litertlm-android:0.12.0` not yet verified
  on a fresh sync.** It's the artifact in `app/build.gradle.kts`. If Android
  Studio fails to resolve it, double-check the coordinates against the
  official LiteRT-LM Android docs (https://ai.google.dev/edge/litert-lm).
- **No CAMERA permission requested.** The system camera intent
  (`ActivityResultContracts.TakePicture`) does not require it for the
  capture-to-uri flow. If we ever switch to in-app camera (CameraX), we'd
  need the runtime permission.
- **No download resume.** If the 614 MB download is interrupted, the user has
  to retry from zero. Acceptable for now; revisit if it becomes a real pain
  point on real devices.
- **No checksum verification on the downloaded model.** If a future fix is
  needed, Hugging Face exposes `X-Linked-Etag` / `X-Xet-Hash` on the resolve
  response we could compare against.
