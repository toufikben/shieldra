# Batch K — Security / Privacy / Device audit (protocol §38: the point for security sign-off)

**Status: STATIC HALF DELIVERED. DEVICE HALF NOT RUN — the owner's RMX3910 was `offline` to `adb` for
this whole session, so nothing on-device was re-measured. NO SIGN-OFF IS GIVEN.**

This report deliberately does **not** say "G6 PASS" anywhere. §24-§25's rule — cite the exact official
source, never an old summary — is applied to the release-policy finding in §6, and every repository
claim below was re-opened in the working tree at `f844ffb35dc798cdb1bf21263ecf61cab1b00d8b`, not
inherited from an earlier batch report.

Labels: **SOURCE** (official page, quoted with the line in the stripped text), **REPO** (read in this
repository at `f844ffb`), **DEVICE** (measured on the phone — **there are none in this report**),
**NOT RUN** (not proven).

The checklist this report walks is the committed one: protocol
`docs/authority/shieldra-master-audit-controlled-protocol.md:1096-1115` (§28 "SECURITY / PRIVACY AUDIT")
names nine things to look for — secrets in source, API keys, passwords, tokens, credentials, unsafe
logging, personal data in logs, accidental camera/location permissions, unnecessary network access,
misleading security claims — and `:753` is the sentence that makes a claim a defect: *"Do not label a
feature 'secure', 'encrypted', 'protected', 'delivered', or 'verified' unless that claim is technically
justified."*

---

## 1. Bottom line

**Clean, with evidence:** no secret of any kind is in the repository; the permission surface is three
declared permissions with **no `INTERNET` permission at all** and exactly one of them ever asked in a
runtime prompt; logging is one call with a
static reason; backups are off and every backup domain is excluded; evidence and database live in
`noBackupFilesDir`; the crypto layer can only be built as AES-256-GCM with a namespaced Keystore alias,
deterministic length-prefixed AAD, no software-key fallback, and a runtime StrongBox probe that fails
closed; key rotation really does run on every approved process start.

**Not clean, and these are the reasons there is no sign-off:**

1. The **Premium tab is a live, false advertisement** (§3). It shows a price, a "Buy Lifetime" button and
   five feature claims, of which **none** exists in the code — including a free-tier sentence that names
   Lock Guard, SIM Guard and Email delivery.
2. **Retention is decided, coded, tested — and never executed** (§4). No production file reads the
   retention policy, and `app/src/main/kotlin` contains **zero delete calls of any kind**, so nothing
   ever expires.
3. **The export gate has no consumer** (§4.3). Export does not happen because a button no-ops, not
   because the six-gate policy is consulted.
4. **Play will reject this build today** (§6). The current official target-API requirement has been in
   force since 2026-08-31 and `targetSdk = 35`.
5. The device half of the audit was **NOT RUN** (§7) — which by itself forbids a security sign-off.

Items 1, 2, 4 and the sign-off itself need owner decisions (§8). Item 3 is ordinary engineering and is
recorded as a finding without a question, because the outcome today is already correct.

---

## 2. Verified clean, with the probe that proved it

| Dimension | Probe | Result |
|---|---|---|
| Secrets, keys, tokens, credentials | `git ls-files \| grep -iE "\.jks$\|\.keystore$\|local.properties\|\.env$\|credentials\|secrets\|google-services\|\.p12$\|\.pem$"` over all 303 tracked files | **no match at all** (REPO) |
| Secret-shaped literals in code | `git grep -niE "api[_-]?key\|secret\|passwd\|password\|token\|client_id\|AIza[0-9A-Za-z_-]{20}\|-----BEGIN"` over `*.kt *.kts *.xml *.json *.properties *.yml` excluding `docs/` | only `design/tokens/*` (a design-token package), `javax.crypto.SecretKey`, and one doc word — **no value** (REPO) |
| Signing material in build or CI | `git grep -nE "signingConfig\|storeFile\|keyPassword\|storePassword\|keystore"` in build/workflow/properties files | **no match**: release signing does not exist yet — that is Batch L, and it also proves no keystore is committed (REPO) |
| Permissions | `app/src/main/AndroidManifest.xml:20-22` | exactly `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`. **No `INTERNET`, no camera, no location, no `READ_PHONE_STATE`, no boot permission (A-10)** (REPO) |
| Components | `AndroidManifest.xml:33-56` | one `LAUNCHER` activity, one `exported="false"` service with `foregroundServiceType="specialUse"`. **No receiver, no provider, no deep link** (REPO) |
| Network surface | `git grep -nE "okhttp\|retrofit\|HttpURL\|firebase\|admob\|Analytics\|WorkManager\|JobScheduler\|AlarmManager"` over `app/src` | one hit: `platform/PlatformBoundaries.kt:5 interface WorkManagerBoundary` — a boundary that exists to keep WorkManager out. **No HTTP client, no analytics, no ads SDK** (REPO) |
| Dependencies | `app/build.gradle.kts:39-56` | androidx activity/navigation/room + Compose BOM + KSP Room compiler only. Nothing that could phone home (REPO) |
| Logging | `git grep -cnE "Log\.[diveiw]\|println(\|printStackTrace"` over `app/src/main/kotlin` | **exactly one** call site: `detection/MotionMonitoringService.kt:195 Log.w(TAG, "a confirmed Movement could not be recorded: ${confirmation.reason}")`. The reasons interpolated are the static contract strings in `domain/InMemoryEventPipeline.kt:146,155,165` ("Evidence state update conflicted" …) — **no event content, no path, no identifier, no user data** (REPO) |
| Backup / transfer | `AndroidManifest.xml:26-28` + `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml` | `allowBackup="false"`, `full-backup-content` empty, and `cloud-backup` **and** `device-transfer` each exclude `sharedpref`, `database` and `file` wholesale (REPO) |
| Storage location | `storage/LocalStorageFactory.kt:48-51` | evidence directory is `File(context.noBackupFilesDir, …)` and creation is guarded by a `canonicalFile.startsWith(noBackupFilesDir)` `require` — a path-traversal attempt fails closed (REPO) |
| Crypto parameters | `storage/EncryptionPolicy.kt:8-33` | `keySizeBits` is `require`d to be exactly 256, transformation is the constant `AES/GCM/NoPadding`, provider `AndroidKeyStore`, `GCM_TAG_BITS = 128`, `GCM_IV_BYTES = 12`, and the class has **no default instance** — "There is intentionally no default instance: product/security review must select and record these values" (REPO) |
| IV handling | `storage/AndroidKeystoreEncryptor.kt:45-56` | the IV is taken from Keystore after encrypt and `require`d to be 12 bytes; a caller-provided IV is never accepted (the comment at `:46-48` records the real-device rejection) (REPO) |
| StrongBox | `storage/AndroidKeystoreEncryptor.kt:92-97` | `Build.VERSION.SDK_INT >= P` **and** `hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)`; with no application context it returns `false` rather than guessing from the SDK level. Preferred at generation (`:148-157`) with silent fall-through to the standard Keystore, exactly the recorded §2.3 decision (REPO) |
| No software-key fallback | `storage/AndroidKeystoreEncryptor.kt:163-168, 186-190, 171-174` | every Keystore failure throws `KeyMaterialUnavailableException` with the text "no software fallback is permitted"; a missing alias is never silently created on the decrypt path (REPO) |
| AAD | `storage/EvidenceAadBuilder.kt:25-62` | `AAD-v1`: version byte + 4-byte big-endian length + UTF-8 bytes, twice; deterministic, no JSON/Base64/platform charset/locale dependence, `require`-fails-closed on blank or non-lowercase `objectClass` (REPO) |
| Key rotation is really wired | `storage/LocalStorageFactory.kt:74-87` + `app/ShieldraApp.kt:68-76` | the factory builds `EvidenceRotationOrchestrator` and `ShieldraApp.checkEvidenceRotation` calls `storage.rotateEvidenceIfNeeded(manual = false)` on every approved process start, off the main thread. The due rule is the approved one: `storage/EvidenceKeyLifecycle.kt:103` → `EvidenceKeyPolicy.rotationDue` with `ROTATION_INTERVAL_DAYS = 90L` (`storage/EvidenceKeyPolicy.kt:36`) (REPO) |
| Failure semantics | `storage/EvidenceKeyLifecycle.kt:92-115` + `LocalStorageFactory.kt:97-120` | successor lookup, corrupted entry or surviving reference returns `blocked(...)` and keeps the old key in service; opening storage returns `RecoveryRequired` and never deletes the database (REPO) |
| Dashboard overclaiming | `presentation/guard/LockGuardDecision.kt:32-49`, `presentation/guard/LockGuardDashboardFactory.kt:23-56` | the runtime projection **cannot** produce `Protected`, `Suspicious`, `SecurityEvent`, `GuardStatus.Active` or a `lastCheckSeconds` value — the enum mapping has no such branch, `enabled = false` on every tile, `isPremiumLocked` is never set (default `false` in `presentation/model/PresentationModels.kt:20`), and the only tile that can look active is Motion, driven by `motionClaim` from the service itself |

That last row is the most important clean result: the §31 vocabulary (`PROTECTED`, `Protection is active`)
exists in `res/values/strings.xml:168-169` but is reachable only through `ProtectionVisualState.Protected`,
and the runtime decision function has no path to that state.

---

## 3. Finding F-K1 — the Premium tab is a live false claim (HIGH)

**REPO.** `navigation/ShieldraNavHost.kt:293-301` is a real `composable()` route, not a preview:

```kotlin
composable(ShieldraRoutes.PREMIUM) {
    PremiumScreen(
        model = DemoData.premium(),
        callbacks = PremiumCallbacks(
            onBuy = { defer() },
            onRestore = { defer() },
        ),
    )
}
```

`presentation/demo/DemoData.kt:100-110` is the model it shows, and every value in it is a string
resource that asserts a product:

| Claim on screen | Source | Reality in this repository |
|---|---|---|
| "Buy Lifetime — 9.99" | `res/values/strings.xml:167` + `:201 demo_premium_price` | no billing library in `app/build.gradle.kts:39-56`, no `signingConfig`, no `BILLING` permission, no purchase code. The button shows `phase3_deferred` ("This action is intentionally deferred in Phase 3.", `:122`) |
| "One payment. Lifetime." | `:48` | nothing is sold |
| "Multiple delivery channels — Email · WhatsApp · Telegram" | `:203-204` | no email, no WhatsApp, no Telegram, no network permission (§2) — protocol §30 forbids them |
| "Advanced delivery receipts" | `:207` | delivery is local-only; `presentation/model/PresentationModels.kt:36` has `Delivered`/`Expired` states but no external delivery exists |
| "No ads" | `:208` | there are no ads in any build, free or paid; the promise implies an ad-supported variant that does not exist |
| "Extended history — 90 days · 500 events" | `:205-206` | 90 days is the *approved free* metadata window (`storage/EvidenceRetentionPolicy.kt:52`), and no 500-event cap exists anywhere |
| "Free includes: Lock Guard · Motion Guard · SIM Guard · Panic · Email delivery." | `:209` (and `values-ar/strings.xml:209`) | **Lock Guard and SIM Guard are not implemented at all** — they are the subject of the still-unanswered Q-I-1…Q-I-6 and Q-J-1…Q-J-6; "Email delivery" does not exist |

Why this is a defect rather than a placeholder: protocol `:753` bars labelling a feature secure/protected/
verified without justification, §18 asks "Does the UI falsely imply functionality exists?", and §31 bars
any UI that asserts a state nothing proves. The free-tier sentence is the sharpest case: it names two
guards as shipping products while the dashboard for those same guards honestly reads "Not monitoring yet"
(`:230`) — the app contradicts itself on two tabs of the same build.

**Not a decision I may make.** Whether the tab is removed, relabelled, or built is a product-claim and
revenue decision: §7 names it as a pause point, and protocol §38 kept billing out of these batches. Also
unresolved and already open: the four-app Play listing carries the same vocabulary (see §5).

---

## 4. Finding F-K2 / F-K3 — lifecycle mechanisms that nothing calls

### 4.1 Retention is never executed (F-K2, MEDIUM, privacy)

**REPO.** `storage/EvidenceRetentionPolicy.kt` is correct and complete as a policy object:

```kotlin
const val QUARANTINE_RETENTION_DAYS: Long = 30L      // :50
const val EVIDENCE_RETENTION_DAYS: Long = 30L        // :51
const val EVENT_METADATA_RETENTION_DAYS: Long = 90L  // :52
val default: EvidenceRetentionPolicy = ...           // :57  "The approved §5.4 policy, amended by A-1."
```

Those numbers match the approved table in `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:361-363`
(evidence 30 / metadata 90 / quarantine 30, quarantine unified by amendment A-1 at `:620`). So the
*windows* are right.

What is missing is any user of them. Three independent greps at `f844ffb`:

- `git grep -n "EvidenceRetentionPolicy\|isExpiredAt\|RetentionDays" -- 'app/src/main/kotlin'` outside the
  policy file → **empty**.
- `git grep -n "Retention" -- 'app/src/main/kotlin'` → only the Settings row machinery
  (`presentation/settings/SettingsContent.kt:130-133`, `presentation/model/PresentationModels.kt:112`,
  `design/icons/ShieldraIcons.kt:46`).
- `git grep -nE "\.delete\(\|deleteFile\|deleteWhere\|DELETE FROM\|deleteAll\|\.deleteRecursively" -- 'app/src/main/kotlin'` →
  **zero matches**. `git grep -nE "@Delete\|@Query(\"DELETE\|deleteEvent\|deleteOlder"` → **zero matches**.

So no code path in the application deletes an evidence file, a quarantined file, or an expired event row,
ever. `FactoryReset.kt` does delete — but it is the §5.7 test vehicle, reached only from `androidTest`.

Consequence in user terms: the app's own promise that evidence disappears after 30 days is currently a
design document, not a behaviour. Nothing has been disclosed falsely (see §4.2), but nothing is true
either, and this is precisely the kind of gap a security sign-off cannot pass over.

### 4.2 Settings is honest but imprecise (F-K2b, LOW)

**REPO.** `presentation/settings/SettingsContent.kt:127-134` renders the Retention row with subtitle
`settings_retention_pending` = **"Policy pending"** (`res/values/strings.xml:221`,
`values-ar/strings.xml:221` "السياسة قيد التحديد"). The policy is **not** pending — it is approved,
recorded as A-1, and implemented as a pure object; what is missing is execution. The row also carries
`requiresAuth = true` for an action that performs nothing. Understating is the safe direction, so this is
a wording finding, not a §31 violation.

### 4.3 The export gate is bypassable by construction (F-K3, MEDIUM)

**REPO.** `storage/EvidenceExportPolicy.kt` encodes §5.5 faithfully — `enabled: Boolean = false`, and

```kotlin
val isAllowed: Boolean
    get() = enabled && satisfiedGates.containsAll(ExportGate.entries)   // :20-21
```

with the six mandatory gates enumerated at `:30-36`. Today `isAllowed` is `false` for every instance,
which is right. But `git grep -n "EvidenceExportPolicy" -- 'app/src'` shows consumers **only in
`app/src/test/kotlin/.../EvidenceExportPolicyTest.kt`**. The runtime reason export does not happen is
`navigation/ShieldraNavHost.kt:357 onExport = { defer() }`.

So export is disabled by an accident of wiring rather than by the policy that exists to make it
impossible to skip. The user-visible behaviour is currently honest — `defer()` shows
"Phase 3 deferred" (`:93-94`), and the Event Detail screen's Delete/Export buttons
(`presentation/screen/eventdetail/EventDetailScreen.kt:196-218`) therefore never pretend to have worked.
The finding is forward-looking: whoever builds export can do it without ever consulting
`EvidenceExportPolicy`, and no static gate would notice. **No owner decision is required** — this is
normal engineering — but it must be recorded so Batch L does not inherit it as "export is protected".

---

## 5. Store-listing and claims state (inherited, now worse)

The "zero permissions" promise in the Play description was already superseded by amendment A-6
(`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:626`, restated at `:731` as a *relative* promise)
and is tracked as an open listing item. This audit adds one fact to it: the build now declares **three**
permissions, not one — `POST_NOTIFICATIONS` plus the two foreground-service permissions added with the
Motion service. The honest sentence remains "no permissions until you need them" (only
`POST_NOTIFICATIONS` is ever asked in a runtime prompt), but any listing that reads "zero permissions" is
now further from the truth than when A-6 recorded it, and any listing that reads "anti-theft protection"
must be reconciled with the measured `RMX3910` background starvation evidence
(`docs/reports/batch-G4A-background-sensor-measurement.md`) and with §1 of the Lock feasibility report,
which shows a Lock guard cannot promise it will notice anything.

**NOT RUN:** the Play Console listing text itself, Data-safety form and Content rating answers for the
four launch packages. These are console-side, not repository-side, and nobody has opened them for this
audit.

---

## 6. Finding F-K4 — the current Play target-API requirement already blocks submission (HIGH)

**SOURCE**, from `https://developer.android.com/google/play/requirements/target-sdk`, fetched
2026-10-02, tag-stripped and searched locally; the quoted text is at line 3552-3557 of the stripped
file (4382 lines):

> "When you upload an APK, it must meet Google Play's target API level requirements.
> **Starting August 31 2026:**
> New apps and app updates must target **Android 16 (API level 36)** or higher to be submitted to Google
> Play; except for Wear OS and Android Automotive OS apps, which must target Android 15 (API level 35) or
> higher, and Android TV and Android XR apps, which must target Android 14 (API level 34) or higher."

Today is 2026-10-02, so **this is already in force** — it is not a future deadline. A new submission is
rejected, not warned; the same text allows an extension request to **November 1 2026** "later this year",
which is a Console action, not a code fix. The build reads
`app/build.gradle.kts:9 compileSdk = 35` and `:14 targetSdk = 35`.

The page's second paragraph also matters for the launch plan: *"Existing apps must target Android 15
(API level 35) or higher to remain available to new users"* — so 35 is fine for an already-live app but
not for the four launch packages, which are new.

**Method note, because §25 demands it.** My first fetch of the Play-side equivalent page
(`https://support.google.com/googleplay/android-developer/answer/11926468`) returned a 6397-line
stripped file whose "target" hits were all CSS and which contains **zero** occurrences of the phrase
"API level" — i.e. the article body never arrived. It was discarded and **not** used for any claim here;
the quote above is the positive control: the developer.android.com file contains the real prose at
:3552+. Anyone re-checking should re-run the same two commands and compare line counts.

**Why this is a decision, not a fix.** Raising `targetSdk` 35→36 changes runtime behaviour on Android 16
(egde-to-edge is already handled, but Android 16 also touches foreground-service/media/sensor policy —
unmeasured here), and the DO-NOT-TOUCH list freezes the FGS type, FGS permissions and the notification
policy. That is exactly the "platform-capability" pause point §7 names, so it is presented as Q-K-3
rather than silently bumped in a Batch-K commit.

---

## 7. DEVICE HALF: NOT RUN

`adb devices -l` returned `IR7X5DJ7KZGEHMS8  offline`, then an empty list after `adb kill-server` +
`adb start-server` + `adb reconnect offline` + six 5-second retries. No install, no instrumented run, no
screenshot and no `dumpsys` probe was performed in this batch, so:

- The full instrumented suite was **not** re-run on `f844ffb`. The APK pair *is* code-identical to the last
  device-verified build: `git diff --name-only 18cc745..f844ffb` contains **0 non-docs files**
  (verified), and `18cc745`'s run `36939853591` was the last one to publish APKs, whose pair this
  report's author downloaded to `%TEMP%/k-apks` (`debug/app-debug.apk` + `androidTest/debug/*.apk`)
  intending to install it. That install never happened.
- **NOT RUN:** granted-permission state on the phone, the real Keystore alias list after a production
  run, the contents of `noBackupFilesDir` for the shipped app, whether the DB file is encrypted at rest
  on a real install (as opposed to under instrumentation), the Motion ongoing-notification wording on
  this build, and **screenshots of the Premium tab and Event Detail** — which is what would have made
  F-K1's user-visible severity concrete.
- Everything in §2 labelled REPO is a source reading, not a device measurement. The strongest device
  evidence for the crypto layer still comes from the earlier batches' instrumented runs, and per
  `docs/reports/batch-J-lock-feasibility.md` §12 a green CI does not convert a reading into a measurement.

The device half must be re-run before any sign-off sentence is written. Sequence already agreed in the
G5 directive (10 steps on RMX3910) applies unchanged.

---

## 8. Owner decisions this audit needs (no invented policy)

| id | Question | Why it cannot be answered here |
|---|---|---|
| **Q-K-1** | What happens to the Premium tab before launch: **remove the route+tab**, keep it with copy that states nothing is purchasable yet, or build real billing (§30 currently forbids the SDK)? | Product/revenue + §30/§31 claim decision. F-K1 lists the exact seven strings involved |
| **Q-K-2** | Is automatic expiry **in scope for launch**? If yes, what triggers the sweep, given A-10 (no boot receiver), the G5 "no polling/no timer/no WorkManager" ruling, and the fact that a dark screen starves the process on this phone? If no, the approved retention table and the Settings "Policy pending" row must both be re-worded to "deletion is not performed by this build" | Decides whether F-K2 is a launch blocker or a documented limit. The only trigger that already exists is process start (that is how rotation works) |
| **Q-K-3** | Raise `compileSdk`/`targetSdk` to 36 now as a Batch-L slice, or request the Console extension to 2026-11-01? | F-K4. Touches the frozen FGS/notification policy area, so it needs your explicit go and a device re-verification afterwards |
| **Q-K-4** | Confirm the listing vocabulary after A-6 and after this batch: 3 declared permissions, 1 prompted, and no claim of continuous protection or "zero permissions" | Play one-way door; needs the same care as the package ids |

F-K3 (export policy has no consumer) is **not** a question: the fix is to make a future export slice
consult `EvidenceExportPolicy`, or to add a static gate forbidding an export path that bypasses it. It is
scheduled as ordinary work, and recorded here so it is not inherited as "protected by policy".

---

## 9. What would let this batch close

1. The device half (§7) run end-to-end on RMX3910 with a screenshot per runtime screen, including the
   Premium tab and Event Detail.
2. Answers to Q-K-1…Q-K-4 — and the still-open **H-1…H-12**, **Q-I-1…Q-I-6**, **Q-J-1…Q-J-6**, because an
   unanswered policy question is an open finding, not a passed gate.
3. A decision on whether the three stale-wording Q-G4 findings and Q-F1 are closed or deferred.
4. Only then: the G6 sign-off sentence, and Batch L (release hardening: signing, `versionName 0.1.0` →
   real versioning, minify/resource shrinking, Play Console declarations).

Until those exist the status of G6 stays **APPROVED PROCESS / FINAL AUDIT PENDING**
(`docs/ROADMAP.md:151`) and nothing in this report may be quoted as "audited" or "verified".

---

## 10. Reproduction

```sh
cd "C:/Users/Administrator/Documents/Default Project/shieldra"
# §2 secrets / permissions / network / logging
git ls-files | grep -iE "\.jks$|\.keystore$|local.properties|\.env$|credentials|secrets|\.p12$"
git grep -niE "api[_-]?key|secret|AIza[0-9A-Za-z_-]{20}|-----BEGIN" -- '*.kt' '*.kts' '*.xml' '*.json' '*.properties' ':!docs'
git grep -nE "okhttp|retrofit|HttpURL|firebase|admob|WorkManager|AlarmManager" -- 'app/src'
git grep -cnE "Log\.[diveiw]|println\(|printStackTrace" -- 'app/src/main/kotlin'
# §4 the lifecycle gap (all three must return nothing outside their own file/test)
git grep -n "EvidenceRetentionPolicy\|EvidenceExportPolicy" -- 'app/src/main/kotlin'
git grep -nE "\.delete\(|@Delete|DELETE FROM|deleteRecursively" -- 'app/src/main/kotlin'
# §6 the official quote
curl -sL "https://developer.android.com/google/play/requirements/target-sdk" -o tsdk.html
sed -e 's/></>\n</g' tsdk.html | sed -e 's/<[^>]*>//g' | grep -v '^[[:space:]]*$' \
  | sed 's/&nbsp;/ /g; s/&amp;/\&/g; s/&#39;/'"'"'/g; s/&quot;/"/g' | awk 'length($0)<300' > tsdk.txt
grep -n "Starting August 31 2026\|API level 36" tsdk.txt      # lines 3555+
# §7 code identity of the un-installed APK pair
git diff --name-only 18cc745..HEAD | grep -vc '^docs/'        # 0
```

Repository read at `f844ffb35dc798cdb1bf21263ecf61cab1b00d8b`. The `%TEMP%` working copies
(`k-apks/`, `tsdk.html`, `tsdk.txt`, `target.html` — the discarded navigation-only fetch, kept until this
batch closes as proof of the §6 method note) are **not** committed to this repository.
