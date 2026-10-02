# Batch I — SIM Guard feasibility (five dimensions, kept separate; no SIM code was written)

Protocol §38 orders **I = SIM Guard — feasibility first, implement only if technically and policy
feasible**. The owner's protocol asked for five things reported **separately**: API capability,
permission requirement, background feasibility, device/OEM limitations, Play implications. This file
delivers those five, then states the verdict and the decisions that are the owner's alone.

**Nothing was implemented.** No permission was declared, no telephony class was imported, no
amendment was recorded, no gate was relaxed. Decision **G1-03** carries the condition *"Android
API/device limitations must be documented before implementation"* — this document is that condition
being satisfied, not an approval to build.

## HOW THE FACTS WERE OBTAINED (so every claim is re-checkable)

Two kinds of evidence are used, and they are labelled differently throughout:

| Label | Source | Why it is trustworthy |
|---|---|---|
| **STUB** | `@RequiresPermission` / `@IntDef` annotations extracted from the installed SDK's own annotation tables: `%LOCALAPPDATA%\Android\Sdk\platforms\android-35\data\annotations.zip` and `android-36.1\data\annotations.zip` → `android/telephony/annotations.xml`; class inventory from `platforms\android-36\android.jar` via `javap -constants` and `unzip -l` | These are the platform's published declarations for the exact API levels this app compiles against (35) and the owner's device runs (36). CompileSdk 35, targetSdk 35, minSdk 26. |
| **DEVICE** | Read-only `adb` inspection of the owner's **RMX3910** (`IR7X5DJ7KZGEHMS8`, Android 16 / SDK 36) on 2026-10-02, plus `/system/framework/framework-res.apk` pulled from that phone and its `AndroidManifest.xml` decoded with build-tools 36.0.0 `aapt2 dump xmltree` | It is the OEM build SHIELDRA would actually ship onto — not AOSP, not an emulator. |
| **POLICY** | Text fetched from Google's own pages on 2026-10-02 (URLs in Sources) | Quoted verbatim where quoted. |
| **NOT RUN** | Anything that needs an installed SHIELDRA build with SIM code | Stated as such in §7. |

One method note, because it matters for trust: a documentation fetch of the `TelephonyManager`
reference page returned only page navigation, and the retrieval model then **invented** an answer
(`getSimState()` "requires READ_PHONE_STATE", `getSimSlotState()` "renamed to `getSimSlotIndex()`").
Both statements are false against the artifacts above. That fabricated answer is recorded here so a
future session does not repeat it, and **no claim in this file comes from a doc fetch that could not
be verified locally.**

---

## 1. WHAT THE REPOSITORY ALREADY DECIDED ABOUT SIM (not re-litigated here)

| Item | State | Where |
|---|---|---|
| SIM Guard rule | **APPROVED (pure)**: *"A confirmed SIM/subscription state change is an immediate confirmed event without repetition. Severity HIGH."* — conditioned on *"Android API/device limitations must be documented before implementation"* | `SHIELDRA_DECISIONS.md:22` (G1-03), `docs/PHASE4_DECISION_REGISTER.md:30` |
| Engine wiring | **EXISTS and is tested**: `GuardKind.SIM -> confirmed(signal, GuardSeverity.HIGH, identityProvider)`; `GuardKind.SIM -> observation is GuardObservation.SimSubscriptionChanged` | `domain/ProtectionStateEngine.kt:25`, `domain/GuardContracts.kt:89`, `app/src/test/.../ProtectionStateEngineTest.kt:185-199` |
| Observation payload | **EMPTY**: `data object SimSubscriptionChanged : GuardObservation` — no slot, no state, no before/after | `domain/GuardContracts.kt:46` |
| Persisted event type | **NONE for SIM.** `SecurityEventType` = `CONFIRMED_SECURITY_EVENT`, `PANIC_ACTIVATION`, `MOTION_CONFIRMED_EVENT` only, and the G5 protocol instruction was *"Do not invent new event types"* | `domain/SecurityEventContracts.kt:13-35` |
| Presentation | `EventType.SimChange`, the History `Sim` filter chip and a Sim icon **already exist**, but the mapper sends `CONFIRMED_SECURITY_EVENT -> EventType.Other` | `presentation/model/PresentationModels.kt:52`, `screen/history/HistoryFilters.kt:18`, `presentation/guard/PersistedEventReader.kt:76-80` |
| Retention/permission classification | **PROPOSED — NOT APPROVED**; permission **UNKNOWN** | `docs/PHASE4_EVIDENCE_MATRIX.md:10`, `docs/architecture/phase-4-decision-register.md:22` |
| Storage ban | *"SIM/subscription history"* must **not** be stored; only the minimum derived fact of an approved confirmed event | `docs/security/g4-data-inventory.md:117` |
| Dedup interaction | **A-18** (owner's Arabic): an active Lock or **SIM** event must **not** absorb a Motion burst — Motion reuse is Motion-scoped only | `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:643` |
| Prior capability verdict | `CONDITIONAL / UNKNOWN`; `PhoneStateListener`/`TelephonyCallback` are *"process-bound and not durable logs"* | `docs/PHASE4_ANDROID_CAPABILITY_MATRIX.md:11` |
| Prior device status | *"SIM/eSIM device **NOT ATTACHED**"* — **now stale**, see §5 | `docs/reports/batch-4b-device-execution-preflight.md:19` |

No amendment A-1…A-19 says anything about SIM. There is no `docs/capabilities/sim-guard.md`.

---

## 2. API CAPABILITY — what an app can actually observe

**SIM presence/state is readable, with no permission at all.** `getSimState()` and
`getSimState(int slotId)` carry **only** an `@IntDef` of the ten state constants —
**no `@RequiresPermission`** — in both the API 35 and API 36.1 annotation tables. **[STUB]**
Constants confirmed against `android.jar`: `SIM_STATE_ABSENT = 1`, `SIM_STATE_READY = 5` (plus
`UNKNOWN`, `PIN_REQUIRED`, `PUK_REQUIRED`, `NETWORK_LOCKED`, `NOT_READY`, `PERM_DISABLED`,
`CARD_IO_ERROR`, `CARD_RESTRICTED`). **[STUB]**

**Carrier-level identity is also permission-free.** `getSimOperator()`, `getSimOperatorName()`,
`getNetworkCountryIso()`, `getPhoneType()`, `getDataActivity()` have **no** permission annotation.
**[STUB]** A change in MCC/MNC is therefore observable without asking anything.

**Subscription facts need `READ_PHONE_STATE`.** `SubscriptionManager.getActiveSubscriptionInfoList()`
and `getActiveSubscriptionInfoForSimSlotIndex(int)` are annotated `RequiresPermission`
`android.permission.READ_PHONE_STATE`; `TelephonyManager.getLine1Number()`,
`getSubscriptionId(PhoneAccountHandle)`, `TelephonyCallback.CallStateListener.onCallStateChanged(int)`
and the cell/service listeners likewise. **[STUB]**

**Card identity is unreachable for a Play app.** `getSimSerialNumber()` (**ICCID**), `getSubscriberId()`
(**IMSI**), `getImei()`, `getMeid()` and `getDeviceId()` are annotated
`READ_PRIVILEGED_PHONE_STATE` — a signature/privileged permission a store-distributed app cannot hold.
**[STUB]** Consequence, and it is the single most important sentence in this report:

> **"the SIM was swapped to a different card" cannot be proven by a third-party app.** It can only be
> *inferred* from weaker signals (state → `ABSENT`, or a carrier numeric change). Any wording that
> claims SIM-*swap* detection would be a §31 violation; SIM-*removal* detection is what is achievable.

**There is no SIM push callback.** `TelephonyCallback` exposes 21 nested listener interfaces on API 36
— `ActiveDataSubscriptionId, BarringInfo, CallDisconnectCause, CallForwardingIndicator, CallState,
CarrierNetwork, CellInfo, CellLocation, DataActivationState, DataActivity, DataConnectionState,
DisplayInfo, EmergencyNumberList, ImsCallDisconnectCause, MessageWaitingIndicator,
PhysicalChannelConfig, PreciseDataConnectionState, RegistrationFailed, ServiceState, SignalStrengths,
UserMobileDataState` — and **none of them is a SIM-state listener**. `PhoneStateListener` has **no**
`LISTEN_SIM*` constant on API 36. **[STUB]** So the only *event* route is the broadcast, and the only
*quiet* route is reading state.

**The broadcast exists and this phone emits it.** **[DEVICE]** `dumpsys activity broadcasts history`:

```
#754: act=android.intent.action.SIM_STATE_CHANGED flg=0x15000010 xflg=0x4 (has extras)
  enq=2026-10-02 05:00:43.909 disp=2026-10-02 05:00:43.909 fin=2026-10-02 05:00:43.910
  extras: Bundle[{android.telephony.extra.SUBSCRIPTION_INDEX=28,
                 android.telephony.extra.SLOT_INDEX=0, phoneName=Phone, reason=null,
                 ss=LOADED, phone=0, subscription=28}]
```

Decoded against the platform's own constants: `0x15000010` = `FLAG_RECEIVER_FOREGROUND`
(0x10000000) + `FLAG_EXCLUDE_STOPPED_PACKAGES` (0x10) + a non-public platform bit (0x04000000).
Two things follow: the extras carry the **slot index** and a state string (`ss=LOADED`), and
**`FLAG_EXCLUDE_STOPPED_PACKAGES` means a force-stopped SHIELDRA is excluded from the broadcast by
design** — an app the user swiped away and that was never restarted receives nothing. **[STUB+DEVICE]**

**The action cannot be spoofed.** In the framework manifest of this very phone
(`/system/framework/framework-res.apk`, `AndroidManifest.xml` line 759):
`E: protected-broadcast — android:name="android.intent.action.SIM_STATE_CHANGED"` with **no
`android:permission` attribute**. **[DEVICE]** It is a protected broadcast (only the system can send
it), and the platform attaches **no receiver permission** to it on this build — consistent with the
stub finding that SIM state needs no permission.

---

## 3. PERMISSION REQUIREMENT — three tiers, measured on the owner's build

`dumpsys package permissions` on the RMX3910 **[DEVICE]**:

| Permission | Protection level on this build | What it buys | Needed for SIM *removal*? |
|---|---|---|---|
| *(none)* | — | `getSimState()`, `getSimState(slot)`, `getSimOperator()`, `getSimOperatorName()`, `getNetworkCountryIso()` | **Yes — nothing else is required** |
| `android.permission.READ_PHONE_STATE` | **`prot=dangerous`** (runtime dialog, Play-sensitive) | subscription list / slot mapping (`SubscriptionManager`), call & cell listeners, line number | **No** |
| `android.permission.READ_BASIC_PHONE_STATE` | **`prot=normal`** (install-time, no dialog) | exists on this build; the stub tables do **not** list any SIM-state getter among the APIs it unlocks | **No** |
| `android.permission.READ_PRIVILEGED_PHONE_STATE` | signature/privileged | ICCID / IMSI / IMEI — **unreachable** | **Impossible** |
| `android.permission.RECEIVE_BOOT_COMPLETED` | **`prot=normal`** | restart-after-reboot | **No** — and barred by **A-10** anyway |

So the honest summary is short: **the achievable SIM guard needs no new permission.** `READ_PHONE_STATE`
appears only if the owner wants *slot/subscription identity* rather than *presence*, and it is exactly
the permission the store listing currently promises the app does not have. **Nothing here authorizes
asking for it** — §23 requires a permission to be necessary for the functionality actually promoted.

---

## 4. BACKGROUND FEASIBILITY — three routes, and what each costs against existing decisions

| Route | Mechanism | Permission | Verdict inside this repository's rules |
|---|---|---|---|
| **A — poll from the running guard** | read `getSimState()` on a cadence from the **already-shipped** `MotionMonitoringService` while the user keeps protection on, feed the value to `DefaultProtectionStateEngine` | none | **Feasible with no permission, no manifest change, no new gate movement** — one `<service>` already exists and is allowed (`g4b-review-check.sh:409-418` caps it at exactly one). **But** it inherits the measured dark-screen starvation in §5, and it adds a *timer-driven* read: the G5 rule *"Do not add polling. Do not add a timer."* was scoped to the event-refresh fix, so extending the guard's sampling loop is a **new architecture decision**, not an obvious fix. |
| **B — runtime receiver inside the service** | `context.registerReceiver` for `SIM_STATE_CHANGED` while the service lives | none on this build | Technically the cleanest (event-driven, no polling, no permission). Blocked today by nothing except that it is new code in `detection/`, where the gates forbid building events (`group-c-contract-check.sh:27-29`) and forbid `startForeground`/FGS tokens outside three named files (`g4b-review-check.sh:695-696`). Same dark-screen caveat as A: a starved process is a dead receiver. |
| **C — manifest receiver (+ boot)** | `<receiver android:exported="true">` for the action, optionally re-armed by `BOOT_COMPLETED` | none for the action itself | **The route this repository already rejected.** A-10 refuses `RECEIVE_BOOT_COMPLETED`/`BOOT_COMPLETED` outright (`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:630`), and `g4b-review-check.sh:505-507, 577-581, 644-648` fail the build on those tokens. Independently, Google's implicit-broadcast exceptions page **does not list** `SIM_STATE_CHANGED` among the actions a manifest receiver may still get when targeting API 26+ **[POLICY — not yet proven on this device, see §7]**, and the measured `FLAG_EXCLUDE_STOPPED_PACKAGES` bit kills the stopped-app case regardless. |

Cross-cutting, already-owned constraints: **WorkManager is prohibited as a real-time detector**
(protocol §17; enforced at `g4b-review-check.sh:423-428`), and **Q-C6 is still unanswered**
(`docs/OWNER_QUESTIONS.md:421-445`, "Your answer: _____________") — *"Does an alert need to work when
the app is closed?"* For Motion the owner answered by accepting a user-started foreground service.
For SIM that question is **decisive rather than advisory**: routes A and B only observe while a
user-started guard is running; route C is the only one that could observe after a force-stop, and it is
the route A-10 refuses. This file does not answer Q-C6.

---

## 5. DEVICE / OEM LIMITATIONS — measured on RMX3910, and the correction it forces

**The phone is a working dual-SIM telephony device.** `getprop` **[DEVICE]**:
`persist.radio.multisim.config = dsds`, `ro.boot.opt_sim_count = 2`. Live network state
(`dumpsys telephony.registry`) **[DEVICE]**: `mVoiceRegState=0(IN_SERVICE)`, LTE,
`mOperatorAlphaLong=Djezzy`, `mMcc=603 mMnc=02`, CS+PS `registrationState=HOME`,
`availableServices=[VOICE,SMS,VIDEO]` / `[DATA,MMS]`; the broadcast record shows
`SUBSCRIPTION_INDEX=28`, `SLOT_INDEX=0`, `ss=LOADED`.

This **supersedes** `docs/reports/batch-4b-device-execution-preflight.md:19` ("SIM/eSIM device
**NOT ATTACHED**"), and it changes the device test matrix: profile **J** (single physical SIM) and
profile **K** (dual-SIM / multi-slot) are both satisfiable **on the owner's own phone**, which
`docs/PHASE4_DEVICE_TEST_MATRIX.md:24-27,36-38` said required an attached cellular device.
`docs/PHASE4_GUARD_RULE_PROPOSAL.md:28`'s *"multi-SIM/eSIM behavior UNKNOWN"* is now half-answered by
measurement: the device is **DSDS with two slots**, so `getSimState(slotId)` per-slot reads are the
relevant API and "one SIM removed from slot 1 while slot 2 stays live" is a real, reproducible case —
not a hypothetical. What remains unknown is **eSIM/MEP** behaviour, which this build's properties do
not establish. **[DEVICE]**

**The dark-screen starvation is the binding limit, and it is already measured.** From
`docs/reports/batch-G4A-background-sensor-measurement.md:102-105` (campaign-2, genuinely dark):

```
C 3  TIMEOUT_NO_DATA  never returned. 221 evictions. Observer: 532 s, +2/+1 ticks.
D 1  TIMEOUT_NO_DATA  never returned. 520 evictions. Observer: 1128 s, +2/+0 ticks.
D 2  TIMEOUT_NO_DATA  never returned. 521 evictions. Observer: 1127 s, +17/+5 ticks.
```

A process that receives **+2 utime ticks over 1128 s** cannot poll a telephony getter on any cadence,
and cannot run a receiver's `onReceive`. That is a Realme/OEM property, not an app bug, and it applies
identically to routes A and B. **Any SIM guard built on the running-guard routes can therefore only
claim: "observed while the guard was running and the phone was responsive."** It cannot claim
protection while the stolen phone lies dark — and §31/protocol forbid claiming what the state does not
prove.

**One thing no software measurement can substitute:** proving a SIM *transition* requires physically
ejecting the tray. That is the owner's hand, not an agent's; §7 lists exactly what to capture when the
owner chooses to do it.

---

## 6. PLAY IMPLICATIONS — quoted, not paraphrased

From Google Play's "Declare permissions for your app" (fetched 2026-10-02) **[POLICY]**:

> *"high-risk or sensitive permissions … (for example, SMS or Call Log), you may be required to complete
> the Permissions Declaration Form and receive approval from Google Play."*

> *"Whenever your app requests any new permissions, you will be required to use the UI to complete a
> revised Permissions Declaration Form specifically addressing the newly requested permissions."*

> *"When you complete the Permissions Declaration Form and roll out your release, your app will be
> subject to an extended review by the Google Play team. Your request may require up to several weeks
> to process."*

Reading those three against §3: **route A/B ship no new permission, so they trigger no new
declaration.** Adding `READ_PHONE_STATE` (only needed for slot/subscription identity, not for
removal) would put SHIELDRA into the declaration + extended-review path, and would contradict the
listing's current "**zero Android permissions**" sentence — already false since A-6/A-19 and already
flagged for correction in `docs/OWNER_QUESTIONS.md:89-98`. **This file does not decide whether
`READ_PHONE_STATE` is worth that trade; that is Q-I-2.**

Second Play-side fact, independent of permission choice and found while reading the shipped strings:
`premium_free_summary` in **both** locales (`app/src/main/res/values/strings.xml:209`,
`values-ar/strings.xml:209`) advertises *"Free includes: Lock Guard · Motion Guard · SIM Guard · Panic ·
Email delivery."* Today **only Motion monitors**; Lock and SIM have no detector, and email delivery is
barred outright by protocol §30. That is one sentence making **four** claims the code cannot prove
(§31). It is recorded as a finding, **not** fixed here, because the free-tier wording is a product-claim
decision (Q-I-6) and this batch is feasibility-only.

---

## 7. NOT RUN — what remains unproven

| Item | Status | What would prove it |
|---|---|---|
| Real `SIM_STATE_CHANGED` delivery with the tray ejected (state string, slot index, ordering, whether `ABSENT` precedes or follows a subscription change) | **NOT RUN** — needs the owner to eject the SIM | One instrumented observation on the RMX3910, in the A-13 measurement-only pattern |
| Whether a **manifest** receiver on this API-36 Realme build actually receives `SIM_STATE_CHANGED` despite the Oreo implicit-broadcast restriction | **NOT RUN** (docs say the action is not exempt; the phone has not been tested) | Same vehicle, declaring a test receiver — and an amendment first, per A-14's "no implicit relaxation" |
| Per-slot reads on the second slot (`getSimState(1)`) with slot 2 empty vs populated | **NOT RUN** | Needs a second SIM in the tray |
| eSIM / MEP behaviour | **NOT RUN** — not established by anything measured here | An eSIM-equipped device |
| Whether `getSimState()` returns `ABSENT` instantly or after a radio delay on this OEM build | **NOT RUN** | Timed observation during an eject |
| Doze/Force-Stop behaviour of a SIM poll specifically | **NOT RUN** as SIM evidence (the general starvation above is sensor-measured, not telephony-measured) | Extend the existing dark-window observer |

None of these can be produced without **installing SIM code**, which requires the owner's decisions
below and an amendment that moves the named gates in the same commit as the code.

---

## 8. VERDICT (technical) — feasible / feasible-with-caveats / not feasible

| Capability | Verdict | Basis |
|---|---|---|
| Detect **SIM removal** (state → `ABSENT`) while the user-started guard is running | **FEASIBLE, zero new permissions** | §2 stubs + §3 device protection levels; the guard service already exists |
| Detect SIM removal **event-driven** rather than by polling | **FEASIBLE, zero new permissions, unproven delivery** | broadcast is emitted on this build and is protected with no receiver permission; `NOT RUN` for the app-side receipt |
| Detect SIM removal while the app is **closed/force-stopped** | **NOT FEASIBLE under this repository's current rules** | route C needs the manifest receiver + boot, barred by A-10 and by the measured `FLAG_EXCLUDE_STOPPED_PACKAGES`; Q-C6 unanswered |
| Detect a **SIM swap** (different card, same slot) | **NOT FEASIBLE as a fact** — only inferable via carrier change; card identity is privileged-only | §2 (`getSimSerialNumber` → `READ_PRIVILEGED_PHONE_STATE`) |
| Know **which slot/subscription** changed | **FEASIBLE only with `READ_PHONE_STATE`** (dangerous, Play-declared) | §2 + §3 |
| Multi-SIM slot handling | **FEASIBLE and now testable on the owner's phone** (DSDS, 2 slots) | §5 device properties |
| Any "SIM protection is active" claim after a dark screen | **NOT CLAIMABLE** | §5 measured starvation + §31 |

**The engineering gap that is *not* a policy question and would be fixed in implementation:** the
approved rule G1-03 speaks of a *"SIM/subscription state change"*, while
`GuardObservation.SimSubscriptionChanged` is a bare `data object` with **no state payload** — so today
the engine cannot distinguish *removal* from *insertion* from *PIN-locked*. Giving it the observed state
is a domain-contract change, which is ordinary engineering (no owner policy needed).

---

## 9. THE DECISIONS THAT BLOCK SIM IMPLEMENTATION — owner's alone, nothing invented here

| # | Decision | Why it is the owner's | Options as measured |
|---|---|---|---|
| **Q-I-1** | What does "SIM Guard" **mean**: *removal only*, or *removal + suspected swap*? | It sets the product claim. A swap claim is unprovable (§2); a removal claim is provable but narrower than the listing's "SIM Guard" wording | (a) removal only, zero permission; (b) removal + carrier-change proxy, still zero permission, with wording that says "the network/SIM changed", not "your card was swapped"; (c) subscription/slot facts, needs `READ_PHONE_STATE` |
| **Q-I-2** | Is **`READ_PHONE_STATE`** ever acceptable? | First runtime, sensitive, Play-declared permission in this app's history; triggers the declaration + extended-review path; contradicts the current listing sentence | yes / no / only behind an explicit opt-in with a stated reason |
| **Q-I-3** | Which persisted **event type** does a SIM event use? | `SecurityEventType` has no SIM value and the G5 rule said *do not invent new event types*; `CONFIRMED_SECURITY_EVENT` today renders as `EventType.Other`, so a SIM event would appear in History as **"Other"** even though a `Sim` filter chip and Sim icon already exist | (a) reuse `CONFIRMED_SECURITY_EVENT` and accept "Other"; (b) approve a `SIM_CONFIRMED_EVENT` type = domain + presentation + gate change (`g4b-review-check.sh:820-823` pins the current three); (c) drop the Sim chip/filter until it has a real type |
| **Q-I-4** | **Q-C6** must be answered for SIM: must detection work with the app closed? | It is the fork between a feasible zero-permission guard and an infeasible one (§4). It has been open since the G2 questions and is still blank | as already framed in Q-C6 |
| **Q-I-5** | **Multi-slot semantics** on this now-proven DSDS device: is removing slot 1 while slot 2 stays live a confirmed event, and does an insertion (a *returning* SIM) produce an event at all? | G1-03 says "state change"; it does not say which direction or which slot counts. Severity HIGH is approved; the trigger set is not | — |
| **Q-I-6** | The **`premium_free_summary`** claim (both locales) advertises Lock, SIM and Email delivery that do not exist | §31 makes this a defect, but the free-tier wording is a product-claim decision, and the G5 protocol's "only these four fixes" bar closed without touching it | — |

Also still open and shared with every future guard: **how a non-terminal event ever closes** (today one
open Motion event absorbs later confirmations; A-18 presumes an active SIM event can coexist, so the
same question applies to SIM).

**Gates a SIM implementation would trip — named now so the future amendment moves them in the same
commit as the code (A-14/A-19 pattern):** `g4b-review-check.sh:330-335` (permission set **equality** —
`READ_PHONE_STATE` fails the build immediately), `:409-418` (exactly one `<service>`), `:505-507` +
`:577-581` + `:644-648` (`BOOT_COMPLETED` tokens), `:695-696` (FGS/`startForeground` tokens outside the
three allowed files), `:780-781` (calibration numerals like `100L`/`500L` in *any* new file under
`detection/` or `runtime/` — a real trap for a polling interval constant), `:792-794` (`RequestPermission
must live in the Settings path only`), `:820-823` (the three-name History mapping pin, if Q-I-3 takes
option b), and `group-c-contract-check.sh:27-29` (no `SecurityEvent` construction in `detection/`).
Note what is **not** gated: no script anywhere greps `TelephonyManager`, `SubscriptionManager`,
`READ_PHONE_STATE`, `BroadcastReceiver` or `SIM` — the telephony ban is indirect, via the permission
allowlist and the boot/service scans. A **removal-only, poll-from-the-existing-service** design would
therefore trip **no gate at all**, which is precisely why Q-I-1…Q-I-4 must be answered *before* code
rather than discovered after it.

---

## 10. STATUS

| Item | Status |
|---|---|
| SIM/telephony code, permission, manifest line, receiver, service | **NONE WRITTEN** |
| Gates / authority amendments | **UNCHANGED** — last amendment is still **A-19** |
| Feasibility dimensions reported | **5 of 5**, separately (§2 API, §3 permission, §4 background, §5 device/OEM, §6 Play) |
| Evidence quality | platform stub tables (API 35 + 36.1) + `android.jar` inventory + read-only inspection of the owner's RMX3910 and its own framework manifest + quoted Play text |
| Corrections forced by measurement | `batch-4b-device-execution-preflight.md:19` is stale (device *is* attached, DSDS 2 slots); `PHASE4_GUARD_RULE_PROPOSAL.md:28` multi-SIM is now partly measured |
| Blocking the next batch | **nothing** — Batch J (Lock feasibility + current official Play/Android requirements) is independent, so per §7 the sequence continues |
| Next owner action | Q-I-1 … Q-I-6, with **Q-I-4 (Q-C6)** and **Q-I-1** first: they decide whether a SIM guard is buildable at all, and with which permission |

## SOURCES

Platform artifacts on this machine (re-checkable, no network needed):
- `%LOCALAPPDATA%\Android\Sdk\platforms\android-35\data\annotations.zip` → `android/telephony/annotations.xml`
- `%LOCALAPPDATA%\Android\Sdk\platforms\android-36.1\data\annotations.zip` → same
- `%LOCALAPPDATA%\Android\Sdk\platforms\android-36\android.jar` (`javap -constants`, `unzip -l`)
- `framework-res.apk` — pulled from RMX3910 with `adb pull $(adb shell pm path android | …)`, decoded with
  build-tools 36.0.0 `aapt2 dump xmltree …/framework-res.apk AndroidManifest.xml` (entry at manifest line
  759). The working copy under `%TEMP%` was deleted after reading; the two commands above reproduce it.
- `adb` on RMX3910 `IR7X5DJ7KZGEHMS8`: `dumpsys activity broadcasts history`,
  `dumpsys telephony.registry`, `dumpsys package permissions`, `pm list permissions`, `getprop`

Official pages read on 2026-10-02:
- Declare permissions for your app — <https://support.google.com/googleplay/android-developer/answer/9214102?hl=en>
- Implicit-broadcast exceptions (the page whose list **does not include** `SIM_STATE_CHANGED`; marked
  NOT RUN on-device in §7) — <https://developer.android.com/guide/components/broadcast-exceptions>
- TelephonyManager / TelephonyCallback reference (used only for navigation context; the reference fetch
  returned no usable body text, which is why §2 relies on the stub tables instead) —
  <https://developer.android.com/reference/android/telephony/TelephonyManager>,
  <https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener>
