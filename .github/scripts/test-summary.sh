#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# Gradle does not print per-class test counts, so the JUnit XML reports are the
# only machine-readable evidence of what actually executed in CI.
shopt -s nullglob
files=(app/build/test-results/*/*.xml)

if [ ${#files[@]} -eq 0 ]; then
    echo "## Test execution evidence" >> "$GITHUB_STEP_SUMMARY"
    echo "" >> "$GITHUB_STEP_SUMMARY"
    echo "NO XML TEST REPORTS FOUND — test execution cannot be quantified." >> "$GITHUB_STEP_SUMMARY"
    exit 0
fi

totals=$(awk -F'"' '
    /<testsuite / {
        for (i = 1; i <= NF; i++) {
            if ($i ~ /tests=$/)      { t += $(i + 1) }
            if ($i ~ /failures=$/)   { f += $(i + 1) }
            if ($i ~ /errors=$/)     { e += $(i + 1) }
            if ($i ~ /skipped=$/)    { s += $(i + 1) }
        }
    }
    END { printf "%d %d %d %d", t, f, e, s }
' "${files[@]}")

read -r tests failures errors skipped <<< "$totals"
passed=$((tests - failures - errors - skipped))

{
    echo "## Test execution evidence (JVM unit tests)"
    echo ""
    echo "| Metric | Value |"
    echo "| --- | --- |"
    echo "| Test classes | ${#files[@]} |"
    echo "| Tests run | $tests |"
    echo "| Passed | $passed |"
    echo "| Failures | $failures |"
    echo "| Errors | $errors |"
    echo "| Skipped | $skipped |"
    echo ""
    echo "Instrumented (androidTest) tests are compiled by CI but executed only on a device."
} >> "$GITHUB_STEP_SUMMARY"

printf 'CI TEST SUMMARY: %s tests, %s passed, %s failures, %s errors, %s skipped\n' \
    "$tests" "$passed" "$failures" "$errors" "$skipped"
