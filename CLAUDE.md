# Weather Wallpaper

A South African live wallpaper for Android (Kotlin). It has three scenes, Cape Town, Savanna and Jhb City, and they follow the live weather and real sky.

Before starting work, read the full handover in @HANDOVER.md. It covers:
- the project state and branches
- the recent features
- a code map
- how to build and test on the phone
- the user's preview-first workflow
- known gotchas

## Rules

- **Branch:** work on `Battery-Optimize` unless told otherwise. A new scene gets its own new branch.
- **Preview first:** for visual changes, show an in-chat `show_widget` preview with an "Apply … to my phone" button before writing Kotlin.
- **Ship it:** after a change, build with `./gradlew assembleDebug`, install via adb with timeouts, check it with a screenshot, then commit and push.
- **Push retries:** pushes to Gitea often fail auth on the first try, so retry once.
- **Reset the phone:** after testing, reset it with `override auto`, `fireworks off` and the user's scene.
- **Keep the handover current:** update `HANDOVER.md` when the project state changes significantly.
