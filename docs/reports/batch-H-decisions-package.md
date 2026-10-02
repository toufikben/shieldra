# Batch H — the decisions package (decisions only; no camera or location code exists after this commit)

Amendment **A-14** fixes what this batch is and is not: *"ثم حزمة الدفعة H **للقرارات فقط**"* and
*"**ممنوع على agent اختراع قرارات H**: مدة التقاط الكاميرا، عدد الصور، دقة الموقع، مدة احتفاظ الموقع،
وما إذا كانت الكاميرا مسموحة أصلًا"*. The owner's protocol §6 repeats it: *"Do not invent owner policy.
Present only the decisions that actually block implementation."*

So this file contains **no implementation, no stub, no permission, no manifest line and no gate
relaxation**. Every open item below is marked `PROPOSED — REQUIRES OWNER ANSWER`, and where the
existing register already carries a proposed number (Q-C5) that number is quoted as *the register's*,
not adopted here.

---

## 1. WHAT H DOES NOT NEED TO BUILD — the substrate is already shipped and device-verified

This matters for scoping: the expensive, security-critical half of "evidence" is done, so H's real
cost is capture, permissions, consent and wording — not crypto.

| Piece | State today | Where |
|---|---|---|
| Encrypted evidence files, AES-256-GCM + Keystore + AAD, outside Room with metadata references | **SHIPPED**, §5 slice closed and device-verified | `storage/EncryptedEvidenceFileStore.kt` (`write`/`read`/`delete`/`reconcile`/quarantine, atomic `.tmp`) |
| Event → evidence linkage | **SHIPPED** — `SecurityEvent.evidenceReferences`, persisted in a Room table carrying key + AAD versions | `domain/SecurityEventContracts.kt:37,62`, `storage/room/RoomEventRepository.kt:46-48,98,133` |
| Rotation metadata per evidence item | **SHIPPED** | `storage/room/RoomRotationMetadata.kt` |
| Retention (evidence 30 d / metadata 90 d / quarantine 30 d) | **LOCKED by §29**, policy implemented | `storage/EvidenceRetentionPolicy.kt` |
| Export | **LOCKED DISABLED** by §5.5 / §29 | `storage/EvidenceExportPolicy.kt` |
| Presentation slots | **EXIST and are honest**: `hasPhoto = false`, `LocationUiModel(kind = Unavailable)` are hardcoded today, which is exactly why §31 is satisfiable right now | `app/presentation/guard/PersistedEventReader.kt:60-61` |
| A running service that could host a post-confirmation action | **SHIPPED** (Motion, `specialUse`, user-started) | `detection/MotionMonitoringService.kt` |

**One structural gap worth pricing up front:** `EvidenceReference` is `data class EvidenceReference(val id: String)`
— it carries **no kind**. Once both a photo and a location exist, the event cannot tell them apart
without a schema change (see H-7).

---

## 2. THE DECISIONS THAT BLOCK IMPLEMENTATION

### H-1 — Is camera evidence allowed **at all**? `PROPOSED — REQUIRES OWNER ANSWER`

The gate that actually blocks everything else. §21 permits camera to be *considered* only after a
confirmed event and an explicit approved evidence policy; it does not approve it. Nothing in the
repository says yes. Until it does, H-2…H-12 are moot for camera and only the location branch is live.

**Consequence of yes:** `CAMERA` becomes the app's first runtime, user-visible, sensitive permission;
the Play listing's data-safety form gains Photos/Videos; the "no permissions until you need them"
promise (already owed after A-6) gains a clause about who is photographed.
**Consequence of no:** camera capture is dropped from the release scope and H shrinks to location +
consent wording, which is a materially smaller slice.

### H-2 — Which guards may capture? `PROPOSED — REQUIRES OWNER ANSWER`

Not in the register at all, and it blocks wiring more than any number does. Today two guards produce
confirmed events: **Panic** (user-pressed) and **Motion** (sensor-judged, threshold `4.6`, and the
RMX3910 record already contains false-positive-shaped conditions — a carried phone, a pocket, a table
bump).

- **Panic only** — the user asked for it; no photo is ever taken without a deliberate press. Smallest
  privacy surface, smallest Play argument.
- **Panic + Motion** — the theft case the listing advertises ("anti-theft"), but a *judged* signal
  starts capturing images of whoever happens to be near the phone, and §26's "minimum relevant
  evidence" has to be argued against that.

This is a meaning-of-a-guard decision (§37), so it is the owner's and I am not proposing a default.

### H-3 — What "capture" physically means: **video or stills**, and for how long? `PROPOSED — REQUIRES OWNER ANSWER`

Q-C5 records `Capture duration — 15 seconds` and `Max photos — 3` side by side, and those are two
different products:

| Reading | API | Consequences |
|---|---|---|
| A **15-second video** (silent — §21 bars audio) | `VideoCapture`, a session that must survive 15 s | Needs a lifetime decision (does the service hold the camera for 15 s? then the FGS type question in H-5 becomes urgent), larger files, and a Play data-safety entry for video |
| **3 stills** over ~15 s | `ImageCapture` / `TakePicture` | No session to keep alive, smaller quota, no video data type |

I am not choosing for the owner; the ambiguity itself blocks picking a camera API, so it has to be
answered before any H-4+ number means anything.

### H-4 — Location precision class: coarse or fine? `PROPOSED — REQUIRES OWNER ANSWER (register proposes coarse)`

Q-C5's proposal is *"Coarse (~1 km), captured once, at event time"*. Coarse is a defensible anti-theft
answer ("which neighbourhood") and a weak one ("which floor of which mall"). Fine is the opposite.
This is not a runtime toggle: it selects the manifest permission
(`ACCESS_COARSE_LOCATION` vs `ACCESS_FINE_LOCATION`), the Play sensitive-permission declaration, and
the data-safety row. Both are `while-in-use` permissions.

### H-5 — **Where** the location comes from, and how fresh it may be: the decision that decides whether SHIELDRA ever needs background location `PROPOSED — REQUIRES OWNER ANSWER`

The register's phrase "captured once, at event time" hides the whole architectural question, because
"at event time" can mean two very different things:

1. **Last known fix, read when the event is confirmed while the app is in a visible state.** No
   background location, no `location` foreground-service type, no extra Play argument. Cost: the fix
   may be stale or absent — which is honest only if the UI says so (today it hardcodes
   `LocationKind.Unavailable`, so the honest path already exists).
2. **A fresh fix obtained by the monitor after the phone moves** — i.e. location read while the app is
   not in the foreground. Android treats that as **background location**, which needs
   `ACCESS_BACKGROUND_LOCATION` (a separately Play-reviewed, high-scrutiny permission) and, for a
   service, the `location` foreground-service type — a type the G5 review explicitly **eliminated**
   because §22 forbids location collection. Choosing this path re-opens that elimination.

**Freshness rule needed either way:** the maximum age of an accepted fix, and what the UI says when
nothing is that fresh. A stale fix presented as "where it happened" is a §31 violation, so this number
is a correctness requirement, not a preference. My recommendation is to answer (1) with an explicit
age ceiling, because (2) is the single largest Play and privacy risk in this entire batch and buys
accuracy the app has no proven need for yet.

### H-6 — Location retention `PROPOSED — REQUIRES OWNER ANSWER`

Q-C5 proposes 30 days aligned with the evidence window. If location is stored as an evidence file it
inherits §29's 30 days automatically and there is nothing to decide; if it is stored as event metadata
it inherits the **90-day** metadata window instead. So H-6 is genuinely answered only after H-7 —
which is why the two are separate questions and why I am not collapsing them.

### H-7 — Where a location value lives: metadata, or an encrypted evidence file with a typed reference `PROPOSED — REQUIRES OWNER ANSWER`

This is the schema decision, and it is the one with a migration attached.

| Option | What it costs | What it means |
|---|---|---|
| **A. `SecurityEvent.metadata` key** (already a `Map<String, String>`) | No Room migration. **But** the value sits in the encrypted database as plain metadata, not under the per-item AAD-bound evidence key, and `group-c-contract-check.sh:33-34` forbids any Android location type in `domain/`, so it must be a framework-free value | Cheapest; weakest binding to the evidence guarantees §26 established |
| **B. An encrypted evidence file + a `kind` on `EvidenceReference`** | A new Room column (`event_evidence_refs.kind`) → a real migration, and the owner has locked **no destructive migration** | Consistent with §26 (a location is evidence, so it is encrypted, AAD-bound, rotatable and reconciled like a photo); makes H-6 fall out of §29 |
| **C. Both: file for coordinates, metadata for the display label** | Both costs | Probably over-engineered for one lat/long pair |

A lat/long pair is small enough that option A is tempting and option B is more honest to the policy
§26 already states. The choice is the owner's because it changes the schema and the migration path.

### H-8 — The consent gate and its exact wording `PROPOSED — REQUIRES OWNER ANSWER`

Q-C7 is **answered** — incremental consent, no bundle, consent before capture — so the *principle* is
locked and is not re-opened here. What blocks code is the text and the surface:

- the consent screen's actual EN + AR copy (what is captured, when, where it is stored, that it never
  leaves the phone, that export is disabled);
- the camera rationale string and the location rationale string — §23 and Play both require "why and
  when", not "for functionality";
- **where refusal leads.** Batch G settled the analogous question for notifications (A-18: monitoring
  fails closed because an invisible ongoing notification would break A-15(3)), and A-6 settled it for
  recording (a refused notification permission never touches Panic). Capture needs its own ruling:
  refused camera ⇒ the guard still records the event and the History row says
  "no photo — permission not granted", or the feature is honestly absent. That is a product claim
  decision, and §31 makes a wrong answer a defect rather than a cosmetic issue.

I will draft the copy once H-1/H-2/H-4/H-5 are answered; drafting it now would mean writing claims
about a capture behaviour that does not exist yet.

### H-9 — Redaction policy `PROPOSED — REQUIRES OWNER ANSWER`, and possibly **deferrable**

The owner's protocol lists redaction among H's decisions, so it is presented — but honestly: today
evidence never leaves the device (§29 export disabled, §30 no cloud, no sharing path of any kind), so
there is currently **no third party to redact for**. The decision that actually blocks is narrower:

- **Does SHIELDRA blur or mask anything at capture time?** (No known use while export is off.)
- **What happens the day export or sharing is approved?** That is the moment redaction becomes real,
  and it is a Batch L / post-K question.

My recommendation is to record redaction as *explicitly deferred with a reason*, not to invent a
redaction feature to satisfy a checklist item. If the owner disagrees, the smallest real version is a
retention-and-deletion surface ("delete this event's evidence now"), which does have a consumer: the
owner's own device.

### H-10 — Permission sequencing: which screen asks, and in what order `PROPOSED — REQUIRES OWNER ANSWER`

§23 forbids a bundle and requires each permission to be requested by the feature that needs it. The
concrete order still has to be fixed: consent screen → camera → location, or consent → location →
camera; whether both live behind one "Evidence" settings row (like today's Notifications row, which
Batch E proved works and Batch G proved the fail-closed path for); and whether onboarding mentions
evidence at all while the guards that would use it are unapproved. Given F2's finding that onboarding
must not promise a request it does not make, an onboarding mention is only allowed if the step really
raises the dialog.

### H-11 — What the store listing and Play forms may then say `PROPOSED — REQUIRES OWNER ANSWER`

Already a live obligation independent of camera: the listing still says the app ships with **zero
Android permissions**, which A-6 made false, and the honest sentence is "no permissions until you need
them" (`OWNER_QUESTIONS.md:89-98`). If H adds `CAMERA` and a location permission, that same listing
also gains: Play's **sensitive permissions** declarations, a **Data Safety** entry for Photos/Videos
and/or Location, and a Data-Safety answer about whether the data leaves the device (today: it does
not). This is a one-way door once the package is uploaded, so it needs the owner's line, not mine.

### H-12 — Storage ceiling per event and in total, and what happens when it is full `PROPOSED — REQUIRES OWNER ANSWER`

Nothing in the current store bounds bytes: `EncryptedEvidenceFileStore` writes, reconciles and
quarantines, and §29 bounds *age*, not *size*. So capture needs an answer for what happens when the
directory is full — refuse the capture and say so honestly, or evict the oldest evidence and say that
instead. Silently losing evidence is the worst of the three and is exactly the failure §6's failure
semantics were written to prevent. This is the lowest-ranked item here, but it does block shipping a
capture path, so it is listed rather than assumed.

---

## 3. THE GATE AND AUTHORITY CHANGES A-14 REQUIRES **BEFORE** ANY H CODE

A-14: *"**لا كود H قبل تعديل السلطة والأحكام نفسها** … ولا تُرخى البوابات ضمنيًا لتمرير كود."*
These are the exact assertions that fail the build the moment camera or location code appears, so the
owner's answer must name them and the amendment must move them **in the same commit as the code**, the
way A-19 did for the service:

| Gate | Current assertion | What H code would trip |
|---|---|---|
| `.github/scripts/group-c-contract-check.sh:24-25` | `check_absent "No camera, location collection, or network implementation"` for `CameraX\|ImageCapture\|FusedLocationProvider\|LocationManager\|SMTP\|OkHttp\|Retrofit\|Firebase\|BillingClient` across **all** of `app/src/main` | Any real camera or location implementation, anywhere in main source |
| `.github/scripts/group-c-contract-check.sh:21-22` | `check_absent "No Safe Zone, geofencing, or continuous GPS source"` | Only if H drifts into geofencing — **should stay exactly as it is** |
| `.github/scripts/group-c-contract-check.sh:33-34` | `check_absent "No Android API dependency in Domain"` for `android.location` (and `android.content/app/hardware`, `androidx.`) in `com/shieldra/domain` | A location type inside the domain contract → forces H-7's value to be framework-free |
| `.github/scripts/g4b-review-check.sh:330-334` | The manifest may declare **only** the A-6/A-19 named three: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | `CAMERA`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` |
| `.github/scripts/g4b-review-check.sh:420-422` | Exactly two `FOREGROUND_SERVICE*` permissions | A `location` or `camera` FGS type's companion permission |
| `.github/scripts/g4b-review-check.sh:542, 625, 750` | The G3 / G4-A / Q-G1 **test-only vehicles** must contain no `Camera`/`Location` token | H code must not be smuggled into the measurement harnesses |
| `.github/scripts/phase3-review-check.sh:35` | `CameraX\|ImageCapture\|FusedLocationProvider\|LocationManager\|…` absent from `app/src/main/kotlin/com/shieldra/app` | Presentation-layer capture |
| `.github/scripts/phase3-review-check.sh:45-52` | Manifest allowlist, same named three | Any new permission |

Plus the authority work the owner's own rules demand: a new amendment row in the decision register
(`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`, whose last row today is **A-19** at line 645)
recording the H decisions, and — if H-5 takes the background route — a correction to
`docs/reports/batch-G5-foreground-service-type-review.md`, whose `location` row was eliminated *because
§22 forbade collection*, not because the type does not fit.

---

## 4. WHAT IS ALREADY LOCKED AND IS NOT ON THE TABLE

Restating so no future session re-litigates it: no audio, ever (§21); no continuous camera (§21); no
continuous location and no hidden background collection (§22); evidence only after a **confirmed**
event, and only the **minimum relevant** evidence (§26); AES-256-GCM + Keystore + AAD, files outside
Room with metadata references (§26); retention 30/90/30 days, export disabled (§29, §5.5); no UI may
show `Evidence captured` / `Location captured` unless real state proves it (§31); no cloud, accounts,
analytics or ads (§30).

---

## 5. STATUS

| Item | Status |
|---|---|
| Camera/location/permission code written by this batch | **NONE** |
| Manifest, gates, authority amendments | **UNCHANGED** — still exactly the A-6/A-19 three permissions, still no capture anywhere |
| Decisions presented | **12** (H-1…H-12), all `PROPOSED — REQUIRES OWNER ANSWER` |
| Decisions I refuse to invent | whether the camera is allowed at all, which guards capture, video-vs-stills, precision, background-vs-last-fix, retention class, consent/refusal semantics, redaction scope, permission order, listing claims, storage-full behaviour |
| Blocking the next batch | **nothing** — Batch I (SIM feasibility) and Batch J (Lock feasibility + current official Play policy) are independent of H's answers, so per §6/§7 the sequence continues while these sit open |

Once the owner answers, the order is fixed by A-14: **record the amendment and move the named gates in
the same commit as the real capture code**, RED first, then CI, then the RMX3910 pass — never a
relaxation that precedes the feature it is meant to admit.
