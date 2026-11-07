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

## Game

- **Home**: continue the open puzzle (or play the next level), Levels, Daily Challenge,
  My Puzzles, Import, Stats, Settings and Help.
- **Levels**: 4 packs (Easy, Medium, Hard, Expert) of 50 levels each. A level unlocks when the one
  before it is solved. Finishing with no hints and no mistakes earns ★★★, and each hint or mistake
  costs one star. Best times and stars are kept when you replay.
- **Daily Challenge**: a Medium puzzle generated from the date, so everyone gets the same one,
  with a day streak.
- **Gameplay**:
  - Notes (pencil marks) mode.
  - Undo and Erase.
  - Hints that fill the most constrained cell.
  - Mistake counter and *Check for mistakes*.
  - Conflict highlighting.
  - Remaining-digit counts on the keypad.
  - A timer.
  - Light and dark themes.
  - Hardware keyboard support: digits, arrows, Backspace, and `N` for notes.
- **Settings**: show mistakes immediately, highlight matching digits, remove notes automatically,
  keep the screen on, and reset progress.

### Level packs

Levels are bundled as SDK81 files in `app/src/main/assets/levels/*.txt`, one puzzle per line.
`tools/generate-levels.sh` regenerates them deterministically. Every puzzle has exactly one
solution and is graded by the techniques it needs:

| Pack   | Needs                                                   |
| ------ | ------------------------------------------------------- |
| Easy   | Singles only, 37–38 clues                               |
| Medium | Singles only, 30–31 clues                               |
| Hard   | Locked candidates and/or naked pairs                    |
| Expert | More than the above (e.g. X-wings, chains or guessing)  |

## Importing puzzles

- **Home → Import puzzle**: paste one or more puzzles (several puzzles = consecutive blocks of
  81 digits). The **Paste** button fills the box from the clipboard.
- **Share** text from any app to *Sudoku*.
- **Deep link**: `sudoku://import?p=<81 digits>`

Each import is checked for format, rule conflicts among the givens and solvability. Puzzles with
more than one solution are accepted with a warning, and mistakes aren't counted for them.
Imported puzzles appear under **My Puzzles**.

## Storage

The original puzzle string is stored unchanged. The player's progress is stored separately as
another 81-character SDK81 string, along with notes, time, hints, mistakes, stars and best time,
in SharedPreferences. **Puzzle menu → Copy puzzle / Copy progress** export either string.

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk (debug-signed)
./gradlew testDebugUnitTest      # parser, solver, generator, level packs + Robolectric game-flow tests
./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -PscreenshotDir=/tmp/shots   # render screens to PNG
```

The release build is signed with the debug key so it installs directly. Add your own
`signingConfig` before publishing to a store. The GitHub Actions workflow in
`.github/workflows/android.yml` builds both APKs and uploads them as the `sudoku-apk` artifact.

A prebuilt APK is in [`dist/sudoku.apk`](dist/sudoku.apk).
