# SHIELDRA — سجل الأسئلة الشامل / Master Questions Register

**Repository:** `toufikben/shieldra` — branch `main`
**Authority level:** this file is a *question register*, not authority. Binding policy
lives in [`SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md) and
[`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](authority/FINAL_SECURITY_STORAGE_DECISIONS.md).
**Purpose:** one file containing every question needed to finish the app — closed, open,
and future — so work proceeds without stopping, and so any past mistake can be corrected here.
**Compiled:** 2026-09-29 against commit `b1e0dec` (CI `36554090232` success).

## How to use this file

1. Sections **A** and **B** are already closed. Read them to verify what was done and to
   correct anything wrong (write your correction in the "Owner correction" line of that
   question's row).
2. Section **C** lists every question that still blocks finishing. Each has a recommended
   answer. You may answer with the recommendation (`نعم`) or write your own.
3. Section **D** lists questions that belong to later phases and only need answering when
   that phase is reached.
4. Nothing here is approved until you say so in writing. Per §14, an unanswered question is
   never silently defaulted.

**Answer format:** write the question number and your answer, e.g. `Q-C1: نعم` or
`Q-C3: نأخذ الخيار 2`. A one-word "yes" to a recommendation counts as approval of that
recommendation only — it does not approve the other questions.

---

## STATUS LEGEND

| Code | Meaning |
|---|---|
| **CLOSED** | Owner answered, implementation done, CI green, device verified. |
| **OPEN-BLOCKING** | Cannot proceed until the owner answers. |
| **OPEN-NONBLOCKING** | Not blocking the next code batch. |
| **DEFERRED** | Belongs to a later phase; answer when that phase starts. |

---

# A. CLOSED QUESTIONS — audit trail (for correction, not for re-asking)

These are the questions already answered in previous sessions. Nothing here should be
re-opened unless you believe an answer was wrong. If so, add a line to the question's
"Owner correction" field and it becomes a new amendment under §17.

## A.1 Crypto / Keystore (G4, §1–§3)

| ID | Question | Your answer | Implementation | Verification | Owner correction |
|---|---|---|---|---|---|
| Q-A1 | Is AAD mandatory for evidence encryption? (§1.2) | **YES — mandatory** | `KeystoreEvidenceCipherWithAad` refuses to encrypt without AAD | CI `36554090232` (171 JVM tests); `RealAesGcmAadTest` OK on RMX3910 | — |
| Q-A2 | What goes into the canonical AAD? (§1.1) | **Version + object class + object id, length-prefixed, never secret content** | `EvidenceAadBuilder` with canonical vector tests | CI + device | — |
| Q-A3 | What happens on `evidenceId` mismatch between AAD and envelope? (§1.3) | **Corrupt → UNAVAILABLE, never silently accepted** | AAD-mismatch classified as corruption, not availability | `RealAesGcmAadTest` wrong-AAD rejection on device | — |
| Q-A4 | Is a software key allowed when the Keystore fails? (§2.5) | **NO — never, no fallback path exists** | `AndroidKeystoreEncryptor` has no software-key branch | Static gate in `g4b-review-check.sh`; device reset test proves prior evidence becomes permanently unreadable | — |
| Q-A5 | Must StrongBox be used? What when the device has none? (§2.3) | **No hard requirement; the app must work and report honestly without StrongBox** | `AndroidKeystoreEncryptor` detects and degrades to TEE/software-backed Keystore with no claim of hardware backing | Your RMX3910 has **no StrongBox** — this is why no "hardware-backed" claim is ever made | — |
| Q-A6 | Is key rotation required? (§3.1) | **YES** | `EvidenceKeyRotator` with resume-after-interruption | `EvidenceKeyRotationInstrumentedTest` on device (needed a key warm-up first) | — |
| Q-A7 | How is the key alias scoped? (§2.1) | **Versioned, per-app, deterministic alias** | `EvidenceKeyPolicy` | JVM + device | — |

## A.2 Storage / migration (§4)

| ID | Question | Your answer | Implementation | Verification | Owner correction |
|---|---|---|---|---|---|
| Q-A8 | What happens if a migration fails? (§4.3) | **Fail closed — keep the database, never destroy and recreate it** | `openOrRecovery` returns `RecoveryRequired`; no destructive fallback | `StorageRecoveryInstrumentedTest` on device; static gate | — |
| Q-A9 | Is downgrade supported? (§4.4) | **NO — unsupported versions fail closed** | `StorageMigrations` rejects unknown versions | JVM + device | — |
| Q-A10 | Are backups and device transfer allowed? | **NO — databases and files excluded from both** | `AndroidManifest.xml` `allowBackup=false` + exclusion XML | `g4b` gate asserts the manifest and XML | — |

## A.3 Evidence lifecycle (§5) — includes your amendments

| ID | Question | Your answer | Amendment | Implementation | Verification |
|---|---|---|---|---|---|
| Q-A11 | How long are quarantined files kept? (§5.4) | **30 days** | **A-1** (2026-09-28) — replaced the original "7 days maximum" | `EvidenceRetentionPolicy` | JVM (23 lifecycle tests) |
| Q-A12 | Does a confirmed factory reset delete the Keystore evidence key? (§5.7) | **YES — permanently and irreversibly** | **A-2** (2026-09-28) — the only sanctioned Keystore-key deletion in SHIELDRA | `deleteEvidenceKeyIrreversibly` | `EvidenceKeyResetInstrumentedTest` OK (2 tests) on device |
| Q-A13 | Does removal happen before or after verification? (§5.7 step 7) | **Removal first; a cleanup failure is tombstoned and never reported as success** | **A-3** (2026-09-28) — a documented, narrow exception to §4.3 that applies only to explicit reset, not migration | `FactoryReset` state machine | CI + device |
| Q-A14 | What if the key is missing on read, non-deliberately? (§6) | **RECOVERY_REQUIRED — operator intervention** | **A-4** (2026-09-29) | `EvidenceKeyRecoveryRequiredException`; the path differentiates by ciphertext presence, not key state alone | CI §6/A-4 static gate; device |

## A.4 Runtime honesty (§15 wiring)

| ID | Question | Your answer | Implementation | Verification |
|---|---|---|---|---|
| Q-A15 | May the app open local storage in a default build? | **NO — storage is installed only behind an approval flag that stays `false`** | `StorageRuntimeApproval.approved = false`; `decideStorageInstall` | `StorageWiringGateOffInstrumentedTest` OK (3 tests) on device: no `shieldra.db`, no Keystore key created |
| Q-A16 | May the dashboard show "Protected" / an "Active" guard with no detection? | **NO — with no detection the app must never claim protection** | `LockGuardDecision` → `lockGuardDashboard`; `DemoData` dropped from the runtime path | CI gate `PASS: runtime guard projection is honest`; on device the app renders `DISABLED` |
| Q-A17 | May the UI invent evidence the storage does not have? | **NO — no fabricated photo, location, battery, or delivery receipt** | `PersistedEventReader` sets `hasPhoto=false`, `hasLocation=false`, battery `null` | CI gate + `PersistedEventReadingInstrumentedTest` OK (5 tests) on device |
| Q-A18 | May Room be queried on the main thread? | **NO — Room throws there; all persisted reads go to `Dispatchers.IO`** | `produceState` + `LaunchedEffect` in `ShieldraNavHost` | CI gate `PASS: persisted events are read off the main thread` |

## A.5 The key invariant these batches established

> **With no detection implemented, the app has nothing to protect and nothing to show.**
> It must present an honest disabled state, read nothing, and claim nothing. This is why
> §15 and G4 stay open: the app is not yet a security product, and the code says so.

---

# B. CLOSED QUESTIONS — process and process-level decisions

| ID | Question | Answer | Note |
|---|---|---|---|
| Q-B1 | Where does work happen? | `toufikben/shieldra` only; `toufikben/Ai_super_cleaner` is read-only | Never modified |
| Q-B2 | Build/test locally? | **NO — CI is the verifier**; APKs come from CI artifacts | Your PC: 2 cores / 4 GB / HDD — local builds are not viable |
| Q-B3 | Verification standard | Compiling ≠ proof. Every claim needs CI green **and** a device run on RMX3910 | Device test rule: app APK + androidTest APK from the **same** run id |
| Q-B4 | How is a claim marked when untested? | **NOT RUN** — never "PASS" | §16 |
| Q-B5 | Can I proceed without asking each time? | **YES** for implementation batches that follow an approved design and touch no policy | `feedback-autonomous-continuation`; but the approval gate in `feedback-approval-gate` still applies to new designs |
| Q-B6 | Can I silently change an approved policy? | **NO — §14; any change is a visible owner commit recorded in §17** | This file is where corrections are recorded |

---

# C. OPEN QUESTIONS — everything still blocking a finished app

These are in dependency order. **C1 blocks C2–C7.**

---

## Q-C1 — Detection scope (unblocks the biggest block) ⭐ HIGHEST VALUE

**The situation:** the app can store and read events, but nothing ever *creates* an event.
The dashboard is permanently `DISABLED` and the event list is permanently empty. Nine UI
buttons are wired to a "deferred" snackbar. The app is a shell.

**Question:** which of these should be built next, and how far?

| Option | What you get | Cost | Risk |
|---|---|---|---|
| **1 (recommended)** | **Build ONE detector end-to-end and prove the whole loop on your phone.** Recommended: **Panic Guard** — a button the user presses. It needs no permissions, no background service, no sensors, and no OEM caveats, and it proves: signal → confirmed event → persisted → appears in History → opens in Detail. | Small | Very low |
| 2 | **Lock Guard** (wrong PIN twice) | Large — needs a `DeviceAdminReceiver`, device-owner provisioning, or a fragile `KeyguardManager` workaround | High: store-policy risk, see **Q-C4**. Not something to discover after building |
| 3 | **Motion Guard** (accelerometer) | Medium — sensor adapter, calibration, sampling, battery | Medium: false positives, OEM sensor behavior |
| 4 | **SIM Guard** | Medium | Medium: `SUBSCRIPTION_STATE_CHANGED` broadcast limits, needs a `READ_PHONE_STATE` permission with a store disclosure |
| 5 | Battery anomaly | — | **G1-04 already forbids it**: battery % alone is not a security event. Not available. |
| 6 | All five | Huge | All of the above at once |

**My recommendation:** Option 1 (Panic Guard) first, then Motion, then decide on Lock with
the Play Store question answered. Reasoning: Panic is the only detector that can be honestly
proven end-to-end on one phone with zero permissions, which is exactly what §15 needs before
anything else is trusted.

**Your answer:** _____________

---

## Q-C2 — §15 production gate: when do we flip `StorageRuntimeApproval.approved`?

**The situation:** the storage wiring is complete and tested, but the flag is `false`, so a
real build never opens the database. Until the flag flips, the app you can install shows an
empty app forever.

**The honest dependency:** flipping this alone gives you an *empty* app, because no event is
ever created. The flag should be flipped **together with** the first real detector, not
before.

| Option | Meaning |
|---|---|
| **1 (recommended)** | Flip the flag in the same commit as the first working detector (Q-C1 option 1), after its device test passes. One visible commit, both halves. |
| 2 | Flip it now, keep the app empty until a detector exists. |
| 3 | Never ship a build that opens storage; keep it behind a build flavor. |

**My recommendation:** Option 1. It keeps §14 intact (the flip is a visible owner commit)
and never produces a build that claims more than it does.

**Your answer:** _____________

---

## Q-C3 — Permissions and onboarding: real or honest-stub?

**The situation:** the onboarding "Allow notifications" button shows a snackbar saying
"deferred". No permission is ever requested. Android requires notification permission
(`POST_NOTIFICATIONS`) for any alert to be visible, so no alert can ever be shown today.

**Question:** when a detector exists and needs to alert you, may the app request Android
permissions for real?

| Option | Meaning |
|---|---|
| **1 (recommended)** | **Yes — implement real permission requests** (notifications first, then camera/location only if and when a guard truly needs them), each with an honest reason string, each revocable in Settings. |
| 2 | No permissions ever; the app only works while open. Simpler, but an anti-theft app that cannot alert you is close to useless. |
| 3 | Yes, but request everything up front during onboarding. **I do not recommend this** — it is the pattern users reject and delete. |

**Also decide (only if you pick option 1):** is camera capture allowed at all, and if so
**when**? Options: (a) never; (b) only inside a confirmed event, for a fixed short window
(G2-01 already requires this); (c) continuously. **I recommend (b)** — G2-01 forbids (c),
and (a) leaves Panic evidence weak.

**Your answer:** _____________

---

## Q-C4 — Is the Lock Guard allowed on the Play Store at all?

**Why this must be decided before any Lock Guard work:** the answer changes the entire
design, so building first and discovering the answer afterwards wastes the batch.

**The problem:** detecting repeated wrong PINs on Android requires either
`DeviceAdminReceiver` (device-admin) or a `KeyguardManager` workaround. Both mean the app
observes the user's screen lock. Google Play has a **"Device and Network Abuse"** policy
covering device-admin use and lock-screen/credential observation, and it is widely
understood that a consumer app doing this needs a specific justification and can face
removal. The permitted categories are typically enterprise device management, parental
control, and anti-fraud where the device is enrolled in device-owner control.

> **Evidence status: PARTIALLY VERIFIED.** I located the governing policy
> ([Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646))
> and a related page ([Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16909972)),
> but I could not retrieve the article body, so **I am not quoting the exact wording**.
> Read those two pages yourself before committing to a store decision. Treat the
> characterization above as my understanding to be confirmed, not as verified policy text.

**Options:**
1. **Assume the Play Store is out of scope.** Ship as a sideloaded APK. All guards allowed.
2. **Play Store is required.** Lock Guard must be dropped, or restricted to an
   enterprise/MDM distribution channel.
3. **Undecided** — decide later; build Panic/Motion first, which are not affected.

**My recommendation:** Option 3 now, Option 1 if you intend to distribute by APK. This must
be settled **before** any Lock Guard work, because the answer changes the whole design.

**Your answer:** _____________

---

## Q-C5 — G2 evidence details (if you answered (b) in Q-C3)

If the camera may be used after a confirmed event, these numbers are currently undefined and
are all "PROPOSED — REQUIRES APPROVAL":

| Item | Recommended value | Question |
|---|---|---|
| Capture duration | **15 seconds** from event confirmation | How long? |
| Max photos | **3** | How many? |
| Audio | **No** — never record audio | Confirm or change |
| Location precision | **Coarse (~1 km), captured once, at event time** | Precise or coarse? |
| Location retention | 30 days, aligned with the evidence window | Confirm |
| On-device encryption | Already decided (§1) — no new question | — |
| Export | Already decided: **DISABLED by default** (§5.5) | — |

**Your answer:** _____________

---

## Q-C6 — Does an alert need to work when the app is closed?

**The situation:** even with a detector, an alert that only appears while the app is open is
of little value to someone whose phone was just stolen.

| Option | What it means | Cost |
|---|---|---|
| 1 (recommended) | **Foreground service + persistent notification** while a guard is active. Survives the app being swiped away. | Medium. Needs `FOREGROUND_SERVICE` + `POST_NOTIFICATIONS`, and OEM battery managers can still kill it. |
| 2 | Detect and record only; you open the app yourself. | Zero — but the app does not protect you. |
| 3 | `WorkManager` periodic work. | Low cost, but the minimum period is ~15 minutes — far too slow for a theft. |

**Honest warning I must record:** no Android mechanism gives a guaranteed 24/7 alert. Force
Stop, Doze, and OEM battery killers all defeat any approach. G3-01 requires this limitation
to be *documented in the app*, not hidden.

**Your answer:** _____________

---

## Q-C7 — G2/G3 consent and privacy text

**The situation:** the app stores evidence (photos, location) about a person's movements.
Google Play and most privacy regimes require a clear, in-app privacy policy and an
explicit consent step before collecting.

| Item | Recommendation |
|---|---|
| Privacy policy | Required. Must state: on-device only, no cloud, no analytics, no ads, what is collected and when. |
| Consent screen | Required, before any permission or capture. Plain language, Arabic + English. |
| Location permission text | Must say *why* and *when*, not just "for functionality". |
| Analytics/crash reporting | **None.** Adding them would be a §14 policy change. |

**Your answer:** _____________

---

# D. DEFERRED QUESTIONS — answer when that phase is reached

These do not block the next batch. They are listed so nothing is forgotten.

## D.1 Phase 5 — local response

| ID | Question | Default if you never answer |
|---|---|---|
| Q-D1 | What is the local response when an event is confirmed? (sound, vibration, full-screen alert) | **Nothing** — record only |
| Q-D2 | Who is notified? (SMS to a trusted contact? WhatsApp? Telegram?) | **Nobody** — the app shows the event in-app only |
| Q-D3 | Are recipients configurable by the user? | **No** — deferred with Q-D2 |
| Q-D4 | Retry policy if a notification fails | Deferred with Q-D2 |

Note: G5 defers all external providers. Q-D2 requires un-deferring G5 first.

## D.2 Phase 6 — external providers

| ID | Question | Default |
|---|---|---|
| Q-D5 | Is billing implemented? (Premium screen is a stub with two dead buttons) | **No billing** — Premium stays a non-functional screen |
| Q-D6 | Is a cloud backup / account ever wanted? | **No** — local-only app |
| Q-D7 | Ads / analytics | **No** — permanently off unless you change §14 |

## D.3 Phase 7 — release

| ID | Question | Default |
|---|---|---|
| Q-D8 | Distribution: Play Store, sideloaded APK, or both? | Depends on Q-C4 |
| Q-D9 | Signing: a real release keystore (never the CI debug one) | Required for any real distribution |
| Q-D10 | Minimum Android version to support? Currently `minSdk 26` (Android 8) | Keep 26, or raise to cut maintenance |
| Q-D11 | App name, icon, package name final? | `com.shieldra.app` — unchanged |
| Q-D12 | Who signs the G6 security/privacy review, and when? | Nobody yet — **G4 and §15 cannot close without it** |

## D.4 G4 acceptance items that remain open regardless

| ID | Item | Why it is still open |
|---|---|---|
| Q-D13 | Threat model sign-off | `docs/security/g4-threat-model.md` is still `PROPOSED — REQUIRES SECURITY/PRIVACY REVIEW` |
| Q-D14 | OEM/API device matrix beyond your RMX3910 | All device evidence comes from **one** phone. Samsung/Xiaomi/Google differ on background behavior. |
| Q-D15 | Full audit set: storage, crypto, guard, evidence, alert, permission, privacy | Seven audits required by §15. Only storage/crypto have evidence so far. |
| Q-D16 | Process-death and reboot recovery evidence for the *whole app*, not just storage | Storage is tested; the event pipeline under process death is not. |
| Q-D17 | Reinstall / downgrade / backup-restore behavior on a real device | Policy exists; device evidence is `NOT RUN`. |

---

# E. CORRECTIONS — for anything I got wrong

If a past answer or a past implementation looks wrong, write it here. Each correction
becomes a numbered amendment under §17 of the decision record before any code changes.

| Date | What was wrong | What it should be | Your note |
|---|---|---|---|
| — | — | — | — |

**Common corrections that are already recorded and do not need repeating:**

- Batch `4d-aad-binding` originally closed without a §16 report and with a placeholder
  `assertTrue(true)` device test. Both were fixed and documented in
  `docs/reports/batch-4d-key-rotation.md`.
- `gh run watch` has three times reported a false failure caused by flaky network, not by a
  failing workflow. Only the run object itself (`gh run view --json conclusion`) is trusted.
- `adb pull /sdcard/...` needs `//sdcard/...` under Git Bash.

---

# F. WHAT HAPPENS AFTER YOU ANSWER

Once Q-C1 … Q-C3 are answered, the work proceeds without further questions in this order:

1. **The first detector**, TDD: RED test → GREEN implementation → CI → device.
2. **Flip `StorageRuntimeApproval.approved`** in the same visible commit (Q-C2 option 1).
3. **Real permission requests** with honest reason strings (Q-C3 option 1).
4. **Foreground service + notification** for the alert (Q-C6 option 1).
5. **History and Dashboard show real events** — already wired, they simply have no producer
   until step 1.
6. **Wiring the currently dead buttons** — `onPanic`, `onGuardClick`, the event-detail
   `onDelete` / `onExport`, and the Premium/Settings actions.
7. **Re-run the device matrix** and the full §15 audit set.

Every one of those steps is a separate batch with its own §16 report, its own CI run, and
its own device verification. No step claims completion without both.

**If you answer nothing at all**, the app stays exactly as it is: honest, tested, and empty —
with storage never opened and no claim of protection. That is a safe stopping point, not a
failure.
