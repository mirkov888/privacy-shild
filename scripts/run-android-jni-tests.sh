#!/usr/bin/env bash
set -uo pipefail

LOGCAT_FILE="${RUNNER_TEMP:-/tmp}/mora-emulator-logcat.txt"
TEST_LOG="instrumented-tests.log"
: > "$TEST_LOG"
: > "$LOGCAT_FILE"

adb wait-for-device
adb logcat -c
adb logcat -v threadtime > "$LOGCAT_FILE" 2>&1 &
LOGCAT_PID=$!

cleanup() {
  if kill -0 "$LOGCAT_PID" 2>/dev/null; then
    kill "$LOGCAT_PID" 2>/dev/null || true
    wait "$LOGCAT_PID" 2>/dev/null || true
  fi
  adb logcat -d -v threadtime >> "$LOGCAT_FILE" 2>/dev/null || true
  cp "$LOGCAT_FILE" emulator-logcat.txt 2>/dev/null || true
}
trap cleanup EXIT

echo "Starting connectedDebugAndroidTest..."
if ./gradlew --no-daemon --stacktrace connectedDebugAndroidTest > "$TEST_LOG" 2>&1; then
  TEST_STATUS=0
else
  TEST_STATUS=$?
fi

echo "connectedDebugAndroidTest exit code: $TEST_STATUS"
echo "=== Instrumentation output ==="
tail -n 200 "$TEST_LOG" || true
echo "=== Emulator crash diagnostics ==="
grep -n -E "FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|DEBUG|AndroidRuntime|UnsatisfiedLinkError|dlopen failed|tombstoned|crash_dump|INSTRUMENTATION_FAILED|Process crashed" "$LOGCAT_FILE" | tail -n 200 || true
exit "$TEST_STATUS"
