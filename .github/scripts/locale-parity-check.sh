#!/usr/bin/env bash
#
# Locale gate.
#
# Arabic must equal English key for key. The app has exactly two user-interface locales, and an owner
# whose phone is set to Arabic must never be handed an English line — that is the class of defect the
# audit rounds keep finding (F-K11, Q-K-4), so it is pinned as a set comparison rather than
# spot-checked.
#
# Design B (2026-11-06) deleted the trilingual lock-screen notice together with the lock it belonged
# to, so there is no third locale left to check. A phone set to a language the app does not ship reads
# English through Android's own fallback; there is no hand-composed notice for a stranger any more, and
# g4b fails the build if values-fr comes back.
set -uo pipefail
cd "$(dirname "$0")/../.."

RES=app/src/main/res
fail=0

keys_of() { grep -ho 'name="[A-Za-z0-9_]*"' "$1" | sort -u; }

# 1. English and Arabic agree on which strings exist, in strings.xml and in plurals.xml.
for file in strings.xml plurals.xml; do
    en="$RES/values/$file"
    ar="$RES/values-ar/$file"
    [ -s "$en" ] || { echo "FAIL: $en is missing or empty"; fail=1; continue; }
    [ -s "$ar" ] || { echo "FAIL: $ar is missing or empty"; fail=1; continue; }
    missing=$(comm -23 <(keys_of "$en") <(keys_of "$ar"))
    orphan=$(comm -13 <(keys_of "$en") <(keys_of "$ar"))
    if [[ -n "$missing" ]]; then
        echo "FAIL: values-ar is missing keys English has in $file:"
        echo "$missing" | sed 's/^/        /'
        fail=1
    fi
    if [[ -n "$orphan" ]]; then
        echo "FAIL: values-ar holds keys English does not define in $file:"
        echo "$orphan" | sed 's/^/        /'
        fail=1
    fi
done

if (( fail > 0 )); then
    echo "LOCALE PARITY FAILED" >&2
    exit 1
fi
echo "LOCALE PARITY PASS — Arabic equals English key for key"
