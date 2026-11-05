#!/usr/bin/env bash
#
# A-45 D3 locale gate.
#
# Two different promises are checked here, and they are different on purpose:
#
#  1. Arabic must equal English key for key. The app has exactly two user-interface locales, and an
#     owner whose phone is set to Arabic must never be handed an English line — that is the class of
#     defect the audit rounds keep finding (F-K11, Q-K-4), so it is pinned as a set comparison rather
#     than spot-checked.
#  2. French carries the holder-facing notice and nothing else. The theft demand is the only text this
#     app shows to a person it does not know, and it is composed by TheftDemand in Arabic, English and
#     French by hand, because a stranger's phone is not the owner's phone. A missing French notice line
#     is a safety gap; a missing French settings label is not, and values-fr/strings.xml says so at
#     the top. So the French check is "these keys exist" plus "no key here is unknown to English",
#     not "every string in the app".
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

# 2. The holder-facing notice exists in all three languages, with the same single placeholder.
NOTICE_KEYS=(
    theft_demand_channel
    theft_demand_title
    theft_demand_body
    theft_demand_owner_message
    theft_demand_contact
    theft_demand_call
)
for locale in values values-ar values-fr; do
    file="$RES/$locale/strings.xml"
    [ -s "$file" ] || { echo "FAIL: $file is missing or empty"; fail=1; continue; }
    for key in "${NOTICE_KEYS[@]}"; do
        grep -q "name=\"$key\"" "$file" || { echo "FAIL: $file is missing the holder-facing $key"; fail=1; }
    done
    # An orphaned French key is a line no resource ever asks for, and it hides a rename.
    fr_orphan=$(comm -13 <(keys_of "$RES/values/strings.xml") <(keys_of "$file"))
    [[ -n "$fr_orphan" && "$locale" == "values-fr" ]] && {
        echo "FAIL: values-fr defines keys the default locale does not: $fr_orphan"
        fail=1
    }
done

for key in theft_demand_owner_message theft_demand_contact; do
    counts=""
    for locale in values values-ar values-fr; do
        line=$(grep "name=\"$key\"" "$RES/$locale/strings.xml" || true)
        n=$(printf '%s' "$line" | grep -o '%1\$s' | wc -l | tr -d ' ')
        counts="$counts$locale=$n "
    done
    distinct=$(printf '%s\n' $counts | sed 's/.*=//' | sort -u | wc -l | tr -d ' ')
    [ "$distinct" = "1" ] || { echo "FAIL: $key placeholder count differs across locales ($counts)"; fail=1; }
done

# 3. The three notice copies must not be one string pasted three times: a copy-paste here is the same
#    untranslated line wearing a locale folder.
bodies=""
for locale in values values-ar values-fr; do
    bodies="$bodies$(grep 'name="theft_demand_body"' "$RES/$locale/strings.xml" || true)"$'\n'
done
[ "$(printf '%s' "$bodies" | sort -u | grep -c 'theft_demand_body')" = "3" ] \
    || { echo "FAIL: theft_demand_body is not three distinct copies"; fail=1; }

if (( fail > 0 )); then
    echo "LOCALE PARITY FAILED" >&2
    exit 1
fi
echo "LOCALE PARITY PASS — Arabic equals English key for key, and the holder-facing notice is in all three languages"
