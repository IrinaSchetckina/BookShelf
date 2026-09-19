#!/usr/bin/env bash
# PostToolUse quality gate: after an Edit/Write of a Kotlin file in the :core reading module,
# run its tests. Exit 2 hands stderr back to Claude as feedback; exit 0 stays silent.
#
# Only Edit/Write reach this hook. Edits made through Bash (heredoc, python, sed) do not;
# the Stop hook is the backstop for those.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
case "$file" in
  */core/src/*/kotlin/ua/readshelf/domain/reading/*.kt) ;;   # main and test sources
  *) exit 0 ;;                                                 # anything else: not our business
esac

cd "${CLAUDE_PROJECT_DIR:?}" || exit 0
log=$(mktemp -t reading-tests)
if ./gradlew :core:jvmTest --tests 'ua.readshelf.domain.reading.*' >"$log" 2>&1; then
  rm -f "$log"
  exit 0
fi

{
  echo "Quality gate: :core reading tests FAILED after editing ${file#"$CLAUDE_PROJECT_DIR"/}."
  echo "Fix this before moving on."
  # Failed tests, assertion messages and compiler errors; the full log stays on disk.
  grep -E ' FAILED$|AssertionError|expected:|^e: |No tests found|What went wrong' -A2 "$log" | head -40
  echo "Full Gradle log: $log"
} >&2
exit 2
