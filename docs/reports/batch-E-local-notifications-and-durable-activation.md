# Batch E — §16 real local notifications (A-6) and durable Panic activations (A-7)

## BATCH
`E-local-notifications-and-durable-activation` (protocol **Batch E**) — turn the deferred
notification stub into the two things the owner approved on 2026-09-30: **amendment A-6**
(real *local* Android notifications, requested only from a setup screen, never at the Panic
moment, never blocking event recording, never read as delivery) and **amendment A-7** (the
Panic activation count becomes persistent metadata on the same event, through an explicit
Room v2→v3 migration).

## SCOPE
- **A-6 — the notification surface.** `POST_NOTIFICATIONS` is declared in the manifest, one
  channel (`shieldra_security_events`, `IMPORTANCE_HIGH`) is created on device, and one local
  notification is posted per recorded Panic event. The permission is requested only from
  `NotificationSetupScreen`; the Panic path contains no request.
- **A-6 — three separate facts, never merged.** `NotificationPermissionState`
  (`NotRequired | NotRequested | Granted | Denied`) describes the device, `LocalPostResult`
  (`NotAttempted | PostedLocally | FailedToPost`) describes what Android actually accepted,
  and the Security Event's own state stays in the domain model. Nothing in this batch maps a
  local post to `DeliveryStatus.SUCCESS` or to `EventState.DELIVERED`; the g4b gate greps for
  that and fails the build.
- **A-6 — "never asked" is not "refused"** (fixed inside this batch, see SELF-ADVERSARIAL
  FINDINGS 1). `PermissionRequestRecord` remembers that the system dialog was answered; until
  then the live platform DENIED is reported as `NotRequested`. This flag lives in one app
  preference, never inside an Event.
- **A-7 — durable activation count.** `panic_activation_count` and
  `panic_last_activated_at_epoch_ms` are nullable columns on `security_events`; Room version
  3, exported `3.json`, explicit `MIGRATION_2_3`, no destructive fallback. The increment is a
  single atomic `UPDATE … COALESCE(count,0)+1 … WHERE state NOT IN (terminal)` exposed through
  a dedicated `PanicActivationStore` API, so `updateState()` still refuses unsupported
  metadata. `SecurityEvent.metadata` is not used for the counter. The first save already
  carries count = 1.
- **A-7 — Panic-scoped binding.** The coordinator only binds a repeat press to an event whose
  `type == PANIC_ACTIVATION`, so a Panic can no longer attach to another guard's open event,
  and after a terminal state a new press starts a new `EventId` at count 1.

Deliberately out of scope: any remote or external provider (no FCM/Firebase/cloud/SMS/email —
§30 keeps them deferred), the Google Play listing copy (outside this repository), the
misleading Settings labels found while verifying (FINDINGS 4 and 5), and background/foreground
monitoring (Batch F).

## FILES CHANGED
- app/src/main/AndroidManifest.xml (one `<uses-permission>`: `POST_NOTIFICATIONS`, plus its
  reason string)
- app/src/main/kotlin/com/shieldra/notification/NotificationState.kt (new — pure state model)
- app/src/main/kotlin/com/shieldra/notification/LocalEventNotifier.kt (new — channel, live
  permission read, request record, local post keyed by event id)
- app/src/main/kotlin/com/shieldra/app/presentation/screen/settings/NotificationSetupScreen.kt
  (new — the only place the permission is requested)
- app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt (notifier wiring, post
  only after persistence, request recorded in the launcher callback, state re-read on return)
- app/src/main/kotlin/com/shieldra/app/navigation/ShieldraRoutes.kt (`NOTIFICATION_SETUP`)
- app/src/main/res/drawable/ic_shield_notification.xml (new notification icon)
- app/src/main/res/values/strings.xml, values-ar/strings.xml (channel, screen, state and
  result labels in both locales)
- app/src/main/kotlin/com/shieldra/domain/SecurityEventContracts.kt (activation count +
  last-activated instant on the event; `PANIC_ACTIVATION` type)
- app/src/main/kotlin/com/shieldra/domain/PanicIncidentCoordinator.kt (persist-first with
  count 1, atomic increment through the store, Panic-type filter, no time window)
- app/src/main/kotlin/com/shieldra/storage/room/StorageEntities.kt, StorageDaos.kt,
  RoomEventRepository.kt, ShieldraDatabase.kt, ShieldraDatabaseMigrations.kt (v3 columns,
  `PanicActivationStore` implementation, `MIGRATION_2_3` registered)
- app/schemas/com.shieldra.storage.room.ShieldraDatabase/3.json (exported schema)
- app/src/test/kotlin/com/shieldra/notification/NotificationStateTest.kt (new — 10 JVM tests)
- app/src/test/kotlin/com/shieldra/domain/PanicActivationDurabilityTest.kt (new — 8 JVM tests)
- app/src/test/kotlin/com/shieldra/domain/PanicIncidentCoordinatorTest.kt (+13 lines: A-7
  assertions on event type, count = 1 on first write, instant recorded)
- app/src/androidTest/kotlin/com/shieldra/notification/NotificationPermissionStateInstrumentedTest.kt
  (new — 4 device tests)
- app/src/androidTest/kotlin/com/shieldra/storage/room/PanicActivationSchemaMigrationInstrumentedTest.kt
  (new — 3 device tests)
- app/src/androidTest/kotlin/com/shieldra/storage/EvidenceKeyLifecycleInstrumentedTest.kt
  (v3 fixture rows now pass the two nullable columns)
- app/src/androidTest/kotlin/com/shieldra/storage/room/ShieldraDatabaseMigrationInstrumentedTest.kt
  (v3-aware)
- .github/scripts/g4b-review-check.sh (§5b A-7 gates + §5c A-6 notification gates)
- .github/scripts/phase3-review-check.sh (the boundary scan now permits exactly one
  `<uses-permission>`)
- docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md (amendments **A-6**, **A-7** in §17)
- docs/OWNER_QUESTIONS.md (Q-C3 → A-6 implemented, Q-C9 → A-7 answered)
- SHIELDRA_DECISIONS.md ("not approved yet" list now points at A-1…A-7)
- docs/ROADMAP.md (Batch E closed, Batch F named)

## DECISIONS ENFORCED
§16 (a recorded event is the notification's precondition, never its consequence), §23 (each
permission is requested by the feature that needs it, at the moment it needs it, with the
reason shown first), §31 (no UI may claim more than the state behind it: the notification body
carries the event's real state label, and no local post is ever worded as delivered), §12–§13
(lifecycle remains the sole authority for binding an activation to an incident), §14 (A-6 and
A-7 are recorded as visible amendments before the code that depends on them), §4.3 / decision
4-3 (v2→v3 is an explicit migration; a build still refuses `fallbackToDestructiveMigration`),
§2.5 (no software key anywhere in the new path), and §30 (no cloud, SMS, email, push or other
external provider — the g4b gate greps the notification package for them).

## TESTS
CI `clean test lint assembleDebug assembleDebugAndroidTest`, workflow "Group A diagnostics"
on `toufikben/shieldra` `main`:

- `36762304298` (`f16e635`) — **RED**, fails as intended: the A-7 store API and the A-6 state
  model do not exist yet.
- `36764485998` (`a8d4bee`) — GREEN attempt, **failure**: unresolved imports for the durable
  activation store.
- `36765606935` (`bdfb8ba`) — **failure** at `:app:compileDebugUnitTestKotlin`: 6 errors in my
  own `NotificationStateTest` (a missing required `granted` argument, and `.entries` on a
  sealed interface).
- `36767514580` (`8084026`) — **failure**: 2 JVM assertions wrong (see FINDINGS 2, 3).
- `36767897215` (`aa3517c`) — **failure**: the same 2 assertions.
- `36768362493` (`c3f367b`) — **failure** at androidTest compile: `EvidenceKeyLifecycleInstrumentedTest`
  built a `security_events` row without the new v3 columns.
- `36769294871` (`ffd6745`, pushed with `aa63f9b`) — **narrow RED for FINDING 1**: the only
  compile error left is `Unresolved reference 'PermissionRequestRecord'` in the new
  notification device suite, i.e. exactly the missing behaviour.
- `36770817105` (`4ec751b`) — **success**.
- `36771561585` (`53f0f43`, HEAD) — **success**: 29 result classes, **231 JVM tests, 0
  failures, 0 errors, 0 skipped**, lint clean, `assembleDebug` and
  `assembleDebugAndroidTest` green, all review scripts green.

This batch adds **28 JVM tests** (`NotificationStateTest` 10, `PanicActivationDurabilityTest` 8,
plus the A-7 assertions inside the 10 `PanicIncidentCoordinatorTest` cases).

A-6 JVM coverage: `below_api_33_the_runtime_permission_is_not_required`,
`at_or_above_api_33_a_missing_grant_is_not_requested`,
`an_explicit_grant_and_an_explicit_refusal_are_distinguished`,
`an_unavailable_permission_never_yields_a_posting_result`,
`the_grant_is_rechecked_at_post_time_not_trusted_from_earlier`,
`a_granted_permission_reports_the_real_outcome_of_the_post`,
`a_thrown_post_is_reported_as_failed_not_as_a_success`,
`no_local_result_ever_reads_as_delivered`, plus the two old-platform cases.

A-7 JVM coverage (the named scenarios): first activation persists 1; repeated activation while
non-terminal increments the persisted count; a terminal event cannot be incremented; a new
Panic after terminal starts at 1; repeats never create a second active Panic event; concurrent
activations lose no increment; count and instant are read back from storage by a fresh
coordinator after restart; `lastActivatedAt` is storage-backed; the persisted event is typed
`PANIC_ACTIVATION`; duplicate `EventId` is bound, not re-confirmed; an unpersistable Panic is
rejected instead of claimed.

## ANDROID TESTS
RUN on the owner device **RMX3910** (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36, **no StrongBox
on this device**) with the app APK and the androidTest APK taken from the **same** green run
`36771561585`, after force-stopping and uninstalling both packages:

- `NotificationPermissionStateInstrumentedTest` — **`OK (4 tests)`**: never-asked reads as
  `NotRequested` (not `Denied`); once asked the state is always a real answer; a post succeeds
  exactly when the live grant allows it and never claims more; the channel really exists on
  the device after `ensureChannel()`.
- `PanicActivationSchemaMigrationInstrumentedTest` — **`OK (3 tests)`**: v2 rows survive to v3
  with both new columns NULL (not 0); `MIGRATION_2_3` runs and validates on the device and the
  atomic increment produces count 2 with the later instant for a live event while a terminal
  event stays NULL; the migration is registered after `MIGRATION_1_2`.
- `RoomPanicPersistenceInstrumentedTest` — **`OK (3 tests)`** (durable count across a reopened
  real Room database on the device).
- `StorageRuntimeInstallInstrumentedTest` — **`OK (6 tests)`**.
- Targeted set as one run: **`OK (12 tests)`**.
- **Full instrumented suite run twice on one install: `OK (55 tests)` then `OK (55 tests)`**,
  0 failures.

Observed on screen (uiautomator text dumps, not screenshots):

1. Fresh install → Settings → notification setup reads **"Not requested"** — the state A-6
   demands, and the pre-batch code showed "Not allowed" here.
2. "Turn on notifications" raises the real system dialog ("Allow Shieldra to send you
   notifications?"). In the first install I answered **"Don't allow"** and the screen then read
   **"Not allowed"** — a refusal, and nothing else changed.
3. After the permission was granted (`dumpsys package` → `POST_NOTIFICATIONS: granted=true,
   flags=[USER_SET…]`) a Panic press posted a **real notification record**:
   `pkg=com.shieldra.app channel=shieldra_security_events id=-1339296980
   tag=144c9135-9662-49eb-bc62-26f216bd8ea1 category=alarm flags=AUTO_CANCEL`, with
   `android.title="Security event recorded"` and
   `android.text="Recorded security event. Status: Recorded."` The tag is the Event ID shown
   later on the event's Detail screen — the notification is bound to a real persisted event.
4. **Repeated Panic presses do not duplicate notification work:** three further presses left
   exactly one `NotificationRecord` for the app, still one row in History ("Security event ·
   Skipped"), and the same tag/id. Across a cold restart (`am force-stop` + relaunch) the next
   press reused the same Event ID rather than starting a second incident.
5. **Revoked after grant is detected.** `pm revoke` is blocked on this OEM build, so the
   permission was turned off through the system's own app-notification settings (`granted`
   became `false`). The app's screen then read **"Not allowed"**, and a further Panic press
   posted **0 notification records** while the recorded event stayed in History — refusal
   costs the notification and nothing else.

**NOT RUN / not claimed:** the integer activation count in the shipping app's own database file
was not read (no `sqlite3` on the device and `pm clear` is blocked on this build; the value is
proven in Room on-device by `RoomPanicPersistenceInstrumentedTest`, not by inspecting
`shieldra.db`). No claim is made that the user *saw* the notification — only that Android
accepted the post.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` succeeded in CI run `36771561585`; the APKs used
for device verification came from that same run's `apks-36771561585` artifact. No local build
was attempted (owner directive: CI is the verifier on this machine).

## STATIC AUDIT
Grep-based gates, run locally and in CI on HEAD:
- `.github/scripts/g4b-review-check.sh` — **PASS** (34 checks, including the new §5b A-7 and
  §5c A-6 gates).
- `.github/scripts/group-c-contract-check.sh` — **PASS**.
- `.github/scripts/phase3-review-check.sh` — **PASS** (now allows exactly one
  `<uses-permission>`).
- `.github/scripts/group-a-diagnostics.sh` is an *environment* diagnostic and cannot pass on
  this machine (no `gradle`/`sdkmanager`/`adb` on its PATH); it passed in CI and its evidence
  artifact is `group-a-evidence-36771561585`.

New gate lines added so the fixes cannot regress silently: the notifier must be able to tell a
permission it never asked about from a refusal (`hasBeenRequested()`), and the UI must record
that the dialog was answered (`recordPermissionRequest()`); exactly one declared permission;
no `DELIVERED`/`SENT` in the notification vocabulary; no remote provider in the notification
package; the Panic file must not contain `RequestPermission`.

## SECURITY AUDIT
- Attack surface added: one runtime permission and one local notification channel. No network,
  no new exported component, no provider, no new file, no new key material. The notification
  carries only the event's own state label — no location, no photo, no evidence path, no
  timestamp that the app cannot produce.
- The permission-request flag is app-private `SharedPreferences` on `applicationContext`,
  `MODE_PRIVATE`, and is *capability state*, never Event state: an Event's truth cannot be
  altered by it, and clearing app data cannot make an Event lie.
- Fail-closed behaviour preserved: a Panic that cannot be persisted is `Rejected` and posts
  nothing; a thrown post becomes `FailedToPost(reason)`, never `PostedLocally`; a missing
  channel is reported as a failed post rather than a silent success.
- Schema change reviewed for downgrade/failure handling: `MIGRATION_2_3` is registered, both
  columns are nullable (so v2 rows are not fabricated into "already activated"), and
  `requireNoDestructiveFallback()` still refuses a destructive build.
- `POST_NOTIFICATIONS` is the only permission declared; §23's incremental rule is enforced by
  the count check in the gate, so a future batch must record an amendment to add another.

## SELF-ADVERSARIAL FINDINGS
1. **Found by reading the code, not by CI: a never-requested permission was displayed as
   "Not allowed".** `checkSelfPermission` returns DENIED for both silence and refusal, so the
   first A-6 implementation invented a refusal the user never made — exactly what A-6 forbids.
   Written as a narrow RED first (`ffd6745`, run `36769294871` failed on
   `Unresolved reference 'PermissionRequestRecord'` and nothing else), then repaired in
   `4ec751b` and verified on the device (ANDROID TESTS 1 and 2).
2. **My own test was wrong, not the code:** I expected three activations where only two presses
   reached the closed incident, and asserted the pre-A-7 event type. Both assertions were
   corrected in `c3f367b`; only the two assertions A-7 supersedes were touched — the pipeline
   design was not redesigned to make a test pass.
3. **A latent v3 breakage in a fixture I did not write for:** `EvidenceKeyLifecycleInstrumentedTest`
   constructed a `security_events` row positionally and silently broke when the columns were
   added. Fixed by passing both NULLs with a comment explaining the row is a storage fixture,
   not a domain event; I refused the tempting shortcut of inventing a fake `eventType` value.
4. **The notification screen is reached from a Settings row labelled "Delivery channels ·
   Email · WhatsApp · Telegram"** — there is no email, WhatsApp or Telegram capability (§30
   defers them), and no row says "Notifications". A user cannot find the setting, and the label
   claims channels the app does not have. **NOT fixed in this batch** (out of A-6's scope);
   it is a truthful-copy defect for Batch F.
5. **Two other UI claims are now stale since A-5 opened real storage:** the dashboard banner
   still says "Phase 3 presentation demo — no production data or integrations" while real Room
   events are being written, and the Settings "Storage" row still shows demo values
   ("12 events · 3.2 MB") instead of the real counts. **NOT fixed in this batch**; recorded
   here so the next batch cannot claim the UI is honest while they stand.
6. **The notification body claimed a time the app never had.** `notification_event_body` read
   "… at %1$s" while the caller passes a state label, and the repository contains no date/time
   formatter at all. Reworded in both locales to "Recorded security event. Status: %1$s" —
   verified as the actual `android.text` on the device.
7. **The "not attempted" result label asserted two facts it could not know** — that the user
   refused, and that the event was still recorded. It is returned both for a refusal and for a
   permission never asked about, and the dashboard also sets it when the Panic itself was
   rejected (in which case nothing was recorded). Reduced to the one true statement in
   `53f0f43`: "No notification was posted."
8. **Five successive CI failures were all type/compile or assertion problems that no static gate
   in this repository can see** (the gates are grep pattern checks, and local builds are
   forbidden). Since run `36770817105` proved the fix chain, I hand-audit every API I use
   against `androidTest`-compilable reality before pushing, and I do not call an unverified
   androidTest file "passing".

## COMMIT
`f16e635` (RED) → `a8d4bee` (GREEN) → `bdfb8ba` → `8084026` → `aa3517c` → `c3f367b` →
`aa63f9b` → `ffd6745` (RED for finding 1) → `4ec751b` (GREEN for finding 1) →
**`53f0f43`** (HEAD: findings 6 and 7 + SHIELDRA_DECISIONS pointer).

## REMOTE
`toufikben/shieldra`, branch `main`. Verified with
`gh api repos/toufikben/shieldra/commits/<sha>` — `53f0f43` is on the remote and is the
parent-less tip used for all evidence above.

## CI
Workflow "Group A diagnostics" run **`36771561585`** (HEAD `53f0f43`) — **success**:
231 JVM tests / 0 failures / 0 errors / 0 skipped, lint clean, both APKs assembled, Group C
boundary scan, g4b and Phase 3 gates all green. Previous run `36770817105` (`4ec751b`) also
success; the eight runs before it failed for the reasons itemised under TESTS.

## ROADMAP
`docs/ROADMAP.md`: row `E-local-notifications-and-durable-activation` closed (CI + DEVICE);
§15 and G4 remain open and unaccepted; Batch F named as required next batch.

## STATUS
**Batch E CLOSED (CI + DEVICE).** A-6 is implemented and true on the owner's phone: the three
facts (permission state / posting attempt / post result) stay separate, an unanswered permission
is not a refusal, a refusal costs nothing but the notification, and no local post is ever
worded as delivered. A-7 is implemented and true: the count is persistent, atomic,
Panic-scoped, migrated without a destructive fallback, and survives process death.

G4 is **not** accepted by this batch. §15 is **not** closed: detectors other than Panic, the
background/foreground mechanism, Motion/SIM/Lock runtime capture and the audit suite remain.

## BLOCKERS
None blocking the next batch. Two owner-side items:
1. **Store copy** (outside this repository): the live Google Play listing still advertises
   "zero Android permissions". After A-6 the honest promise is "no permissions until you need
   them". A-6 authorises correcting only those passages; the wording is the owner's to publish.
2. **A-6's own entry point** (FINDINGS 4/5) needs one owner line: should the notification
   setting get its own Settings row ("Notifications") and should the "Delivery channels" row
   and the demo banner/storage numbers be corrected in Batch F, or deferred?

## NEXT BATCH
**Batch F — background/foreground monitoring** (protocol §38 order: Panic → notifications →
background → Motion), including the truthful-copy items listed in FINDINGS 4 and 5 as its first
task, since §31 is violated by them today.
