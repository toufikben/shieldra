#!/usr/bin/env bash
# Publishes the device APK pair to the rolling draft release.
#
# Q-K-5 made the required-APK rule real: a run whose APK upload fails must not report success. The
# artifact channel cannot satisfy that rule while GitHub's artifact storage is exhausted, so the
# authoritative "APK pair from the same CI run" is now this release, and the artifact copy is a
# convenience. This script is therefore REQUIRED, not continue-on-error: if either APK is missing
# from the workspace, or the release does not carry both afterwards, the run fails.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

TAG="${DEVICE_APK_TAG:-device-apks}"
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
HEAD_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"
RUN_ID="${GITHUB_RUN_ID:-local}"
REPOSITORY="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${REPOSITORY}/actions/runs/${RUN_ID}"
TITLE="Device APK pair (rolling, newest successful run)"
NOTES="Authoritative device-APK channel. head=${HEAD_SHA} run=${RUN_ID} run_url=${RUN_URL} — both assets are overwritten by every successful run, so the pair is always from one build of one commit."

fail() { echo "FAIL: $1" >&2; exit 1; }

[ -s "$APK" ] || fail "no debug APK in the workspace: $APK"
[ -s "$TEST_APK" ] || fail "no androidTest APK in the workspace: $TEST_APK"

if gh release view "$TAG" >/dev/null 2>&1; then
  gh release edit "$TAG" --draft --target "$HEAD_SHA" --title "$TITLE" --notes "$NOTES"
else
  gh release create "$TAG" --draft --target "$HEAD_SHA" --title "$TITLE" --notes "$NOTES"
fi

gh release upload "$TAG" "$APK" "$TEST_APK" --clobber

assets="$(gh release view "$TAG" --json assets --jq '[.assets[].name] | join(" ")')"
case " ${assets} " in
  *" app-debug.apk "*) ;;
  *) fail "the ${TAG} release does not carry app-debug.apk (assets: ${assets})" ;;
esac
case " ${assets} " in
  *" app-debug-androidTest.apk "*) ;;
  *) fail "the ${TAG} release does not carry app-debug-androidTest.apk (assets: ${assets})" ;;
esac

echo "PASS: the device APK pair is published on draft release ${TAG} for ${HEAD_SHA}"
