#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

if [ "${CHATGPT_FULL_GRADLE_BUILD:-false}" = "true" ]; then
    export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.chatgpt/gradle-home}"
    "$ROOT_DIR/gradlew" --no-daemon --offline clean build
    exit $?
fi

bash "$ROOT_DIR/tools/chatgpt/compile-java8-production.sh"
