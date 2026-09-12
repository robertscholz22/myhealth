#!/usr/bin/env bash
# MyHealth verification script (PLAN P0.5).
# Runs the standard ALL command set, then reports APK size and the executed unit-test count.
# Exits non-zero if the build, the tests or lint fail.
set -uo pipefail

export JAVA_HOME="${JAVA_HOME:-/home/robert/jdk/current}"
export ANDROID_HOME="${ANDROID_HOME:-/home/robert/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

APK="app/build/outputs/apk/debug/app-debug.apk"
RESULTS_DIR="app/build/test-results/testDebugUnitTest"

echo "== MyHealth verify =="
echo "JAVA_HOME    : $JAVA_HOME"
echo "ANDROID_HOME : $ANDROID_HOME"
echo

./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug "$@"
STATUS=$?
if [ $STATUS -ne 0 ]; then
    echo "FAIL: gradle build/test/lint failed (exit $STATUS)"
    exit $STATUS
fi

if [ ! -f "$APK" ]; then
    echo "FAIL: APK not found at $APK"
    exit 1
fi

APK_BYTES=$(stat -c%s "$APK")
APK_MB=$(awk -v b="$APK_BYTES" 'BEGIN { printf "%.1f", b / 1048576 }')

TESTS=0
FAILURES=0
ERRORS=0
SKIPPED=0
if compgen -G "$RESULTS_DIR"/TEST-*.xml > /dev/null; then
    read -r TESTS FAILURES ERRORS SKIPPED <<< "$(
        grep -ho '<testsuite [^>]*' "$RESULTS_DIR"/TEST-*.xml \
        | awk '{
            t=f=e=s=0
            for (i = 1; i <= NF; i++) {
                split($i, kv, "=")
                gsub(/"/, "", kv[2])
                if (kv[1] == "tests")    t = kv[2]
                if (kv[1] == "failures") f = kv[2]
                if (kv[1] == "errors")   e = kv[2]
                if (kv[1] == "skipped")  s = kv[2]
            }
            T += t; F += f; E += e; S += s
        } END { print T+0, F+0, E+0, S+0 }'
    )"
fi

echo
echo "== Result =="
echo "APK          : $APK_MB MB ($APK_BYTES bytes)"
echo "Unit tests   : $TESTS executed, $FAILURES failures, $ERRORS errors, $SKIPPED skipped"
echo "Lint report  : app/build/reports/lint-results-debug.html"

if [ "$TESTS" -lt 2 ]; then
    echo "FAIL: expected at least 2 executed unit tests, got $TESTS"
    exit 1
fi
if [ "$FAILURES" -ne 0 ] || [ "$ERRORS" -ne 0 ]; then
    echo "FAIL: unit tests reported failures/errors"
    exit 1
fi

echo "VERIFY OK"
exit 0
