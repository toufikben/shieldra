# Batch K — Security / Privacy / Device audit (protocol §38: the point for security sign-off)

**Status: STATIC HALF DELIVERED. DEVICE HALF MEASURED TWICE — the storage, permission, service,
notification-channel and battery-exemption probes ran on the RMX3910 and were re-run to confirm them (§7), and
the UI screenshot pass ran twice: once on the build that predates the owner's answers
(`batch-K-owner-answers.md` §7) and once on the build that carries them (§7.2 there). NO SIGN-OFF IS GIVEN.**

**A second, non-device blocker appeared while this report was being verified, and has since been worked
around:** the GitHub Actions artifact-storage quota is exhausted, so CI cannot upload the APK pair (§11,
finding **F-K5**). The owner chose an alternate delivery path the same day: the APK pair now publishes to the
required draft release `device-apks` (§11.1, and `batch-K-owner-answers.md` §6.5) with nothing deleted. That
unblocked the screenshot pass, which ran; what it did **not** fix is signing — each runner uses its own debug
keystore, so a newer pair cannot be installed over an existing one, and that is now the device half's blocker
(`batch-K-owner-answers.md` §7.3, **Q-K-6**).

This report deliberately does **not** say "G6 PASS" anywhere. §24-§25's rule — cite the exact official
source, never an old summary — is applied to the release-policy finding in §6, and every repository
claim below was re-opened in the working tree at `f844ffb35dc798cdb1bf21263ecf61cab1b00d8b`, not
inherited from an earlier batch report.

Labels: **SOURCE** (official page, quoted with the line in the stripped text), **REPO** (read in this
repository at `f844ffb`), **DEVICE** (measured on the owner's RMX3910 `IR7X5DJ7KZGEHMS8` — see §7),
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
static reason; backups are off and every backup domain is excluded; **evidence files** live under
`noBackupFilesDir` behind a canonical-path `require`, while the **Room database sits in the ordinary
`databases/` directory as plaintext SQLite** — it is protected from extraction by `allowBackup="false"`
plus the backup and device-transfer exclusions, **not** by encryption, and no UI text claims otherwise
(§2, §7); the crypto layer can only be built as AES-256-GCM with a namespaced Keystore alias,
deterministic length-prefixed AAD, no software-key fallback, and a runtime StrongBox probe that fails
closed; key rotation really does run on every approved process start.

**What the audit found, and where each finding stands now.** The five findings were written against
`f844ffb`; the owner answered four of them the same day, so each line below keeps the original finding and
adds the disposition that is true at `902f15c`. Full execution record:
`docs/reports/batch-K-owner-answers.md`.

1. The **Premium tab was a live, false advertisement** (§3). It showed a price, a "Buy Lifetime" button and
   five feature claims, of which **none** existed in the code — including a free-tier sentence that named
   Lock Guard, SIM Guard and Email delivery. → **Closed by Q-K-1 / A-20** (`d0573bf`): route, tab and the
   seven strings are gone, a test fails if any declared route names a premium screen, and the phone now
   photographs a three-item tab bar (owner-answers §7.2).
2. **Retention was decided, coded, tested — and never executed** (§4). No production file read the retention
   policy, and `app/src/main/kotlin` contained **zero delete calls of any kind**. → **Closed by Q-K-2 / A-21**
   (`d34ed86`): the windows now drive `EvidenceRetentionSweeper` at three authorized points, and two device
   tests removed real ciphertext from `no_backup/` (owner-answers §7.3).
3. **The export gate had no consumer** (§4.3). Export did not happen because a button no-oped, not because
   the six-gate policy was consulted. → **Closed** (`6ab41f2` + the fixture proof in §4.3): a static gate now
   forbids an export path that bypasses `EvidenceExportPolicy`.
4. **Play would reject the build** (§6). The current official target-API requirement has been in force since
   2026-08-31 and the build was `targetSdk = 35`. → **Closed by Q-K-3 / A-22** (`8da7686`): compile and target
   are 36, and the resulting build installs and runs on this Android 16 / API 36 phone (owner-answers §7.2),
   after which §3's item list was probed one item at a time (§7.4): edge-to-edge by bounds, back navigation by
   two measured pops, the FGS type read live as `specialUse`, the permission set re-read as 3 declared /
   1 runtime. Landscape stayed **NOT RUN** and the 16 KB page item is untestable on a 4096-byte-page phone.
5. The device half of the audit was **only partly measured** (§7): the probes ran but the screenshot pass did
   not, because the keyguard is secure and no agent may guess the PIN. → **Measured twice** (owner-answers §7
   on the pre-answers build, §7.2 on the build that carries the four answers above), and the phone is locked
   again as of `17:34` (`mInputRestricted=true`). A sign-off still cannot be written from a reading of source
   alone — but the readings are no longer the only evidence.
6. **CI could no longer publish the APK pair** (§11.1, F-K5). The GitHub Actions artifact-storage quota was
   exhausted — and it already was at the **first** commit of this report: all four of the batch's runs
   finished **success** with `332 tests, 332 passed`, and **all four** also logged three
   `Failed to CreateArtifact: Artifact storage quota has been hit` errors each, so not one of them left an
   `apks-*`, `unit-test-results-*` or `group-a-evidence-*` artifact behind. → **Worked around by Q-K-5 / the
   owner's fourth way** (§11.1): the pair now publishes to a required draft release, and the `902f15c` pair
   was downloaded and hash-verified. The quota itself is untouched and F-K5's warning stays true.
7. **New, from the second screenshot pass: F-K7** — the dashboard's Network tile reads "Offline" for a state
   nothing measures (owner-answers §7.2). The projection layer passes an honest "unknown" label and the strip
   discards it. Display-only, scheduled under Q-K-4, recorded rather than silently patched because it is a
   release-facing claim a Play reviewer can photograph.
8. **New, and it is what the device half now waits on: Q-K-6.** Each CI runner signs with its own debug
   keystore, so a newer APK pair cannot be installed over the install that carries this batch's device
   evidence; running HEAD's version of the sweep test means uninstalling the app and losing the permission
   grant, the database, the channels and the Keystore aliases (owner-answers §7.3).

9. **New, from the cold-start probe: F-K8 → Q-K-7.** `am force-stop` + a launcher tap puts the **onboarding
   welcome page** on the screen again, on a phone that already holds a persisted Security Event —
   `ShieldraNavHost.kt:81` hardcodes `startDestination = ONBOARDING_WELCOME` and no persisted "onboarding
   completed" value exists anywhere in `app/src/main`. It is not a false claim (the page is true each time it
   is seen), which is why it is a question and not a fix: owner-answers §7.4.

10. **New, from the control pass: F-K9** — Settings says **"App lock — Require authentication"** while the
    storage policy deliberately ships `requireUserAuthentication = false` (`EvidenceKeyPolicy.kt:71-77`, with
    the reason written down), and the row's click handler discards the flag (`ShieldraNavHost.kt:296`) and
    defers. Pressing it on the phone asked for no authentication of any kind (owner-answers §7.5 row 10). The
    screen and the key policy disagree in writing, in a sentence a Play reviewer can photograph, so it joins
    **F-K6/F-K7** under Q-K-4 rather than being patched per screenshot.
11. **New, from the same pass: F-K10** — the only History row is labelled **"Skipped"** while its own
    notification sits in the shade (`NotificationRecord … tag=8c75b077-…`, same minute, same phone). The
    label is `PersistedEventReader.kt:109` reporting an internal pipeline stage (`INITIATED`/`COLLECTING`)
    for an event the Panic path never hands to the pipeline; the filter chips offer `Delivered/Deferred/Failed`
    and so cannot select the state the only row actually has (owner-answers §7.5 row 5). Partly wording,
    partly behaviour — only the wording half falls under Q-K-4.
12. **New, and open as a question: F-K11 → Q-K-8.** A `Silent Panic` press two hours after the incident it
    belongs to created **no** new row and no new event: `PanicIncidentCoordinator.kt:62-64` binds to the first
    non-terminal `PANIC_ACTIVATION` among the 100 most recent events (`ACTIVE_EVENT_SCAN_LIMIT = 100`, `:153`)
    with **no time window**, and per F-K10 nothing advances that state, so the
    binding is forever (owner-answers §7.5 row 4). That is A-7's dedup working as written; the unbounded
    *duration* is the owner's number to set, not an audit's.

Of the twelve, **1–4 and 6 are closed** and each keeps its original finding text above so the audit trail is
not rewritten into success. What still stands between this batch and a sign-off is the sign-off decision
itself (§8), item 5's remaining device work now that it is limited by Q-K-6, the Q-K-4 wording set — items 7
and 10 plus item 2's "Policy pending" tail and item 11's chip — item 9's first-run decision, item 12's
window, and the unanswered H/I/J questions that decide what may be claimed about camera, location,
SIM and Lock.

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
| What is on the phone, and in which directory | `adb shell run-as com.shieldra.app ls -R` on a shipped debug install | `no_backup/` holds `shieldra-evidence/` (the encrypted evidence files) and `shieldra-key-lifecycle.bin`; the database is at `databases/shieldra.db` (+`-wal`), i.e. **not** inside `no_backup`; `shared_prefs/` holds one key, `permission_requested=true` (DEVICE) |
| Database at rest | `run-as … head -c 16 databases/shieldra.db \| xxd`, then the same on `shieldra.db-wal` | first 16 bytes are `53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00` — **"SQLite format 3"**, i.e. plaintext. The WAL exposes the whole schema and column names (`security_events(event_id, event_type, state, severity, created_at_epoch_ms, …, panic_activation_count, …)`) as readable text. **Event metadata is not encrypted; only evidence payloads are** (no SQLCipher in `app/build.gradle.kts:39-56`, no `SupportFactory` anywhere in `app/src/main`). Mitigation is the backup surface, not crypto — and no UI string claims encryption (`grep -i encrypt res/values/strings.xml` → no match) (DEVICE + REPO) |
| Lifecycle record carries no key material | `run-as … head -c 96 no_backup/shieldra-key-lifecycle.bin` | plaintext properties: `shieldra-key-lifecycle/1`, `active=v1`, `retired=`, `lastRotationEpochMs=…`, `createdEpochMs=…` — a version label and two timestamps, no key bytes, no alias secret (DEVICE) |
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

**CLOSED 2026-10-02, statically.** `g4b-review-check.sh` now enforces §5.5 over the whole production tree
instead of only the storage package: no evidence-egress primitive (`ACTION_SEND`,
`ACTION_CREATE_DOCUMENT`, `EXTRA_STREAM`, `FileProvider`, `ClipboardManager`, SAF `CreateDocument`,
`MediaStore`, an HTTP client or a multipart uploader) may appear anywhere under `app/src/main`, and every
line that wires the export affordance must stay deferred, forward an existing callback, or name
`EvidenceExportPolicy` in the same file. The gate was proven against a fixture, not assumed. With a
temporary presentation-package file that shared evidence through `FileProvider` + `Intent.ACTION_SEND`,
**the old scan printed `PASS: evidence export is disabled by default with no hidden bypass (§5.5)`** — the
finding reproduced — while the new one fails with `FAIL: F-K3: an evidence-egress path exists outside the
export policy:` plus the offending path. A second fixture with a real `onExport = ::exportEventEvidence`
wiring and no egress primitive failed only the wiring rule, at `FAIL: F-K3: an export affordance is wired
without the policy:`. Both fixtures were deleted and the clean tree is green again (52 `PASS:` lines, exit 0). No production
behaviour changed — the shipped wiring is still `{ defer() }`, now at `navigation/ShieldraNavHost.kt:345` —
so nothing in this closure is owed a device pass. A future export slice must still satisfy the six §5.5
gates; what it can no longer do is arrive without the policy being named.

---

## 5. Store-listing and claims state (inherited, now worse)

The "zero permissions" promise in the Play description was already superseded by amendment A-6
(`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:626`, restated at `:731` as a *relative* promise)
and is tracked as an open listing item. This audit adds one fact to it, measured on the phone (§7): the
installed build declares **three** permissions, not one — `POST_NOTIFICATIONS` plus the two
foreground-service permissions added with the Motion service — and `dumpsys package` additionally shows
the AGP-injected signature-level `com.shieldra.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which is
not a permission the app asks the user for but *is* visible in the declared list, so a listing that counts
permissions should count the three. The honest sentence remains "no permissions until you need them" (only
`POST_NOTIFICATIONS` is ever asked in a runtime prompt, and on this device it carries `granted=true,
flags=[USER_SET|…]`, i.e. a real decision was made), but any listing that reads "zero permissions" is
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

## 7. DEVICE HALF: measured on `IR7X5DJ7KZGEHMS8` — probes here, device passes in §7–§7.5 of the answers file

Everything below was measured on the install that was on the phone when this report was written
(`apks-36939853591`, commit `18cc745`, `targetSdk=35`). The screenshot pass this section recorded as NOT RUN
has since run — twice, once on this install and once on a `targetSdk=36` build — the retention sweep has
run on the device, §3's Android 16 item list was probed one item at a time, and every reachable control on
the shipped build was pressed and read once more; those records are
`docs/reports/batch-K-owner-answers.md` §7, §7.2, §7.3, §7.4 and §7.5, and §7.2 also
re-ran the probes below against the newer install.

The phone came back on `adb` mid-batch (`IR7X5DJ7KZGEHMS8 device` after having been `offline`), so the
storage, permission, service and exemption probes below ran for real. The screenshot pass did not, at the time
this section was written: the keyguard was **secure** (`dumpsys window` → `mInputRestricted=true`,
`deviceHasKeyguard=true`), so no agent-side swipe could reach the UI. Nobody guessed a PIN and no bypass was
attempted; the owner unlocked it later the same day.

**Re-run note (method correction).** After this report was committed, every §7 device probe was run a
second time on the same install (`dumpsys package` permissions, `run-as ls -R`, the `head -c 16` database
header, the lifecycle record, the SharedPrefs file, `dumpsys activity services`, `dumpsys deviceidle
whitelist`, `dumpsys notification`). All readings reproduced except one: the "live notification" probe
recorded below as `grep -ic shieldra` on the notification dump returned **0** the first time and **15**
the second. That command counts *any* line naming the package — channel definitions, `AppSettings`
entries, the ranking log — not posted notifications, so its first reading was not a safe measurement. The
probe has been replaced with one that names the object it claims to count
(`grep -c "NotificationRecord.*com.shieldra"`) and that now returns **0**; the conclusion ("no ongoing
notification is left behind while monitoring is stopped") is restated against the corrected probe, and the
discrepancy itself is kept here as the method note §25 asks for.

**Built and installed:** the pair from run `36939853591` (`debug/app-debug.apk` +
`androidTest/debug/app-debug-androidTest.apk`) installed with `adb install -r -t` and again with `-g`,
both `Success`. It is code-identical to this batch's HEAD:
`git diff --name-only 18cc745..dd7cb1b` contains **0 non-docs files**. Docs-only CI runs publish no APK
artifact (`gh api …/actions/artifacts` lists `apks-36939853591` as the newest), which is why that pair is
the one on the phone.

**MEASURED (DEVICE):**

| Probe | Reading |
|---|---|
| Declared vs granted permissions | `dumpsys package com.shieldra.app`: requested = `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` + the AGP-injected signature-level `com.shieldra.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; install-granted = the two FGS ones + that auto one; runtime = `android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET\|… ]`. **`USER_SET` is the proof the runtime prompt really reached the user**, and the count is 3 declared / 1 runtime — the number §5 and Q-K-4 are about |
| Evidence + database layout | `run-as com.shieldra.app ls -R` → `no_backup/shieldra-evidence/` (empty at this moment), `no_backup/shieldra-key-lifecycle.bin`, `databases/shieldra.db`+`-shm`+`-wal`, `shared_prefs/shieldra_notification.xml`, plus `files/profileInstalled`. The re-run additionally listed four `cache/persisted-reading-<uuid>.db.lck`, one `cache/shieldra.db.lck`, and an empty `app_dxmaker_cache/`. The `persisted-reading-` name is generated only in `app/src/androidTest/…/PersistedEventReadingInstrumentedTest.kt:144` (throwaway test databases; `git grep -n "persisted-reading-" app/src` matches no `main/` source), so that residue belongs to the aborted instrumented pass recorded below, not to the production storage path. `app_dxmaker_cache` appears nowhere in the repository and is not attributed by this audit. `.db.lck` files carry no data |
| Database encrypted at rest? | **No.** `head -c 16 databases/shieldra.db` = `SQLite format 3\0`; the WAL yields the full plaintext schema. Only evidence payloads are encrypted (§2 row) |
| Lifecycle record contents | `active=v1`, `retired=`, `lastRotationEpochMs=…`, `createdEpochMs=…` — no key material |
| SharedPrefs contents | one boolean, `permission_requested=true` — no event data, no identifier |
| Anything running after force-stop | `dumpsys activity services com.shieldra.app` → **no `ServiceRecord`**; `ps -A \| grep shieldra` → nothing. The app does not resurrect itself |
| Battery exemption claimed? | `dumpsys deviceidle whitelist \| grep -ci shieldra` → **0**. The same dump lists other packages (`system-excidle,com.android.vending,…`), so 0 is "not in the list", not "the command failed". The build asks for no Doze/battery whitelist entry, so no listing may imply it survives everything |
| Notification currently posted? | `dumpsys notification --noredact \| grep -c "NotificationRecord.*com.shieldra"` → **0** (the Notification List at that moment held only `com.android.systemui`, `com.masarat.salati`, `com.google.android.googlequicksearchbox`, `com.oplus.battery`, `android`). With monitoring stopped there is **no** ongoing "SHIELDRA protection is active" notice left behind — the corrected probe for this claim, see the re-run note above |
| Notification channels that exist on the phone | Same dump, `AppSettings: com.shieldra.app (10859) importance=DEFAULT userSet=true` and two channels: `shieldra_security_events` (name "Security events", **`mImportance=4`**, sound, `mShowBanner=true`) and `shieldra_motion_monitoring` (name "Motion monitoring", **`mImportance=2`**, `mShowBanner=false`, `mVibrationEnabled=false`). The ongoing-notice channel is measured **low-importance/silent**, and the event channel is the audible one — the two are not the same channel, which is the device side of the A-9/A-6 notification policy |

**NOT RUN (and why):**

- ~~**Screenshots of every runtime screen, including the Premium tab and Event Detail**~~ — **RUN, twice.**
  Once on this install (`batch-K-owner-answers.md` §7, which is where F-K1's severity became visible rather
  than argued: the Premium screen photographed with its price and its five claims) and once on the
  `targetSdk=36` build that carries the answers (§7.2, 38 screens, where the tab is gone and the Event Detail
  and its event-ID-tagged notification are read together).
- **The Keystore alias list after a real production run.** Not enumerable here: `dumpsys keystore` answers
  `Can't find service: keystore`, and `/data/misc/keystore` needs root. Alias existence on this device
  remains proven the way it was before — by the instrumented encrypt/decrypt round-trips of earlier
  batches, not by a listing.
- **The full instrumented suite on this build.** One pass was started and **aborted by me**
  (`am force-stop`, which ends it as `INSTRUMENTATION_RESULT: shortMsg=Process crashed` — an abort
  record, not a test failure). Progress before the abort: `PersistedEventReadingInstrumentedTest` 5/5
  dots, then `MotionBackgroundMeasurementInstrumentedTest` began; that measurement vehicle holds the run
  for many minutes per window, which is why device passes of the suite have excluded it before
  (`68 vs 72 tests`). No `OK (n tests)` line was produced, so this batch claims **no** suite count on
  this build; CI's 332 JVM tests are the only executed-test evidence here, and they are JVM-only.
  **Since then one class has run on the newer install** — `EvidenceRetentionSweepInstrumentedTest`,
  `Tests run: 3, Failures: 1` twice, with the failure being the stale expectation HEAD already fixed
  (owner-answers §7.3). A whole-suite count on a `targetSdk=36` build is still NOT RUN.
- **`pm clear` / `pm grant` are refused on this phone** (`SecurityException: … does not have permission
  android.permission.CLEAR_APP_USER_DATA`, and `Neither user 2000 nor current process has
  GRANT_RUNTIME_PERMISSIONS`). Consequence for the audit: a *fresh-install* state cannot be produced from
  `adb` on the RMX3910, so "first launch with storage absent" is not device-reachable here; the
  instrumented tests that assert it run inside the test harness instead.
- **The Motion ongoing-notification wording on this exact build** — needs the UI pass (it was verified on
  the G5 device pass, on code identical to this build, and is not re-claimed here).

Everything in §2 labelled REPO is a source reading, not a measurement; per
`docs/reports/batch-J-lock-feasibility.md` §12 a green CI does not convert a reading into a measurement.
**What the device half now waits on is not the keyguard but the keystore:** HEAD's APK pair cannot be
installed over the evidence-bearing install (owner-answers §7.3), which is **Q-K-6** below. The phone is
locked again (`mInputRestricted=true` at `17:34`), and no agent may guess the PIN. The 10-step sequence
agreed in the G5 directive applies unchanged once it is unlocked.

---

## 8. Owner decisions this audit needs (no invented policy)

**Q-K-1 … Q-K-5 were answered on 2026-10-02** as amendments A-20 … A-24 and executed; the rows below are kept
verbatim as the questions that were asked, with their answers in
`docs/reports/batch-K-owner-answers.md` §1. **Q-K-6, Q-K-7 and Q-K-8 are new and open.**

| id | Question | Why it cannot be answered here |
|---|---|---|
| **Q-K-1** | What happens to the Premium tab before launch: **remove the route+tab**, keep it with copy that states nothing is purchasable yet, or build real billing (§30 currently forbids the SDK)? | Product/revenue + §30/§31 claim decision. F-K1 lists the exact seven strings involved |
| **Q-K-2** | Is automatic expiry **in scope for launch**? If yes, what triggers the sweep, given A-10 (no boot receiver), the G5 "no polling/no timer/no WorkManager" ruling, and the fact that a dark screen starves the process on this phone? If no, the approved retention table and the Settings "Policy pending" row must both be re-worded to "deletion is not performed by this build" | Decides whether F-K2 is a launch blocker or a documented limit. The only trigger that already exists is process start (that is how rotation works) |
| **Q-K-3** | Raise `compileSdk`/`targetSdk` to 36 now as a Batch-L slice, or request the Console extension to 2026-11-01? | F-K4. Touches the frozen FGS/notification policy area, so it needs your explicit go and a device re-verification afterwards |
| **Q-K-4** | Confirm the listing vocabulary after A-6 and after this batch: 3 declared permissions (plus the AGP-injected signature-level one, §5), 1 prompted, and no claim of continuous protection or "zero permissions" | Play one-way door; needs the same care as the package ids. §7 now supplies the measured side of it: `POST_NOTIFICATIONS: granted=true, flags=[USER_SET|…]`, **no** `dumpsys deviceidle whitelist` entry, and no service or notification surviving a force-stop — so "runs 24/7 in the background, exempt from battery optimisation" is unsupportable in three separate ways. **Correction from the second install:** on the `targetSdk=36` build the same probe reads `granted=true` with **no** `USER_SET` flag, so the "the prompt really reached a user" proof rests on the `18cc745` install alone; the newer install proves only that the request path ran (`shared_prefs/shieldra_notification.xml`, `permission_requested=true`, mtime inside the pass). Use the conservative sentence (owner-answers §7.2). **Four in-app labels are now part of the same decision:** **F-K6** "Policy pending" (understates what the build does), **F-K7** the unmeasured "Offline", **F-K9** "App lock — Require authentication" above a key policy that ships `requireUserAuthentication = false`, and **F-K10** the "Skipped" chip on an event whose notification is in the shade; the fixed-form age string (`event_age_hours`, both locales) rides along as the minor one (owner-answers §7.5) |
| **Q-K-5** | CI artifact storage is exhausted (§11.1: 301 artifacts ≈ 1,215 MiB, of which 69 `apks-*` ≈ 1,211 MiB). Choose one: **delete old `apks-*` artifacts** (irreversible, and some are cited as evidence by earlier batch reports), **add `retention-days` to the three `upload-artifact` steps** (edits the CI/CD pipeline), **raise the plan's storage**, or **accept that future code commits cannot be device-verified** | A new platform-capability decision under §7. It does not block this batch's screenshot pass (the needed pair is already on the phone), it blocks Batch L and every rebuilt-APK pass afterwards |
| **Q-K-6** (new) | Running HEAD's instrumented tests on this phone requires **uninstalling the app**, because each CI runner signs with its own debug keystore and `adb install -r -t` is refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on `com.shieldra.app.test`. Choose: **accept the loss** (the install carrying today's permission grant, `shieldra.db`, the two channels, the Keystore aliases and the lifecycle record goes away, and `pm clear` is refused so it cannot even be reproduced cleanly), **pin one signing keystore for the device pair** (a CI/CD change, and it puts signing material somewhere the §2 secret scan has never looked), or **leave HEAD's sweep test unrun on device** and treat §7.3's two passing deletion tests plus the measured third as enough | Only you can trade audit evidence for a test count. Nothing was uninstalled to find this out |
| **Q-K-7** (new) | **Should onboarding ever be seen twice?** After `am force-stop` + a launcher tap the first screen is the welcome page again, on a phone that already holds a persisted Security Event: `ShieldraNavHost.kt:81` hardcodes `startDestination = ONBOARDING_WELCOME` and no persisted "onboarding completed" flag exists in `app/src/main` (the only `getSharedPreferences` is `LocalEventNotifier.kt:35`, the permission state). `HOME` + a launcher tap, by contrast, restored the scrolled dashboard exactly — so the rule today is "every process death makes the user a new user", and G4-A measured that process death is this phone's normal case. Choose: **persist completion**, **start on the dashboard whenever a database already exists**, or **accept it** | It is not a false claim — the welcome copy is true each time it is seen — so this is a first-run product decision, not one of the four allowed truthfulness fixes, and the G5 directive's frozen list forbids patching it on an audit's own authority (owner-answers §7.4) |
| **Q-K-8** (new) | **How long does a Panic incident stay *the* incident?** `PanicIncidentCoordinator.kt:62-64` binds every press to the first non-terminal `PANIC_ACTIVATION` row among the 100 most recent events (`ACTIVE_EVENT_SCAN_LIMIT = 100`) with **no time window**, and because the Panic path posts through `LocalEventNotifier` without entering `InMemoryEventPipeline`, that row never reaches a terminal state. Measured: a `Silent Panic` press ~2 h after the event it joined created no new row and no new event, and counted as another activation of the two-hour-old incident (owner-answers §7.5 row 4). Choose: **bind for N minutes then start a new incident** (N is yours), **bind until the event is deleted or swept**, or **accept forever-binding** | A-7 authorized the dedup design and the durable activation count; it did not bound a duration, and an audit may not pick the number. This is behaviour, not wording, so it cannot ride in on Q-K-4 |

F-K3 (export policy has no consumer) is **not** a question: the fix is to make a future export slice
consult `EvidenceExportPolicy`, or to add a static gate forbidding an export path that bypasses it. It is
scheduled as ordinary work, and recorded here so it is not inherited as "protected by policy".
**Done 2026-10-02** — the second branch, the static gate, with the fixture proof in §4.3.

---

## 9. What would let this batch close

1. ~~The **screenshot pass** of §7~~ — **done twice** (owner-answers §7 and §7.2), including Event Detail and
   the Premium screen before and after its removal, ~~§3's Android 16 item list probed item by item~~
   (owner-answers **§7.4**), and ~~every reachable control pressed and read~~ (owner-answers **§7.5**, 14
   rows: cold start, the four tabs, Event Detail's Export and the Delete row left unpressed, the five Settings
   rows, and Motion start/stop with the service and notification reads taken after each). What the device half
   still owes is narrower now: **Q-K-6** (HEAD's instrumented
   tests cannot be installed without losing this evidence), **landscape**, which this phone refused to enter,
   and, if you want it, the full instrumented suite minus the measurement vehicle on a `targetSdk=36` build,
   which would turn "no suite count claimed" into a number.
2. Answers to Q-K-1…Q-K-5 — **delivered 2026-10-02** as A-20…A-24 and executed — the still-open
   **H-1…H-12**, **Q-I-1…Q-I-6**, **Q-J-1…Q-J-6**, and the three questions this batch produced: **Q-K-6**,
   **Q-K-7** and **Q-K-8**. An unanswered policy question is an open finding, not a passed gate.
3. A decision on whether the three stale-wording Q-G4 findings and Q-F1 are closed or deferred, plus the
   wording items this batch produced and now counts **four**: **F-K6** (Settings "Retention policy — Policy
   pending" now understates the build), **F-K7** (the dashboard's unmeasured "Offline", owner-answers §7.2),
   **F-K9** ("App lock — Require authentication" above a key policy that requires no authentication,
   owner-answers §7.5) and **F-K10**'s label half (the "Skipped" chip — its mapping half is behaviour and
   stays with Q-K-8, not with this pass).
4. Only then: the G6 sign-off sentence, and Batch L (release hardening: signing, `versionName 0.1.0` →
   real versioning, minify/resource shrinking, Play Console declarations). Batch L **can** now be
   device-verified for downloads — the `device-apks` release carries a hash-verified pair per run — but not
   for *installs over an existing build*, which is Q-K-6.

Until those exist the status of G6 stays **APPROVED PROCESS / STATIC AUDIT + DEVICE PROBES AND SCREENSHOT
PASSES DELIVERED, NO SIGN-OFF** (`docs/ROADMAP.md` row G6) and nothing in this report may be quoted
as "audited" or "verified".

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
# §7 code identity of the APK pair that IS installed on the phone
git diff --name-only 18cc745..HEAD | grep -vc '^docs/'        # 0
# §11 the CI verdicts, read from the run objects themselves
gh run view 36974410187 --json status,conclusion,headSha
gh run view 36976932429 --json status,conclusion,headSha
gh run view 36976932429 --log | grep -E "CI TEST SUMMARY|BUILD SUCCESSFUL|boundary scan|REVIEW PASS"
# §11.1 the artifact quota
gh api repos/toufikben/shieldra/actions/artifacts --jq '{total_count: .total_count}'
gh api repos/toufikben/shieldra/actions/artifacts --jq '[.artifacts[].size_in_bytes] | add' --paginate
gh api repos/toufikben/shieldra/actions/artifacts \
  --jq '[.artifacts[] | select(.name|startswith("apks-"))] | {n: length, mb: ((map(.size_in_bytes)|add)/1048576|floor)}' --paginate
grep -n "retention-days" .github/workflows/*.yml             # no match
```

The §7 device probes, in the order they were run (`A` = `…/Android/Sdk/platform-tools/adb.exe`, with
`export MSYS_NO_PATHCONV=1` in Git Bash):

```sh
"$A" install -r -t -g <debug/app-debug.apk>       # and the androidTest apk
"$A" shell dumpsys package com.shieldra.app | sed -n '/requested permissions/,/runtime permissions/p'
"$A" shell dumpsys package com.shieldra.app | sed -n '/runtime permissions/,/User-specific/p'
"$A" shell run-as com.shieldra.app ls -R
"$A" shell run-as com.shieldra.app head -c 16 databases/shieldra.db | xxd
"$A" shell run-as com.shieldra.app cat databases/shieldra.db-wal | tr -c '[:print:]' '\n' | grep "CREATE TABLE"
"$A" shell run-as com.shieldra.app head -c 96 no_backup/shieldra-key-lifecycle.bin | xxd
"$A" shell run-as com.shieldra.app cat shared_prefs/shieldra_notification.xml
"$A" shell dumpsys activity services com.shieldra.app
"$A" shell dumpsys notification --noredact | grep -c "NotificationRecord.*com.shieldra"   # posted = 0
"$A" shell dumpsys notification --noredact | grep -in shieldra | cut -c1-90               # channels + logs
"$A" shell dumpsys deviceidle whitelist | grep -ci shieldra
"$A" shell dumpsys window | grep -E "mCurrentFocus|mInputRestricted"   # the keyguard blocker
```

Repository read at `f844ffb35dc798cdb1bf21263ecf61cab1b00d8b`; the §7 measurements were taken with the
install that matches `dd7cb1b0ad5b75961431eb7286704f83ed913a35`. The `%TEMP%` working copies
(`k-apks/`, `kshot/`, `tsdk.html`, `tsdk.txt`, `target.html` — the discarded navigation-only fetch, kept
until this batch closes as proof of the §6 method note) are **not** committed to this repository.

---

## 11. CI record for this batch

Read back from the run object itself (`gh run view 36974410187 --json status,conclusion,headSha`), not
from a watch message. The upload column was counted two ways, and both agree: `gh run view <run-id> | grep -c
"Failed to CreateArtifact"` → **3 for every one of the four runs**, and
`gh api repos/toufikben/shieldra/actions/artifacts` (pages 1–4, `total_count` = 301) → **no artifact whose
name contains any of these four run ids**.

| Commit | Run | Verdict | Quoted output |
|---|---|---|---|
| `dd7cb1b0ad5b75961431eb7286704f83ed913a35` (this report + Q-K-1…Q-K-4 + ROADMAP rows K/G6/G7) | `36974410187` | **success, with three artifact-upload failures** | `CI TEST SUMMARY: 332 tests, 332 passed, 0 failures, 0 errors, 0 skipped` · `BUILD SUCCESSFUL in 7m 46s` · `Group C boundary scan passed.` · `G4-B LOCAL REVIEW PASS — runtime/device evidence still required` · `PHASE 3 STATIC REVIEW PASS` · `Failed to CreateArtifact` ×3 — so the quota was **already** exhausted when this batch's first run published: no `apks-36974410187` exists |
| `f1454c6c5c254acd3ef8dfaa5ec188548d0a9664` (the §7 device probes, the corrected §1/§2 database claim, §11 itself) | `36976932429` | **success, with the same three artifact-upload failures** | `CI TEST SUMMARY: 332 tests, 332 passed, 0 failures, 0 errors, 0 skipped` · `BUILD SUCCESSFUL in 7m 37s` · `Group C boundary scan passed.` · `G4-B LOCAL REVIEW PASS — runtime/device evidence still required` · `PHASE 3 STATIC REVIEW PASS` — **and** `X Failed to CreateArtifact: Artifact storage quota has been hit. Unable to upload any new artifacts. Usage is recalculated every 6-12 hours.` ×3 (annotations `…github#20` ×2, `…github#24`), read from `gh run view 36976932429` after the job list showed the run `completed`/`success`, started `07:07:30Z`, updated `07:16:06Z` |
| `6e473e73a374c841be8a1ffe1b0606aae33d2ced` (the §7 re-run note, the corrected notification probe, F-K5 / Q-K-5) | `36978663360` | **success, with the same three artifact-upload failures** | `CI TEST SUMMARY: 332 tests, 332 passed, 0 failures, 0 errors, 0 skipped` · `BUILD SUCCESSFUL in 6m 4s` · `Group C boundary scan passed.` · `G4-B LOCAL REVIEW PASS — runtime/device evidence still required` · `PHASE 3 STATIC REVIEW PASS`, plus `Failed to CreateArtifact: Artifact storage quota has been hit` ×3 (`gh run view 36978663360 --json headSha,status,conclusion` → the SHA above) |
| `691056df2c304d68cc9c67faaa401cc6b6edffb8` (records the `6e473e7` verdict) | `36979575104` | **success, with the same three artifact-upload failures** | `CI TEST SUMMARY: 332 tests, 332 passed, 0 failures, 0 errors, 0 skipped` · `BUILD SUCCESSFUL in 7m 1s` · `Group C boundary scan passed.` · `G4-B LOCAL REVIEW PASS — runtime/device evidence still required` · `PHASE 3 STATIC REVIEW PASS` · `Failed to CreateArtifact` ×3 |

**The recording stops here.** This table's rows are `dd7cb1b`, `f1454c6`, `6e473e7` and `691056d`, each read
back from its own run object; the commit that adds this line is the fifth docs commit of the batch, and
quoting its verdict would require a sixth, so its result is carried into the next batch's report instead of
chasing itself — the §12 limit named below, applied once and closed.

What that proves, and what it does not: all four are **docs-only** commits, so the runs show that no gate,
permission, constant or claim was moved by them. They prove nothing about the findings in §1, and they are
not the device half — the `G4-B LOCAL REVIEW PASS` line says so itself ("runtime/device evidence still
required"). The 332 tests are JVM tests; the instrumented suite was **not** completed on this build (§7).
The same §12 limit recorded in `docs/reports/batch-J-lock-feasibility.md` §12 is what leaves the last
commit's own verdict out of this table.

### 11.1 F-K5 — the CI artifact quota is exhausted (measured; HIGH for verification, not for security)

**MEASURED (CI).** All four runs above finished green, and **all four failed every upload step** — 3
`Failed to CreateArtifact` errors each — so the quota was already exhausted at the moment this batch's first
report commit was verified. From `gh api repos/toufikben/shieldra/actions/artifacts`:

| Reading | Value |
|---|---|
| Artifacts in the repository | `total_count` = **301** |
| Space they occupy | `537343374 + 442473186 + 293971108 + 2256` bytes = **1,273,789,924 B ≈ 1,215 MiB** |
| Of which `apks-*` bundles | **69 artifacts ≈ 1,211 MiB** — i.e. the APK pairs are essentially the entire quota |
| Upload result on **all four** runs `36974410187`, `36976932429`, `36978663360`, `36979575104` | 3 × `Failed to CreateArtifact: Artifact storage quota has been hit` on **each** — one per upload step, on four runs inside **69 minutes** (`36974410187` created `06:37:02Z` → `36979575104`
updated `07:45:47Z`), so this is a standing state rather than a transient one — and it is the state the
quota message itself describes ("Usage is recalculated every 6-12 hours"). Cross-checked the other way: the artifacts listing (301 entries, 4 pages) contains **no** artifact named after any of these four run ids. Each run still reported `success` because all three steps are declared `continue-on-error: true` (`.github/workflows/…:72`, `:83`, `:92`), so a refused upload cannot fail CI. **That is the trap**: a green run no longer guarantees an APK exists |
| Workflow retention | `.github/workflows/…` calls `actions/upload-artifact@v4` at `:73` (`apks-<run-id>`), `:84` (`unit-test-results-…`), `:93` (`group-a-evidence-…`) with **no `retention-days`** anywhere in the directory (`grep -rn retention-days .github/workflows/` → 0 matches), so every run uploads at the repository default |
| Newest `apks-*` still present | `apks-36939853591` (18,532,050 B, `expired: false`) — the exact pair installed on the phone (§7) |

What it blocks and what it does not: JVM tests, lint, both builds and the static gates keep working — a
**docs** commit verifies normally. What stops working is any future **code** commit that needs its APK on
the phone: the upload will fail, so the device half of the verification cycle (§7's install-and-look step,
a rebuilt-APK instrumented pass, Batch L's release-candidate check) has no artifact to download. The
already-installed pair is code-identical to HEAD, so **the screenshot pass does not depend on this** — it
depends only on the owner unlocking the phone.

Why no fix was applied here: every available remedy is an owner call, not an engineering default.
Deleting artifacts destroys shared CI history that earlier batch reports cite as their evidence
(`apks-36939853591` is one of them) and is irreversible; adding `retention-days` to the workflow edits the
CI/CD pipeline; raising the storage quota is a billing change. Recorded as **Q-K-5** in §8 rather than
silently resolved.

**RESOLVED FOR DELIVERY 2026-10-02 — the owner chose a fourth way, and nothing was deleted.** Asked between
naming a deletion cut-off, raising the plan, an alternate delivery path, or waiting, the owner picked the
**alternate delivery path**. Each successful run now publishes the same-run APK pair to a rolling **draft
release `device-apks`** through a required, fail-closed step (`589f4a5` + `a147bef`); run `37003070708` is
**success** and its release carries `app-debug.apk` (18,505,786 B) and `app-debug-androidTest.apk`
(626,877 B) with `targetCommitish` equal to that exact SHA. The quota itself is untouched — 38 `apks-*`
pairs / 667.7 MiB remain and `apks-37003070708` is still absent from the artifacts listing — so the finding
stands as a measurement, and F-K5's warning stays true in its original form: **a green run still does not
prove an artifact exists.** What changed is that a green run now also proves a *downloadable pair* exists,
because the publish step fails the run if it cannot re-read both asset names from the release. Full record:
`docs/reports/batch-K-owner-answers.md` §6.5. The screenshot pass now depends only on the owner unlocking
the phone.
