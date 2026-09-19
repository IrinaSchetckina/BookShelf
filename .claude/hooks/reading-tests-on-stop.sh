#!/usr/bin/env bash
# Stop quality gate: before Claude ends a turn, run the reading tests once —
# whatever tool made the edits (Edit/Write, or Bash heredoc/python/sed).
#
# Loop guard: exit 2 keeps Claude working and hands it the failure. On the next stop
# stop_hook_active is true; the tests run again, but a failure then only reaches the user
# (systemMessage) and never blocks. So this hook blocks at most once per turn.
set -uo pipefail

input=$(cat)
active=$(jq -r '.stop_hook_active // false' <<<"$input")
cd "${CLAUDE_PROJECT_DIR:?}" || exit 0

# Where reading code and its tests live (the SQLDelight schema feeds the repository tests).
paths=(core/src/*/kotlin/ua/readshelf/domain/reading
       app/shared/src/*/kotlin/ua/readshelf/{data/local,presentation/reading,ui/reading,di}
       app/shared/src/commonMain/sqldelight)

# Skip turns that touched none of it: most turns are conversation, not code.
# The state file holds HEAD at the last green run; its mtime marks when that run finished.
state="${TMPDIR:-/tmp}/readshelf-reading-green-$(printf %s "$PWD" | shasum | cut -c1-8)"
if [ -f "$state" ] && [ "$(git rev-parse HEAD)" = "$(cat "$state")" ] \
   && [ -z "$(find "${paths[@]}" -type f -newer "$state" 2>/dev/null | head -1)" ]; then
  exit 0
fi

log=$(mktemp -t reading-stop)
if ./gradlew :core:jvmTest --tests 'ua.readshelf.domain.reading.*' \
     :app:shared:testAndroidHostTest --tests 'ua.readshelf.data.local.*' \
       --tests 'ua.readshelf.presentation.reading.*' --tests 'ua.readshelf.ui.reading.*' \
       --tests 'ua.readshelf.di.*' >"$log" 2>&1; then
  git rev-parse HEAD > "$state"
  rm -f "$log"
  exit 0
fi

summary=$(grep -E ' FAILED$|AssertionError|expected:|^e: |What went wrong' -A2 "$log" | head -40)

if [ "$active" = "true" ]; then
  # Already blocked once and Claude tried to fix it: tell the user instead of blocking again.
  jq -n --arg m "Reading tests still failing after a fix attempt. Log: $log" '{systemMessage: $m}'
  exit 0
fi

{
  echo "Stop gate: reading tests FAILED — fix before finishing the turn."
  echo "$summary"
  echo "Full Gradle log: $log"
} >&2
exit 2
