# Sudoku (Android)

A small native Android Sudoku game (Java, no third-party runtime dependencies, minSdk 24)
that imports puzzles in the **SDK81-v1** format.

## SDK81-v1 format

| Property   | Specification                                           |
| ---------- | ------------------------------------------------------- |
| Length     | Exactly 81 ASCII digits                                 |
| `0`        | Empty cell                                              |
| `1`–`9`    | Pre-filled, immutable cell                              |
| Ordering   | Row-major, left to right, top to bottom                 |
| Index      | `row * 9 + column` (zero-based)                         |

Whitespace (spaces, newlines) is ignored, so the human-readable 9-line form can be pasted too.
Example (`SDU-3`, bundled as the default puzzle):

```
000013400080006950650000000960201000100070002000304016000000079025800040019760000
```

## Importing puzzles

- **Menu → Import**: paste one or more puzzles (several puzzles = consecutive blocks of 81 digits).
  The **Paste** button fills the box from the clipboard.
- **Share** text from any app to *Sudoku*.
- **Deep link**: `sudoku://import?p=<81 digits>`

Each import is checked for format, rule conflicts among the givens and solvability; puzzles with
more than one solution are accepted with a warning. Duplicates are not added twice.

## Gameplay

Tap a cell, then a digit. The app has:

- Notes (pencil marks) mode.
- Undo and Erase.
- Hints, taken from the solver's solution.
- *Check for mistakes*, which marks entries that differ from the solution.
- Live highlighting of rule conflicts.
- Remaining-digit counts on the keypad.
- A timer.
- Light and dark themes.
- Hardware keyboard support: digits, arrow keys, Backspace, and `N` for notes.

## Storage

The original puzzle string is stored unchanged. The player's progress is stored separately as
another 81-character SDK81 string, along with notes, elapsed time and solved state, in
SharedPreferences. **Menu → Copy puzzle / Copy progress** export either string.

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk (debug-signed)
./gradlew testDebugUnitTest      # parser/solver + Robolectric UI tests
```

The release build is signed with the debug key so it installs directly. Add your own
`signingConfig` before publishing to a store. The GitHub Actions workflow in
`.github/workflows/android.yml` builds both APKs and uploads them as the `sudoku-apk` artifact.

A prebuilt APK is in [`dist/sudoku.apk`](dist/sudoku.apk).
