#!/usr/bin/env bash
# Regenerates the bundled level packs in app/src/main/assets/levels.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=app/src/main/java/com/stalingino/sudoku
OUT=$(mktemp -d)
javac -d "$OUT" $SRC/{Sdk81,Solver,Grader,Generator,Difficulty}.java tools/GenerateLevels.java
java -cp "$OUT" GenerateLevels app/src/main/assets/levels
rm -rf "$OUT"
