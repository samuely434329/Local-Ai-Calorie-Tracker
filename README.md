# Calorie Tracker — Hand-Drawn Sketch Edition

A single-screen Jetpack Compose calorie tracker styled to look like a pencil
sketch on graph paper.

## What it does

- Daily calorie ring with sketchy strokes and a big handwritten number
- Three macro bars (Protein, Carbs, Fats) drawn with hand-drawn outlines and
  pencil-hatching fill
- Food log with time + name + kcal; tap a row to edit or delete it
- Editable daily goals (overflow menu → "Edit goals")
- Procedural smudges and grain on the paper background — no bitmap textures
- Persists food entries in Room and goals in DataStore

## Project layout

```
CalorieTracker/
├── settings.gradle.kts
├── build.gradle.kts            # plugin versions only
├── gradle.properties
└── app/
    ├── build.gradle.kts        # Compose, Room, DataStore, KSP
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/imagecaltracker/
        │   ├── MainActivity.kt
        │   ├── data/                 # Room + DataStore
        │   │   ├── CalorieDatabase.kt
        │   │   ├── FoodEntry.kt
        │   │   ├── FoodEntryDao.kt
        │   │   ├── FoodRepository.kt
        │   │   └── GoalsRepository.kt
        │   └── ui/
        │       ├── MainScreen.kt
        │       ├── MainViewModel.kt
        │       ├── Dialogs.kt        # Goals + Edit-entry dialogs
        │       ├── sketch/           # Procedural drawing primitives
        │       │   ├── SketchUtils.kt        # jittered strokes, smudges, grain
        │       │   ├── SketchyProgress.kt    # ring + macro bar
        │       │   ├── SketchyButton.kt
        │       │   └── SketchyTextField.kt
        │       └── theme/
        │           ├── Color.kt
        │           ├── Theme.kt
        │           └── Type.kt              # Patrick Hand + Caveat (downloadable)
        └── res/
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
            ├── values/{strings,themes,colors,font_certs}.xml
            └── xml/{backup_rules,data_extraction_rules}.xml
```

## How to import into Android Studio

This project is delivered as plain source — no Gradle wrapper binaries, no
`.idea/` files, no precompiled APK. Open it once in Android Studio and the
IDE will fill in the rest:

1. Open Android Studio (Hedgehog 2023.1.1 or newer recommended; tested
   target is **AGP 8.5.2 / Kotlin 1.9.24 / JDK 17**).
2. **File → Open…** and select the `CalorieTracker/` directory.
3. Android Studio will:
   - Detect the missing Gradle wrapper and offer to generate it. Accept.
   - Sync the project. The first sync downloads dependencies from
     Google's Maven repo — this can take a few minutes the first time.
4. Make sure you have **JDK 17** selected for Gradle JVM
   (Settings → Build, Execution, Deployment → Build Tools → Gradle).
5. Plug in a device or start an API 26+ emulator and hit **Run**.

If sync fails because of the Compose Compiler version, bump the
`kotlinCompilerExtensionVersion` in `app/build.gradle.kts` to one that
matches the Kotlin version Android Studio offers. The official
compatibility table lives at
<https://developer.android.com/jetpack/androidx/releases/compose-kotlin>.

## Notes

- **Min SDK 26**, target SDK 34. We use `java.time` (JSR-310) directly,
  which requires API 26+.
- **Downloadable fonts**: typography uses `Patrick Hand` and `Caveat` via
  `androidx.compose.ui.text.googlefonts`. The first launch downloads them
  through Google Play services, then caches.
- **No bitmap assets** are required. The launcher icon is a vector;
  smudges and grain are drawn at runtime via Compose Canvas.
- **No tests** are included by default. If you'd like coverage, the
  natural targets are `MainViewModel` (StateFlow assembly + add/edit/delete)
  and the DAO (with an in-memory Room database).

## Why it looks the way it does

- **Strokes**: every "outline" is drawn as 2–3 layered passes of
  pseudo-randomly jittered line segments instead of a clean geometric
  primitive. Seeds are fixed per composable so the wobble stays stable
  across recomposition.
- **Smudges**: clusters of overlapping translucent ellipses, drawn behind
  content (not on top), so they read as paper texture rather than
  obscuring data.
- **Grain**: hundreds of tiny, very low-alpha dots scattered with a fixed
  seed; drawn on top of content for a fine pencil-fiber feel.
- **Graph paper**: vertical and horizontal lines at a fixed cell size,
  with slight per-line alpha jitter so the grid never looks printed.
