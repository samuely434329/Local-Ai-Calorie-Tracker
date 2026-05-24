# ImageCalTracker — AI Context

## Current Goal

Hand-drawn-sketch Android calorie tracker (Jetpack Compose) with an in-app
local-AI assistant. The assistant has two tabs:
- **Chat** — short diet/nutrition Q&A.
- **Scan** — take a photo, write a *mandatory* description, get estimated macros,
  optionally push them into the day's food log.

## Status

All code changes for the assistant feature are in place. The project still
needs to be opened in Android Studio for the first Gradle sync (the repo
deliberately ships without `gradlew` per the README).

## Completed (2026-05-25)

- **`AndroidManifest.xml`** — declared optional `android.hardware.camera`
  feature; added `androidx.core.content.FileProvider` with authority
  `${applicationId}.fileprovider`.
- **`res/xml/file_paths.xml`** — exposes
  `<external-files-path>/assistant_photos/` to the FileProvider.
- **`app/build.gradle.kts`** — added two dependencies:
  - `com.google.mediapipe:tasks-genai:0.10.14` (on-device Gemma).
  - `io.coil-kt:coil-compose:2.6.0` (async image preview).
- **`assistant/AssistantEngine.kt`** — wraps `LlmInference`. Lazy-loads the
  model from `<external-files>/llm/gemma.task` if present; falls back to a
  deterministic keyword-driven responder when missing or load fails. Exposes
  `chat(prompt)` and `estimateMacros(description)` as `suspend` functions.
  Also defines `MacroEstimate` and `MacroSource { Llm, Heuristic }`.
- **`assistant/AssistantViewModel.kt`** — `AndroidViewModel` holding a single
  `AssistantUiState` flow (messages, sending, photoUri, description,
  estimating, estimate, usingFallback). Owns one `AssistantEngine`, closed
  in `onCleared()`. `newPhotoUri()` builds a FileProvider Uri for the camera.
- **`assistant/AssistantDialog.kt`** — Compose `Dialog` with the sketch
  aesthetic. Header (offline status: `local model` vs `heuristic mode`),
  Chat/Scan tab pills, Chat tab (LazyColumn of bubbles + input row +
  auto-scroll on new messages), Scan tab (photo preview via Coil, take /
  retake, mandatory multi-line description, **Estimate Macros** disabled
  until both photo + description are present, result card with macro chips
  and **Add to Today's Log**). Camera launched via
  `ActivityResultContracts.TakePicture`.
- **`ui/MainScreen.kt`** — added an `Icons.Default.AutoAwesome` `IconButton`
  in the right cluster of `TopBar`, **immediately to the LEFT** of the
  existing `MoreVert` overflow menu. Tapping it shows `AssistantDialog`,
  whose `onAddToLog` callback forwards estimated macros to
  `viewModel.addEntry(...)`.

## Key Decisions

- **MediaPipe LlmInference, not a custom integration.** It's the official
  Google on-device LLM API and is the path Gemma is shipped through.
- **Model is NOT bundled.** `.task` files are 500 MB+. The user (or app)
  drops `gemma.task` at `/Android/data/com.imagecaltracker/files/llm/`.
  Without it, the assistant uses a heuristic responder so the UI still works.
- **Text-only model, photo is decorative + UX gate.** A multimodal Gemma
  build was not chosen; the user's mandatory description is the actual
  source of truth for the macro estimate. This matches the user's stated
  requirement: "MANDATORY DESCRIBE IMAGE USING TEXT, ai will determine
  macros of image with the text given by user."
- **Coil for image rendering.** ~250 KB, standard Compose-friendly choice.
- **Manual JSON parsing.** No Gson/Moshi dependency added — two regexes
  pull `name`/`calories`/`proteinG`/`carbsG`/`fatsG` out of the model's
  reply, robust to surrounding prose / code fences.
- **Self-contained ViewModel.** `AssistantViewModel` is created via the
  default `viewModel()` from inside the dialog, so it lives only as long
  as the dialog is mounted, and the LLM handle is released on dismiss.

## Known Issues / Caveats

- **No real Gradle build was performed** — the repo ships without a Gradle
  wrapper. Verified instead via tree-sitter AST parse, standalone kotlinc
  syntax check (zero structural errors; only expected unresolved-reference
  errors from missing classpath), an isolated kotlinc run of the JSON
  parser against four test inputs (passed), and a manual review of imports
  against `build.gradle.kts`. Final build must be done in Android Studio.
- **MediaPipe model is not Gemma 4.** As of writing there is no Gemma 4 —
  Gemma 3 (1B / 4B) is the latest. The dependency is `tasks-genai:0.10.14`,
  which loads any compatible `.task` file (Gemma 2 2B and Gemma 3 1B both
  work; 1B is smaller and recommended for phones).
- **No CAMERA permission requested.** The system camera intent
  (`ActivityResultContracts.TakePicture`) does not require it for the
  capture-to-uri flow. If we ever switch to in-app camera (CameraX), we'd
  need the runtime permission.
