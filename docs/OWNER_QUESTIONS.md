# SHIELDRA — سجل الأسئلة الشامل / Master Questions Register

**Repository:** `toufikben/shieldra` — branch `main`
**Authority level:** this file is a *question register*, not authority. Binding policy
lives in [`SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md) and
[`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](authority/FINAL_SECURITY_STORAGE_DECISIONS.md).
**Purpose:** one file containing every question needed to finish the app — closed, open,
and future — so work proceeds without stopping, and so any past mistake can be corrected here.
**Compiled:** 2026-09-29 against commit `b1e0dec` (CI `36554090232` success).
**Corrected:** 2026-09-29 after re-reading the store-launch decisions — see the CORRECTION
boxes in Q-C3, Q-C4, Q-C7, Q-D5 and Q-D7.
**ANSWERED:** 2026-09-29 — the owner supplied the **Final Master Execution Protocol**,
which answers C1–C7 and fixes the execution order. Its rulings are transcribed into each
question below and into `docs/ROADMAP.md`. Where the protocol conflicts with an older note
in this file, the protocol is marked and the conflict is recorded, not hidden.

> **Two items are still open after the protocol, and neither blocks Batch B or C:**
> 1. **Q-D5/Q-D7 — ads and billing.** The protocol (§30) contradicts the 2026-09-29
>    store-launch decision. Needs one line from you. See the CONFLICT box there.
> 2. **Q-C8 — destroying a *retired* evidence key version.** §10 forbids me from inventing a
>    destructive policy, so Batch B stops at `RETIRED` until you answer.
>
> Everything else in section C is answered and quoted to its source.

> **Important context:** Shieldra is one of four apps in a Google Play launch effort
> (with Pulse, AnimAi, and AI Photo Studio Chat). The store listing is being written in
> parallel, so **a decision here can invalidate copy already written there.** Two claims are
> already in tension with the code as it stands today:
> - the listing says the app ships with **zero Android permissions** — true right now
>   (verified: no `uses-permission` in the manifest), but **it will stop being true at
>   Batch E**, when the real notification path needs `POST_NOTIFICATIONS`, and again at Batch F
>   for whatever the active guard legitimately requires (§16–§17). That wording is
>   **pre-launch copy, not a permanent constraint**: §23 requires permissions to be
>   incremental and necessary, which is stricter than promising zero and is the rule I follow.
>   Whoever owns the listing must update the sentence before release; I am flagging it here so
>   the copy is not silently invalidated by a batch that had no idea it existed;
> - the listing describes an **anti-theft** app — true as intent, but the app currently has
>   no detection, so no shipped feature may be advertised that the code does not do.
>   Panic (Batch C) is the first claim the code will actually support.

## How to use this file

1. Sections **A** and **B** are already closed. Read them to verify what was done and to
   correct anything wrong (write your correction in the "Owner correction" line of that
   question's row).
2. Section **C** held the seven blocking questions, Q-C1 … Q-C7. All seven are now answered
   from the protocol; each row quotes the section that answers it, so the answer can be checked
   rather than trusted. **Q-C8 is new** — it is a question the protocol itself raised (§10) and
   did not answer.
3. Section **D** lists questions that belong to later phases and only need answering when
   that phase is reached. **D.2 is the exception: it carries the one open conflict.**
4. Authority order, since the protocol arrived: **the owner's Final Master Execution Protocol
   > `SHIELDRA_DECISIONS.md` + `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` > this
   register.** This file is still a question register, not the ultimate authority. Per §14 an
   unanswered question is never silently defaulted — and per §37 a *new* policy question is
   still one I must ask rather than assume.

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

# C. FORMERLY-BLOCKING QUESTIONS — now answered from the protocol

These were in dependency order and **C1 blocked C2–C7**. All seven are answered below, each
with the protocol section that answers it. One new question (Q-C8) was raised by the protocol
itself and is open.

---

## Q-C1 — Detection scope (unblocks the biggest block) ⭐ HIGHEST VALUE

**The situation:** the app can store and read events, but nothing ever *creates* an event.
The dashboard is permanently `DISABLED` and the event list is permanently empty. Nine UI
buttons are wired to a "deferred" snackbar (`ShieldraNavHost.kt` lines 130, 154, 155, 184,
185, 192, 221, 222, 223 — `onAllow`, `onGuardClick`, `onPanic`, `onBuy`, `onRestore`, the
settings callback, `onOpenInMaps`, `onDelete`, `onExport`). The app is a shell.

**Question:** which of these should be built next, and how far?

| Option | What you get | Cost | Risk |
|---|---|---|---|
| **1 (recommended)** | **Build ONE detector end-to-end and prove the whole loop on your phone.** Recommended: **Panic Guard** — a button the user presses. It needs no permissions, no background service, no sensors, and no OEM caveats, and it proves: signal → confirmed event → persisted → appears in History → opens in Detail. | Small | Very low |
| 2 | **Lock Guard** (wrong PIN twice) | Large — needs a `DeviceAdminReceiver`, device-owner provisioning, or a fragile `KeyguardManager` workaround | High: store-policy risk, see **Q-C4**. Not something to discover after building |
| 3 | **Motion Guard** (accelerometer) | Medium — sensor adapter, calibration, sampling, battery | Medium: false positives, OEM sensor behavior |
| 4 | **SIM Guard** | Medium | Medium: `SUBSCRIPTION_STATE_CHANGED` broadcast limits, needs a `READ_PHONE_STATE` permission with a store disclosure |
| 5 | Battery anomaly | — | **G1-04 already forbids it**: battery % alone is not a security event. Not available. |
| 6 | All five | Huge | All of the above at once |

**Your answer:** ✅ **ANSWERED — Option 1. Protocol §12–§13: implement Panic Guard
end-to-end as the highest-value next product batch.** Confirmed by the trace:
`ProtectionStateEngine.kt:32` already maps `GuardKind.PANIC` → immediate
`confirmed(..., GuardSeverity.CRITICAL, ...)`, so the domain rule exists and only the
platform path is missing. §13 requires the full loop
`UI → signal → immediate confirmation → EventId → SecurityEvent → EventPipeline → Room →
reload → History → Detail`, idempotent on repeat (§13, §15). Motion follows as Batch G.

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

**Your answer:** ✅ **ANSWERED — Option 1. Protocol §11: "DO NOT flip it alone"; §14: flip
in the SAME controlled batch as the first genuine detector, and only AFTER that detector
passes device verification.** Protocol Batch D is explicitly bound to Batch C. Until then
`StorageRuntimeApproval.approved` stays `false`.

---

## Q-C3 — Permissions and onboarding: real or honest-stub?

**The situation, now confirmed by reading the code:** `AndroidManifest.xml` declares
**zero** `uses-permission` entries, and no `android.permission.*` string appears anywhere
in `app/src/main/`. The app today ships with no permissions at all. The onboarding "Allow
notifications" button shows a snackbar saying "deferred" — nothing is ever requested.

**Question:** when a detector exists and needs to alert you, may the app request Android
permissions for real?

| Option | Meaning |
|---|---|
| **1 (recommended)** | **Yes — implement real permission requests** (notifications first, then camera/location only if and when a guard truly needs them), each with an honest reason string, each revocable in Settings. |
| 2 | No permissions ever; the app only works while open. Simpler, but an anti-theft app that cannot alert you is close to useless. |
| 3 | Yes, but request everything up front during onboarding. **I do not recommend this** — it is the pattern users reject and delete. |

> **Correction to my earlier recommendation.** I had marked option 2 as a reasonable
> default. I was wrong: my own store notes record Shieldra's listing as **"it ships with
> zero Android permissions"** — that is a selling point in the store copy, not a limitation
> to design around. Option 2 would be self-contradictory with the listing. **Option 1 stands,
> and the copy needs a matching "no permissions until you need them" promise.**

**Your answer:** ✅ **ANSWERED — Option 1 (incremental). Protocol §23: permissions are
requested "incrementally and only for real current functionality"; explicitly **NOT**
camera + location + phone state during onboarding. §16: request `POST_NOTIFICATIONS` only
when the feature genuinely needs it, and handle granted / denied / disabled-in-Settings
honestly — never show "alert sent" without permission.**
Camera and location are **excluded from the Panic batch** by §21–§22, so neither moves into
the manifest until Batch H.

**Also decide (only if you pick option 1):** is camera capture allowed at all, and if so
**when**? Options: (a) never; (b) only inside a confirmed event, for a fixed short window
(G2-01 already requires this); (c) continuously. **I recommend (b)** — G2-01 forbids (c),
and (a) leaves Panic evidence weak. Note this directly changes the store copy: claiming
"zero permissions" and shipping camera capture are not both true.

→ **Partially answered:** §21 rules out (c) and forbids adding a camera merely to make
Panic look advanced; camera "can only be considered after a confirmed event + an explicit
approved evidence policy", which is exactly option (b)'s shape. **The specific parameters
are still open and are deferred to Batch H (Q-C5).**

---

## Q-C4 — Is the Lock Guard allowed on the Play Store at all?

> **CORRECTION — this question was already answered by you on 2026-09-29.**
> You told me: *"أنشئ كلاهما رغم التجميد"* — build **both**, despite the freeze. This is
> recorded in my memory as the four-app launch effort (Shieldra, Pulse, AnimAi, AI Photo
> Studio Chat), and it overrides `SHIELDRA_DECISIONS.md` G0-05. **Per §14 I am not treating
> that as silent; it is written here, and the repository freeze will need a visible owner
> amendment to match.** Until you confirm, this row stays as written below and Lock Guard
> remains unbuilt — the store answer is still what decides the design.

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

**Your answer:** ✅ **ANSWERED for now — Option 3, as protocol §38 Batch J. §24: "DO NOT
implement Lock Guard Android observation yet"; the domain rule stays approved but the
platform implementation is deferred. Before any Lock code, §24 requires five gates in
order: Android observability feasibility, DeviceAdmin/Keyguard implications, the **current**
Google Play policy, and the distribution decision (Play vs sideload/enterprise).**
§25 adds a hard evidence rule: **do not treat old policy summaries as authoritative, do not
rely on community interpretation — cite the exact official source in the engineering
report.** That is precisely why my partial verification above is marked as insufficient; it
must be redone with the real policy text at Batch J, not carried forward as a premise.

---

## Q-C5 — G2 evidence details (camera, photos, location precision)

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

**Your answer:** ⏸️ **DEFERRED — not needed for the next batches.** Protocol §38 puts evidence
and privacy work in **Batch H**, after Panic (C/D), notifications (E), background execution
(F) and Motion (G). Protocol §21 is explicit: *"Camera can only be considered after confirmed
event + explicit approved evidence policy"*, and the Panic batch must not be made "more
advanced" by attaching a camera to it.

What is already **locked** by the protocol and is therefore no longer a proposal:

| Item | Locked value | Source |
|---|---|---|
| Audio | **No audio/microphone recording is approved at all** | §21 |
| Continuous camera | **Forbidden** | §21 |
| Continuous location | **Forbidden** | §22 |
| Location timing | Only *after* a confirmed event, minimum required precision, minimum retention, clear disclosure, explicit permission, encrypted storage, never hidden background collection | §22 |
| Evidence trigger | `Signal ≠ Evidence`; only a **confirmed** event produces the **minimum relevant** evidence | §26 |
| Evidence protection | AES-256-GCM + Android Keystore + AAD, files outside Room with metadata references | §26 |
| Retention | Evidence 30 days, event metadata 90 days, quarantine 30 days — **do not silently change** | §29 |
| Export | **Disabled** until separately approved | §29 |
| Panic batch | **No location, no camera** | §22: *"Do not add location to the Panic batch unless actually required"* |

Still genuinely open at Batch H: capture duration, photo count, and location precision class.
Those stay `PROPOSED — REQUIRES APPROVAL` and I will re-ask them **at Batch H**, not before.
They do not block anything.

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
| Privacy policy | Required. Must state: on-device only, no cloud, no analytics, what is collected and when. **Ad clause is on hold:** the earlier "AdMob authorized" note is contradicted by protocol §30 — see the CONFLICT box under D.2. Until you confirm, the policy text I write will say **no ads, no trackers**, because that is what the code will actually contain. Publishing to your own GitHub is pre-authorized. |
| Consent screen | Required, before any permission or capture. Plain language, Arabic + English. |
| Location permission text | Must say *why* and *when*, not just "for functionality". |
| Analytics/crash reporting | **None.** Adding them would be a §14 policy change, and §30 bars analytics explicitly. |

**Your answer:** ✅ **ANSWERED — Option "incremental consent", with one item pulled forward
and one contradiction to confirm (§D.2 below).**

Protocol §23 sets the rule directly: *"Google Play's current policy requires sensitive
permissions/APIs to be necessary for current promoted functionality and requested
incrementally with appropriate explanation. Do not request permissions for unimplemented
future features."* So:

- **No permission bundle during onboarding.** Camera, location and phone state must **not** be
  requested up front — §23 names that exact pattern as forbidden.
- Each permission is requested **by the feature that needs it, when it needs it**:
  `POST_NOTIFICATIONS` in Batch E, whichever background/guard permission Batch F actually
  requires, camera/location only at Batch H if §21/§22 conditions are met.
- **Consent before capture stays required.** §31 forbids showing "Evidence captured",
  "Location captured" or "Alert sent" unless real state proves it — which makes the consent
  screen and the reason strings a correctness requirement, not a legal formality.
- Retention/export/redaction policy detail is deferred to Batch H exactly as §38 orders it;
  the *principles* (minimum evidence, no audio, no continuous anything) are already locked in
  Q-C5 above and are not up for re-litigation.

One contradiction sits underneath this question and is *not* resolved by it: the earlier
"AdMob authorized" answer versus protocol §30. See the **CONFLICT** box under D.2 — it is the
only item in this whole register I am not proceeding on.

---

## Q-C8 — When may an old evidence key actually be destroyed? (NEW, raised by §10)

**OPEN-NONBLOCKING** — Batch B proceeds without the answer; only the last step is held.

**The situation:** §9 and §10 give Batch B a retirement lifecycle:

```text
all eligible evidence identified → all migrated → every migrated item verified
→ zero remaining references to the old key version → old key marked RETIRED
→ only then consider irreversible destruction
```

Everything up to `RETIRED` is an engineering state machine and is approved. The final arrow is
not: §10 says in as many words **"If the final destruction policy is not already explicitly
approved, implement the retirement state but do NOT invent a destructive policy"**, and the
existing decision record only covers destruction of the **whole** evidence key on confirmed
factory reset (§5.7, amendment A-2) — which is a different event from retiring one **version**
after a successful rotation.

| Option | Rule | Consequence |
|---|---|---|
| **1 (recommended)** | Automatic destruction once **all** four preconditions hold: every eligible item migrated and verified, zero references to the version, no rotation in flight, and the item-level check re-confirmed at the moment of deletion. | Safest key hygiene; matches §10's ordering; the only risk is a bug in the reference check, which is why Batch B builds and device-tests the check *before* the delete exists. |
| 2 | Retire, and leave destruction behind an explicit user action in Settings (auditable, never automatic). | Maximum safety, weaker hygiene — old keys can accumulate in the Keystore indefinitely. |
| 3 | Retire only; destruction never implemented. | Keystore aliases accumulate; §2.5's "fail closed" model is unaffected but the "old keys do not linger" property is lost. |

**Batch B's behaviour if you answer nothing:** options 1's *preconditions* get implemented and
tested, the retirement state gets recorded, and **no delete call exists**. That is the
non-inventive reading of §10, and it is reversible — a later batch can add the trigger once you
pick.

**Your answer:** _____________

---

# D. DEFERRED QUESTIONS — answer when that phase is reached

These do not block the next batch. They are listed so nothing is forgotten.

## D.1 Phase 5 — local response

| ID | Question | Default if you never answer |
|---|---|---|
| Q-D1 | What is the local response when an event is confirmed? (sound, vibration, full-screen alert) | **Nothing** — record only |
| Q-D2 | Who is notified? (SMS to a trusted contact? WhatsApp? Telegram?) | **Nobody outside the device** — the app shows the event in-app only |
| Q-D3 | Are recipients configurable by the user? | **No** — deferred with Q-D2 |
| Q-D4 | Retry policy if a notification fails | Deferred with Q-D2 |

**Updated by the protocol (2026-09-29).** Q-D1 and Q-D2 are no longer fully open: §16 approves
**real on-device Android notifications** as Batch E, immediately after the event producer
exists, with `POST_NOTIFICATIONS` requested only when that feature genuinely needs it and the
granted / denied / disabled-in-Settings outcomes handled honestly (§16: *"Do not show 'alert
sent' if notification permission is unavailable"*). What remains genuinely open is the *shape*
of the local response (sound? vibration? full-screen?) — §8 of the roadmap leaves "local
response, lock-screen disclosure, deduplication, and failure semantics" unresolved, so I will
ask when Batch E reaches that decision. Q-D2/Q-D3 stay **closed at "nobody outside the
device"**: §30 defers SMS, email, WhatsApp and Telegram providers, so recipients remain out of
scope until G5 is un-deferred by a new approval.

## D.2 Phase 6 — external providers

| ID | Question | Status |
|---|---|---|
| Q-D5 | Is billing implemented? (Premium screen is a stub with two dead buttons) | **⚠️ CONFLICT — see box below.** Earlier answer (2026-09-29): build AdMob *and* IAP despite the G0-05 freeze. Protocol §30 now says no billing during these batches. |
| Q-D6 | Is a cloud backup / account ever wanted? | **No** — local-only app. Reinforced by §30 ("Remain device-first… Do NOT add… cloud backend, account system, remote database"). |
| Q-D7 | Ads / analytics | **⚠️ CONFLICT for ads; settled for analytics.** §30 bars ads and analytics explicitly. Analytics: **none**, agreed on both sides — closed. Ads: needs the one-line confirmation below. |

> ### ⚠️ CONFLICT — the only item I am stopping on
>
> Two of your own instructions disagree, and §37 puts the difference inside the boundary where
> I must not choose for you (*"Stop only if implementation requires changing… data-sharing
> model… distribution strategy"*).
>
> | Source | Says |
> |---|---|
> | 2026-09-29 store-launch decision (four apps shipping on Google Play) | *"أنشئ كلاهما رغم التجميد"* — create both AdMob and Play Billing even while G0-05 is frozen; the store listing and monetization setup are being prepared in parallel |
> | Final Master Execution Protocol §30 | *"Do NOT add: … billing, ads, analytics"* during these core batches; §38 defers external services to Batch K/L territory |
>
> **What I am doing meanwhile (safe under both readings):** nothing is blocked. Batches B–J
> need no ads and no billing, so I proceed with them at full speed. The Premium screen's two
> dead buttons stay dead — §31 and §32 forbid wiring a payment flow that does not exist, so
> whichever way you answer, the current honest-disabled state is correct today.
>
> **Reply with one line to close it:**
> - **`§30`** → no ads, no billing anywhere in this codebase; the Play monetization prep is
>   parked until after the security audit, and the privacy policy keeps the "no ads, no
>   trackers" wording. *(My recommendation: it is the conservative reading, it is the newer
>   instruction, and adding a third-party SDK later is cheap while removing one from a
>   shipped, audited security app is not.)*
> - **`store-launch`** → AdMob + Billing stay authorized; I then treat them as a separate
>   track after Batch D, and the privacy policy, data-safety form and consent flow must all
>   disclose ad identifiers — which directly weakens the "zero permissions / nothing leaves the
>   device" store copy that is already live.
>
> Verified current state, so the answer is grounded: `app/build.gradle.kts` and
> `gradle/libs.versions.toml` contain **no** AdMob or Play Billing dependency;
> `AndroidManifest.xml` declares **zero** `uses-permission`; the Premium buttons are still wired
> to the deferred snackbar. Nothing has been built either way, so this decision costs nothing
> to make now and a great deal to make late.

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
| 2026-09-29 | `ROADMAP.md` "Required next batch" still described AAD binding as upcoming, and several gate rows said "no device available" | Replaced with the actual tip (`4d-persisted-event-reader`) and measured device results — see `docs/reports/batch-A-roadmap-reconciliation.md` | — |
| 2026-09-29 | Roadmap implied G4's rotation work was complete | The rotation **algorithm** is device-verified; the rotation **trigger** does not exist (`rotationDue()` has zero production callers) and there is **no retirement code at all** | — |
| 2026-09-29 | Register said "no ads" / "no billing" as settled | Superseded 2026-09-29 by the store-launch decision, then re-opened by protocol §30 — now recorded as an explicit CONFLICT in D.2 rather than a default | — |
| 2026-09-29 | Register treated "zero permissions" as a permanent promise | It is pre-launch copy that Batch E legitimately invalidates; §23's incremental rule is the binding constraint | — |

**Common corrections that are already recorded and do not need repeating:**

- Batch `4d-aad-binding` originally closed without a §16 report and with a placeholder
  `assertTrue(true)` device test. Both were fixed and documented in
  `docs/reports/batch-4d-key-rotation.md`.
- `gh run watch` has three times reported a false failure caused by flaky network, not by a
  failing workflow. Only the run object itself (`gh run view --json conclusion`) is trusted.
- `adb pull /sdcard/...` needs `//sdcard/...` under Git Bash.

---

# F. EXECUTION ORDER (AUTHORITATIVE)

The order below is not my proposal any more — it is §38 of the owner's Final Master Execution
Protocol, transcribed verbatim so a future session cannot invent a different one. Batch A is
this reconciliation; it is in progress now.

| Batch | Name | Gate status |
|---|---|---|
| **A** | Reconcile roadmap / questions / decisions against the actual repository | in progress — this commit |
| **B** | Close real G4 lifecycle gaps: rotation **trigger**, old-key **retirement**, plus any gap found by trace | approved, unblocked |
| **C** | **Panic Guard end-to-end** (UI → signal → confirm → EventId → SecurityEvent → pipeline → Room → reload → History → Detail) | approved (§12–§13, §15) |
| **D** | Flip `StorageRuntimeApproval.approved = true` **inside the same verified Panic batch** | approved (§11, §14) — never a standalone commit |
| **E** | Real notification path (`POST_NOTIFICATIONS`, granted/denied/disabled handled honestly) | approved (§16) |
| **F** | Background / active-guard execution (legitimate mechanism, no 24/7 promise) | approved (§17) |
| **G** | **Motion Guard** (3 s strong movement, or two suspicious movements in 10 s) | approved (§18) |
| **H** | Evidence / privacy / permission implementation | approved in principle, parameters open (Q-C5) |
| **I** | **SIM Guard** — feasibility first, implement only if technically and policy feasible | §19 |
| **J** | **Lock Guard** feasibility + **current** official Play policy review, cited exactly | §24–§25, deferred by the owner |
| **K** | Full security / privacy / device audit (§15's seven gates, G6) | required before any production or security claim |
| **L** | Release hardening | last |

**Standing prohibitions across every batch** (the owner wrote these as rules, so they are
repeated here rather than left to memory): no battery-percentage security events (§20), no
continuous camera (§21), no continuous location (§22), no permission bundles (§23), no cloud /
accounts / analytics / ads / messaging providers (§30), no fabricated UI state (§31), no dead
buttons left misleading once the first detector works (§32), no "CI pending" when no run exists
(§33), no device PASS without actual execution (§34).

**§36 is the default behaviour:** after a batch passes, update the roadmap, take the next
dependency-free approved batch, and continue without asking. I stop only for a genuinely new
policy decision inside §37's boundary (security/privacy policy, retention, export, cloud
architecture, cryptographic algorithm, key-protection model, meaning of a guard, severity,
distribution strategy, data-sharing model). Ordinary engineering choices are mine to make and
must simply be documented.

Under that rule there are exactly **two** items I am holding: the ad/billing conflict in D.2,
and the retired-key destruction policy in Q-C8. Neither blocks Batch B or Batch C — B ships the
retirement state without the delete, and no batch before K needs ads.
