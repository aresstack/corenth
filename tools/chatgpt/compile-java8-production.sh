#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/chatgpt/classes"
SOURCE_LIST="$ROOT_DIR/build/chatgpt/java8-production-sources.txt"
SKIPPED_LIST="$ROOT_DIR/build/chatgpt/skipped-external-sources.txt"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$(dirname "$SOURCE_LIST")"

find "$ROOT_DIR" \
    -path "$ROOT_DIR/.git" -prune -o \
    -path "$ROOT_DIR/.gradle" -prune -o \
    -path "$ROOT_DIR/build" -prune -o \
    -path "$ROOT_DIR/research" -prune -o \
    -path '*/src/test/*' -prune -o \
    -path '*/build/*' -prune -o \
    -path '*/out/*' -prune -o \
    -path '*/target/*' -prune -o \
    -name '*.java' -print | sort > "$SOURCE_LIST.all"

: > "$SOURCE_LIST"
: > "$SKIPPED_LIST"

while IFS= read -r source_file; do
    if grep -Eq '^import (org\.apache\.lucene|org\.junit|org\.junit\.jupiter|opennlp\.|com\.aresstack\.winproxy|com\.aresstack\.keepassrpc)' "$source_file"; then
        printf '%s\n' "${source_file#$ROOT_DIR/}" >> "$SKIPPED_LIST"
    else
        printf '%s\n' "$source_file" >> "$SOURCE_LIST"
    fi
done < "$SOURCE_LIST.all"

if [ ! -s "$SOURCE_LIST" ]; then
    echo "No dependency-free Java production files found." >&2
    exit 1
fi

javac -source 8 -target 8 -encoding UTF-8 -d "$BUILD_DIR" @"$SOURCE_LIST"

compiled_count="$(wc -l < "$SOURCE_LIST" | tr -d ' ')"
skipped_count="$(wc -l < "$SKIPPED_LIST" | tr -d ' ')"

echo "Compiled $compiled_count Java 8 production files."
echo "Skipped $skipped_count production files with external imports."

if [ "$skipped_count" != "0" ]; then
    echo "Skipped files are listed in ${SKIPPED_LIST#$ROOT_DIR/}."
fi
