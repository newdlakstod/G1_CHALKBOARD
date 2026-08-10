# G1 Sketchbook 2.0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the chalkboard-first UI with the approved adaptive Paper Pals sketchbook experience and publish version 2.0.1.

**Architecture:** Preserve Firebase board documents and drawing synchronization while introducing sketchbook terminology, canvas presets, an entry-cover state, and compact/expanded layouts. Keep drawing coordinates independent of window size; UI recomposes from `screenWidthDp` rather than targeting a Fold model.

**Tech Stack:** Kotlin, Android Views, Firebase Auth/Firestore, Android AppWidget, JUnit 4.

## Global Constraints

- Version name is `2.0.1`; version code is `30`.
- The first screen keeps the approved single-cobalt composition; only typography changes to Cavorting.
- Pretendard is used for utility and explanatory text; Cavorting is used selectively for brand display text.
- Document presets use document-shaped icons; web ratios use rounded-rectangle icons; preset icons have no labels underneath.
- Compact, medium, and expanded layouts derive from available width, not a device model.
- Existing shared drawing synchronization, participants, invitations, undo/redo, cover images, and widgets remain functional.

---

### Task 1: Version, fonts, and design tokens

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/res/font/cavorting.otf`
- Create: `app/src/main/java/com/gdonotice/app/SketchbookDesign.kt`
- Test: `app/src/test/java/com/gdonotice/app/SketchbookDesignTest.kt`

**Interfaces:**
- Produces: `SketchbookDesign.windowMode(widthDp)`, palette constants, canvas preset definitions.

- [ ] Write tests for compact/medium/expanded width classification and preset categories.
- [ ] Run `gradlew.bat testDebugUnitTest` and verify the new tests fail because the API is missing.
- [ ] Implement the design tokens, preset model, and version/font resources.
- [ ] Re-run unit tests and verify they pass.

### Task 2: Entry cover and authentication routing

**Files:**
- Modify: `app/src/main/java/com/gdonotice/app/MainActivity.kt`
- Test: `app/src/test/java/com/gdonotice/app/EntryRouteTest.kt`

**Interfaces:**
- Consumes: `SketchbookDesign.windowMode(widthDp)`.
- Produces: deterministic `EntryRoute.resolve(isSignedIn, hasEntered)` behavior.

- [ ] Write failing tests proving first-time users see the cover, Log in authenticates, and signed-in Enter routes home.
- [ ] Run the focused test and confirm the expected failure.
- [ ] Implement the cobalt cover without changing its approved composition and apply Cavorting to all cover copy.
- [ ] Re-run focused and full unit tests.

### Task 3: Adaptive home and sketchbook library

**Files:**
- Create: `app/src/main/java/com/gdonotice/app/SketchbookHomeView.kt`
- Create: `app/src/main/java/com/gdonotice/app/SketchbookLibraryView.kt`
- Modify: `app/src/main/java/com/gdonotice/app/MainActivity.kt`
- Test: `app/src/test/java/com/gdonotice/app/HomeActionTest.kt`

**Interfaces:**
- Produces: Home actions `CONTINUE`, `NEW`, `JOIN`; library sort and ownership sections.

- [ ] Write failing tests for home actions and signed-in routing.
- [ ] Run tests and confirm the new behavior is absent.
- [ ] Implement compact and expanded recompositions with recent sketchbook, new, join, array thumbnails, and avatar account entry.
- [ ] Re-run all unit tests.

### Task 4: Canvas preset selection and paper canvas

**Files:**
- Create: `app/src/main/java/com/gdonotice/app/CanvasPresetView.kt`
- Modify: `app/src/main/java/com/gdonotice/app/DrawingView.kt`
- Modify: `app/src/main/java/com/gdonotice/app/MainActivity.kt`
- Test: `app/src/test/java/com/gdonotice/app/CanvasPresetTest.kt`

**Interfaces:**
- Consumes: `CanvasPreset`, `CanvasKind`.
- Produces: aspect ratio, paper size metadata, custom width/height validation.

- [ ] Write failing tests for preset ratios, custom validation, and resize modes.
- [ ] Run tests and confirm failure for missing behavior.
- [ ] Implement icon-only preset selection, paper background, aspect-preserving drawing viewport, and custom sizing.
- [ ] Re-run unit tests.

### Task 5: Drawing tools, account surface, and compatibility

**Files:**
- Modify: `app/src/main/java/com/gdonotice/app/MainActivity.kt`
- Modify: `app/src/main/java/com/gdonotice/app/BoardWidget.kt`
- Modify: `app/src/main/java/com/gdonotice/app/WidgetConfigActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/gdonotice/app/LegacyBoardCompatibilityTest.kt`

**Interfaces:**
- Preserves existing Firestore board fields and defaults missing canvas metadata to the legacy ratio.

- [ ] Write a failing compatibility test for legacy board metadata.
- [ ] Verify the test fails for the missing migration default.
- [ ] Implement the compact tool dock, account sheet, sketchbook terminology, and legacy/widget compatibility.
- [ ] Re-run all tests.

### Task 6: Build, device verification, and release

**Files:**
- Modify: `README.md`
- Create: `release-notes-v2.0.1.md`

- [ ] Run `gradlew.bat testDebugUnitTest lintDebug assembleDebug` and require exit code 0.
- [ ] Install on the configured emulator and verify compact portrait, expanded landscape, login/enter, new/join, presets, drawing, sync, and back navigation.
- [ ] Review `git diff --check`, `git status --short`, and the final APK path.
- [ ] Commit only project changes, push the public repository, and publish GitHub release `v2.0.1` with the APK and release notes.
