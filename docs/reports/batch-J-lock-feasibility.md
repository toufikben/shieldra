# Batch J — Lock Guard Feasibility (Android observability, device-admin/Keyguard implications, current official Play policy)

**Status: FEASIBILITY DELIVERED. NO LOCK CODE WAS WRITTEN AND NO GATE WAS RELAXED.**
**Gate:** G4 remains **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS** for Motion only. Lock Guard
remains **blocked on the owner decisions at the end of this report**.
**Rule authority:** protocol §24 (five gates, in order), §25 ("do not treat old policy summaries as
authoritative, do not rely on community interpretation — cite the exact official source"), §38
Batch J, and amendment **A-14** (`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:635`) — which
applies to J exactly as it applied to H and I: **the agent does not invent owner policy, and no
gate is loosened implicitly to let code through.**

Every claim below carries one of these labels:
**SOURCE** (quoted from AOSP or an official Google page, with the exact location),
**DEVICE** (measured read-only on the owner's RMX3910 `IR7X5DJ7KZGEHMS8`, Android 16 / SDK 36),
**REPO** (read from this repository at `c3b2a4a`),
**NOT RUN** (not proven; nobody should repeat this report as if it were).

---

## 1. Bottom line

**Provable:** Android can tell a device administrator, in real time, that the user entered a wrong
device or profile challenge, with **zero new `<uses-permission>` entries**, with **no accessibility
service, no notification listener, no device-owner role, no MDM, and no foreground service**, and
the broadcast that carries it **cannot be forged by any other app**.

**Not provable:** that SHIELDRA will reliably hear it. The same platform that delivers the callback
refuses to deliver it to a force-stopped app, and the only standard fix — `BOOT_COMPLETED` — is
barred by **A-10** (`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:630`). So Lock Guard is
feasible **as an opportunistic signal, not as a guarantee**, and any wording that implies
"we will notice someone trying your PIN" would be a §31 violation on this device class.

**The decision that is not mine:** whether a consumer security app should ask a user to make it a
*device administrator* at all. That role is a system-level trust grant, Google documents it as an
**enterprise** API, and the current Play policy makes the two things it must never become very
concrete: **irreversible** and **un-removable**. See §5 and §6.

---

## 2. Gate 1 — Android observability feasibility

### 2.1 The action

`android.app.action.ACTION_PASSWORD_FAILED` — **SOURCE**, AOSP `frameworks/base` @ `refs/heads/main`,
`core/java/android/app/admin/DeviceAdminReceiver.java:148-163`:

```java
/**
 * Action sent to a device administrator when the user has entered an incorrect device
 * or profile challenge password.  You can at this point check the
 * number of failed password attempts there have been with
 * {@link DevicePolicyManager#getCurrentFailedPasswordAttempts
 * DevicePolicyManager.getCurrentFailedPasswordAttempts()}.  You will generally
 * handle this in {@link DeviceAdminReceiver#onPasswordFailed(Context, Intent, UserHandle)}.
 *
 * <p>The calling device admin must have requested
 * {@link DeviceAdminInfo#USES_POLICY_WATCH_LOGIN} to receive
 * this broadcast.
 */
@SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
@BroadcastBehavior(explicitOnly = true)
public static final String ACTION_PASSWORD_FAILED
        = "android.app.action.ACTION_PASSWORD_FAILED";
```

Two properties of that declaration matter more than anything else in this report:

- **`@BroadcastBehavior(explicitOnly = true)`** — the system delivers it only to a named component.
  **REPO consequence:** the Android 8.0 (API 26) implicit-broadcast ban does **not** apply, so a
  manifest-declared receiver is still a live delivery path. This is the opposite of Batch I's
  finding for `SIM_STATE_CHANGED`, which is implicit and therefore unreachable in the background
  (`docs/reports/batch-I-sim-feasibility.md`).
- **It is a `<protected-broadcast>`** — **SOURCE**, `core/res/AndroidManifest.xml:539` (same ref):
  `<protected-broadcast android:name="android.app.action.ACTION_PASSWORD_FAILED" />`. **DEVICE**,
  confirmed in this build's own `framework-res.apk` (see §8 for the reproducible commands). A
  non-system app that tries to broadcast this action is rejected by the platform.

**Therefore the Signal cannot be forged and cannot be self-generated.** This is precisely the
constraint `docs/capabilities/lock-guard.md` already imposes ("It must never synthesize a
failed-PIN Signal from app lifecycle events, screen on/off events, accessibility observations, or
guessed counters"), and it is now satisfied by the platform rather than only by our own discipline.

### 2.2 The callback

**SOURCE**, `DeviceAdminReceiver.java:697` (3-arg, current) and `:683` (2-arg, `@Deprecated`
"From O"):

```java
public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent,
        @NonNull UserHandle user) { onPasswordFailed(context, intent); }
```

`onReceive` dispatches `ACTION_PASSWORD_FAILED` to the 3-arg callback and passes
`Intent.EXTRA_USER` (`DeviceAdminReceiver.java:1190-1191`). **REPO:** `minSdk = 26`
(`app/build.gradle.kts:13`), so the non-deprecated overload is the one available everywhere SHIELDRA
runs; only the pre-O 2-arg form is deprecated, **the capability itself is not**.

### 2.3 What must be requested — and what must not

**SOURCE**, `core/java/android/app/admin/DeviceAdminInfo.java:74-83`:

```java
/**
 * A type of policy that this device admin can use: able to watch login
 * attempts from the user, via {@link DeviceAdminReceiver#ACTION_PASSWORD_FAILED},
 * {@link DeviceAdminReceiver#ACTION_PASSWORD_SUCCEEDED}, and
 * {@link DevicePolicyManager#getCurrentFailedPasswordAttempts}.
 *
 * <p>To control this policy, the device admin must have a "watch-login"
 * tag in the "uses-policies" section of its meta-data.
 */
public static final int USES_POLICY_WATCH_LOGIN = 1;
```

Compare the policy directly above it, `USES_POLICY_LIMIT_PASSWORD` (`:62-72`), which says "the
device admin **must be a device owner or profile owner**". `USES_POLICY_WATCH_LOGIN` carries **no
such sentence**. **This is the single most important line in the feasibility answer:** watching
failed challenges needs only an ordinary, user-consented device admin that declares `watch-login`.
It does **not** need device owner, profile owner, `dpm set-device-owner`, an MDM, or a managed
profile.

### 2.4 The counter

**SOURCE**, `core/java/android/app/admin/DevicePolicyManager.java:6096-6108` (main):

```
 * The calling device admin must have requested {@link DeviceAdminInfo#USES_POLICY_WATCH_LOGIN}
 * to be able to call this method; if it has not, a security exception will be thrown.
 ...
 * @throws SecurityException if the calling application does not own an active administrator
 *             that uses {@link DeviceAdminInfo#USES_POLICY_WATCH_LOGIN}
 */
@RequiresFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
@RequiresPermission(value = MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS, conditional = true)
public int getCurrentFailedPasswordAttempts() {
```

`MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS` is marked **`conditional = true`**: for a `watch-login`
device admin the condition is the policy declaration, not a runtime permission the app requests.
**No new `<uses-permission>` is required** — see §7 for why that is load-bearing for our gates.

### 2.5 Who is allowed to report a failure

**SOURCE**, `services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java:8389-8395`
(24 901 lines; re-fetched and line-count-verified against `refs/heads/main`):

```java
@Override
public void reportFailedPasswordAttempt(int userHandle, boolean parent) {
    Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

    final CallerIdentity caller = getCallerIdentity();
    Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
    Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));
```

and the delivery, same file `:3111-3146`:

```java
boolean sendAdminCommandLocked(ActiveAdmin admin, String action, Bundle adminExtras,
        BroadcastReceiver result, boolean inForeground) {
    Intent intent = new Intent(action);
    intent.setComponent(admin.info.getComponent());
    ...
    if (mInjector.getPackageManager().queryBroadcastReceiversAsUser(
            intent, PackageManager.MATCH_DEBUG_TRIAGED_MISSING,
            admin.getUserHandle()).isEmpty()) {
        return false;
    }
    ...
    mContext.sendBroadcastAsUser(intent, admin.getUserHandle(), null, options.toBundle());
```

Verified facts from this code:
- the intent is **explicit to our component** (`setComponent`) — §2.1's `explicitOnly`;
- the increment `policy.mFailedPasswordAttempts++` happens at `:8408` and the broadcast is sent at
  `:8420-8422`, i.e. **the counter is already updated when the callback runs**;
- the counter is reset by the system on success (`:8346`, `:8493`), so
  `getCurrentFailedPasswordAttempts()` is **not** a durable log. The approved domain engine counts
  its own Signals (`docs/capabilities/lock-guard.md`), and that design is correct;
- **`FLAG_INCLUDE_STOPPED_PACKAGES` is never added on this path.** It appears exactly once in the
  whole file (`:21597`) and only for `ACTION_MANAGED_PROFILE_PROVISIONED`. This is the root of the
  reliability problem in §4.

### 2.6 What the callback does *not* give

**SOURCE + reasoning.** Nothing in the action carries the PIN, the attempt reason, the identity of
who typed, whether the challenge was PIN/password/pattern/biometric, or whether the screen was
locked by an attacker or by the owner fumbling. `onPasswordFailed` gives an `Intent` with the action,
`EXTRA_USER`, and nothing else of substance; the only number available is the attempt count. So the
Signal's real content is **"the owner's device recorded one rejected challenge at time T for user U"**.
Any richer claim in the UI or in an evidence record would be invented.

---

## 3. Gate 2 — DeviceAdmin / Keyguard implications

### 3.1 What the role actually is

**SOURCE**, official Android Enterprise guide, `developer.android.com/work/device-admin`:
"Android includes support for enterprise apps by offering the Android Device Administration API…
These APIs allow you to create security-aware apps that are useful in **enterprise settings**, in
which IT professionals require rich control over employee devices."

That framing is a **product-claim** problem, not a technical one: SHIELDRA would be asking a
consumer to grant an API Google documents for IT administrators. §6 lists the decisions this
touches.

### 3.2 Deprecation — the precise position for the one policy we need

**SOURCE**, official page `developers.google.com/android/work/device-admin-deprecation`:

> "Android's original device admin API, introduced in Android 2.2, **is being deprecated for
> enterprise use**… Key device admin policies related to cameras, keyguard features, and password
> management have been deprecated in Android 9.0…"

The full deprecated list on that page is: `USES_POLICY_DISABLE_CAMERA`,
`USES_POLICY_DISABLE_KEYGUARD_FEATURES`, `USES_POLICY_EXPIRE_PASSWORD`,
`USES_POLICY_LIMIT_PASSWORD` (throw `SecurityException` for admins targeting API 29+), and
`USES_POLICY_RESET_PASSWORD` (Android 11, targeting 24+).

**`USES_POLICY_WATCH_LOGIN` is not in that list.** And the same page carves out exactly our
category:

> "Some applications use the device admin for **consumer device administration**, e.g. locking and
> wiping a lost device. The following policies will continue to be available to enable this:
> `USES_POLICY_WIPE_DATA`, `USES_POLICY_FORCE_LOCK`."

Combined with §2.3 (the surviving, non-owner-required policy is the one that observes a rejected
challenge) and §3.5 (a live `watch-login` admin exists on this exact build), the honest summary is:
**device admin is deprecated *as a management platform*; observing failed challenges is not one of
the deprecated policies, and it is still functioning on Android 16.** That is a narrow window, and
it can close in a future release — which is a reason to build small, not a reason to skip building.

### 3.3 The user-facing cost, quoted

**SOURCE**, same guide:

> "**To uninstall an existing device admin app, users need to first unregister the app as an
> administrator.**"

> "If a device contains multiple enabled admin apps, **the strictest policy is enforced**. There is
> no way to target a particular admin app."

The second sentence is a real risk on a device that already has an admin (§3.5): our declaration of
`watch-login` is scoped to us in practice, but the platform's model is "strictest wins", so we must
not declare any policy we do not use. **The `uses-policies` block must contain exactly `watch-login`
and nothing else** — no `limit-password`, no `force-lock`, no `wipe-data`, no `disable-keyguard-features`.

The first sentence is the one that collides with Play policy (§6.2).

### 3.4 Reversibility (which is the opposite of ransomware)

The same official page lists device admin's *limitations* as an argument for moving to managed
device: "Device admin has limitations compared to newer modes like managed device and work profile,
particularly in areas like trust relationships, secure password reset, and **preventing admin
removal**." Read the sign: device admin **cannot** prevent removal. The user can always revoke us
in Settings. That is the property that keeps a Lock Guard inside policy (§6.2) and it is also why
Lock Guard can never be a *lock*: a thief who knows Android just turns the admin off.

**NOT RUN:** whether this realme build's Settings offers deactivation as plainly as AOSP does, and
what the OEM dialog wording is. This must be observed on-device before any claim is shipped.

### 3.5 Device evidence that the role is grantable here (DEVICE)

Read-only measurements on the owner's `IR7X5DJ7KZGEHMS8`:

| Probe | Result |
|---|---|
| `pm list features` | `feature:android.software.device_admin` **and** `feature:android.software.secure_lock_screen` — the `@RequiresFeature` of §2.4 is satisfied |
| `dumpsys device_policy` | `mHasFeature=true`, `mIsWatch=false`, `mIsAutomotive=false`, `mHasTelephonyFeature=true` |
| Enabled admins, user 0 | `com.toufikbendjeddah.phonefortress/com.example.receiver.MyDeviceAdminReceiver`, `uid=10815`, `testOnlyAdmin=false`, `policies:` → **`watch-login`**, `maximumFailedPasswordsForWipe=0`, `disabledKeyguardFeatures=0` |
| `cmd package query-activities -a android.app.action.ADD_DEVICE_ADMIN` | `com.android.settings/com.oplus.settings.feature.security.OplusDeviceAdminAdd` — the OEM ships a working grant screen |
| `dumpsys package permissions` | `Permission [android.permission.BIND_DEVICE_ADMIN] … prot=signature\|role` |
| AOSP `core/res/AndroidManifest.xml:5916-5917` | `<permission android:name="android.permission.BIND_DEVICE_ADMIN" android:protectionLevel="signature\|role" />` |

The `watch-login` admin in row 3 belongs to the sibling PhoneFortress build, not to SHIELDRA. It is
reported because it is the strongest available proof that this exact OEM/Android-16 combination
grants, persists and advertises the precise policy Lock Guard would need — and because
`maximumFailedPasswordsForWipe=0` shows that admin wipes nobody.

**A device admin is declared in the app's own manifest with `android:permission="android.permission.BIND_DEVICE_ADMIN"` on the receiver** — that is an attribute on our component, not a permission we request. **REPO/DEVICE:** the sibling's decoded manifest shows the shape and carries **no `<uses-permission>` for `BIND_DEVICE_ADMIN`**.

---

## 4. Gate 3 — background, process death, and the A-10 collision

- **Oreo implicit-broadcast ban: does not apply.** §2.1's `explicitOnly` delivery.
- **Force-stop and reboot: delivery is refused.** §2.5's `sendAdminCommandLocked` sets no
  `FLAG_INCLUDE_STOPPED_PACKAGES`, and DPMS bails out at `:3127-3131` if the receiver does not
  resolve for that user. A freshly rebooted or force-stopped app is in the stopped state, so
  **the callback is silently not delivered** — there is no error to observe, no missed-event
  signal, nothing. The only standard cure is a `BOOT_COMPLETED` receiver, which **A-10 forbids**
  (`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md:630`) and which
  `.github/scripts/g4b-review-check.sh:577-581` and `:644-648` enforce as a CI failure.
- **Doze / OEM battery killing.** Motion measured this device class directly: with the screen off,
  the process received 0–22 scheduler ticks across 229–1128 seconds
  (`docs/reports/batch-G4A-background-sensor-measurement.md`, RMX3910 campaign). A `DeviceAdminReceiver` is a broadcast
  receiver, not a service, so it is not "starved" the same way — but it is woken by the same
  AMS/broadcast queue whose behaviour on this build has **NOT RUN** been measured for the
  lock-screen case, and the OEM's "deep sleep"/auto-manage settings can keep the app frozen.
- **This is why Lock Guard's honest capability ceiling is: "detects two wrong attempts while the app
  has been running since it last opened."** Anything stronger is a §31 claim. The capability doc
  already demands exactly this candour ("claim guaranteed monitoring across process death,
  force-stop, Doze, reboot, profiles, or OEM policy" is on the forbidden list).
- **No foreground service is needed** to receive it, so A-15/A-19's FGS decisions are untouched, and
  A-9's notice wording is not extended by Lock.

---

## 5. Gate 4 — the current official Google Play policy, quoted exactly

§25 required a redo. **The premise recorded in Q-C4 is not supported by today's policy text.** The
register said Play's "Device and Network Abuse" policy "cover[s] device-admin use and
lock-screen/credential observation", with permitted categories "typically enterprise device
management, parental control, and anti-fraud". I read the current full text of both pages it points
at, and of the consolidated Developer Program Policy. **Neither "Device and Network Abuse" nor
"Permissions and APIs that Access Sensitive Information" mentions device administration,
`DevicePolicyManager`, `DeviceAdminReceiver`, the lock screen, or credentials observation at all.**
The sensitive-APIs page's sections are: Restricted Permissions, minimum-scope alternatives,
Contacts, Photo/Video, SMS & Call Log, Location, All Files Access, Package Visibility, Accessibility
API, Request Install Packages, Body Sensor, Health Connect, VPN, Exact Alarm, Full-Screen Intent,
Age Signals. Device admin appears in none of them. The Android "behavior changes" pages for 13, 14,
15 and 16 also contain zero occurrences of "device admin" or "DevicePolicyManager".

**A negative claim needs a positive control** — Batch I was burned by a fetch that returned only page
navigation. Each file whose zeros are quoted above was checked to contain real policy prose first: the
Device and Network Abuse copy carries its own chapeau ("Google Play prohibits your app (or any
third-party SDKs in your app) from unauthorized access or interference with the user's device…" at line
309 of its stripped text), and the same greps over the same files do find the words that should be
there ("Malware", "Ransomware", "Stalkerware", "Lockscreen Monetization"). The only "credential"
matches on the sensitive-APIs page are inside its own JavaScript (`withCredentials`), not its policy
text, so "credentials observation" is reported as absent rather than padded.

So there is **no Play clause that forbids a consumer app from becoming a device admin.** What
actually governs us is a different set of clauses, and they are stricter in a way the old premise
missed. All quotes below are from the consolidated **Developer Program Policy**,
`support.google.com/googleplay/android-developer/answer/18258653` (fetched 2026-10-02; line numbers
refer to the tag-stripped text file §11 shows how to rebuild):

**5.1 Deceptive Behavior (chapeau, lines 1218-1219):**
> "Apps must provide an accurate disclosure, description and images/video of their functionality in
> all parts of the metadata… **Any changes to device settings must be made with the user's knowledge
> and consent and be reversible by the user.**"

**5.2 Deceptive Device Settings Changes (lines 1233-1238):**
> "2.1 We don't allow apps that make changes to the user's device settings or features outside of the
> app without the user's knowledge and consent…"
> "2.2.1 Apps that modify device settings or features with the user's consent but do so **in a way
> that is not easily reversible**."
> "2.2.3 Apps that **mislead users into** … **modifying device settings or features**."
> "2.2.4 Apps that **encourage or incentivize users into** … **modifying device settings or features
> unless it is part of a verifiable security service**."

2.2.1 is the clause a device-admin grant has to survive: becoming an admin is reversible, but the
policy text Google quotes makes "with consent **and** easily reversible" two separate tests. 2.2.3 and
2.2.4 are what makes Q-J-4 (the prompting shape) a policy question rather than a UX one — a re-prompt or
an incentivised grant is exactly the conduct those two sub-clauses name. **2.2.4 carries its own
escape, "unless it is part of a verifiable security service"**, and it is stated here rather than
dropped: a Lock Guard that really detects failed challenges could claim it. What SHIELDRA may not do is
invoke that escape for a signal §4 has shown can be silently undelivered — 2.2.4 would then be paid for
out of 5.5's misleading-claims clause, which has no such exception.

**5.3 Malware → Ransomware (lines 1463-1466):**
> "Some ransomware encrypts data on the device and demands payment to decrypt the data and/or
> **leverage the device admin features so that it can't be removed by a typical user**. Examples
> include: … **Leveraging device policy manager features and blocking removal by the user.**"

The very next line (1467) is the only exemption in these pages that touches device admin: "Code
distributed with the device whose primary purpose is for subsidized device management may be excluded
from the ransomware category provided they successfully meet requirements for secure lock and
management, and adequate user disclosure and consent requirements." **It is about pre-installed carrier
or OEM management code, not an app a user installs from Play**, so SHIELDRA cannot claim it — recorded
here so a later batch does not discover it and read it as a way out.

**5.4 Malware → Elevated Privilege Abuse (line 1458):**
> "Apps that **abuse features to prevent them from being uninstalled or stopped**."

**5.5 Deceptive Behavior → 1. Misleading Claims (lines 1220-1226):**
> "Apps that misrepresent or do not accurately and clearly describe their functionality: An app that
> claims to be an antivirus app, but only contains a text guide explaining how to remove viruses."
> "Apps that claim functionalities that are not possible to implement…"

**5.6 "Protect user data and privacy" summary clause (lines 1531-1534):**
> "Do not request or deceive users into turning off device security protections such as Google Play
> Protect."

**5.7 Clauses that were checked and do not apply**, with the reason stated so nobody re-litigates them:
- *Stalkerware / "Guidelines for Monitoring Applications"* (lines 1424-1435, `isMonitoringTool`):
  applies to apps "exclusively designed and marketed for **monitoring another individual**".
  SHIELDRA observes the owner's own device, stores locally, and transmits nothing (§30), so it is
  not a monitoring app and must **not** set `isMonitoringTool`. If a future build ever phones a Lock
  event to anyone else, this clause turns on instantly and §30 is already broken first.
- *Accessibility Services* (line 1034, "prevent the ability for users to disable or uninstall any
  app"): the only "uninstall" language in the sensitive-APIs policy lives in the Accessibility
  section. **SHIELDRA declares no accessibility service and must not** — `docs/capabilities/lock-guard.md`
  already forbids accessibility as a Lock signal source.
- *Sensitive Events* (lines 419-424): about capitalising on public tragedies; irrelevant here.
- *Lockscreen Monetization* (line 1657): "apps may not introduce ads or features that monetize the
  locked display of a device" — no ads are in this build; recorded because the anti-theft copy on
  the store listing sits next to this clause.
- *Foreground Services* and *FLAG_SECURE* / *REQUIRE_SECURE_ENV* (Device and Network Abuse): we
  declare `specialUse` for Motion (A-15/A-19) and would add nothing for Lock; we neither read nor
  bypass another app's `FLAG_SECURE`.

**5.8 Data Safety / User Data.** A recorded failed-challenge timestamp is user data collected inside
the app. Under §26/§29/§30 it stays encrypted on-device with no export and no network, so the
correct Data-safety answer remains "not collected / not shared" **only while that stays true**; the
declaration must be re-checked at Batch L rather than inherited from this report. **NOT RUN:** the
actual Play Console Data-safety form state for the four launch packages.

### The compliance shape this dictates (if the owner says yes)

1. grant is **user-initiated**, from a named screen that says in plain words what a device admin is
   and that it can be removed;
2. the app carries **exactly `watch-login`** and no other policy (§3.3);
3. an **in-app "remove protection" action exists and is discoverable**, and the app never blocks,
   discourages, or complicates deactivation or uninstall — 5.2 and 5.3 turn on the *easily reversible*
   word, not on the role itself;
4. no claim of guaranteed or 24/7 monitoring anywhere (A-9 and 5.5 agree);
5. uninstalling must stay possible: if Lock Guard ever shipped a "block uninstall until PIN"
   feature, it would sit inside 5.3/5.4 and **must not** be built without a fresh owner amendment.

---

## 6. Gate 5 — distribution decision

Q-C4 remains open (answered "Option 3 — decide at Batch J"). The corrected evidence changes the
question's shape: the risk is **not** "Play forbids device admin", it is "Play punishes an admin that
is not easily reversible, that is described better than it is, or that promises more than §4 allows
it to deliver". A sideloaded build has neither the review nor the listing-contract exposure. This is
still a single owner call, and §5.7 of the protocol means I present it rather than decide it.

---

## 7. What already exists in the repo, and what is missing (REPO)

Already in place, tested, and CI-green:

- `app/src/main/kotlin/com/shieldra/domain/GuardContracts.kt:76-84` — `ApprovedGuardRules` with the
  approved Lock numbers: `lockConfirmationWindow = 2 min`, `lockCounterResetWindow = 10 min`,
  `lockCooldown = 5 min`, `lockAttemptsToConfirm = 2`.
- `app/src/main/kotlin/com/shieldra/domain/ProtectionStateEngine.kt:36-64` — `evaluateLock`,
  deterministic, framework-free, cooldown → reset-window pruning → 2-of-N confirmation.
- `app/src/main/kotlin/com/shieldra/domain/GuardContracts.kt:29,87` — `GuardObservation.FailedPinAttempt`
  and the shape rule `LOCK -> observation is FailedPinAttempt`.
- `app/src/main/kotlin/com/shieldra/detection/GuardBoundaries.kt:12-68` — the five explicit
  capability states (`AVAILABLE / NOT_DEVICE_ADMIN / UNSUPPORTED / PERMISSION_OR_PROVISIONING_REQUIRED / RUNTIME_ERROR`)
  and the boundary that only `AVAILABLE` may carry a verified Signal.
- `app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDecision.kt:17,34,37,49` — the
  honest projection: `NotActive`, `NotActive -> ProtectionVisualState.Disabled`, and
  `lastCheckSeconds(state) = null` for every state. `LockGuardDashboardFactory.kt:59-64` maps
  `GuardKind.Lock` to the tile label "Lock Guard"/«حارس القفل» (`values/strings.xml:183`,
  `values-ar/strings.xml:183`).

Missing, and all of it behind an owner decision:

- a `DeviceAdminReceiver` subclass; a `<receiver android:permission="android.permission.BIND_DEVICE_ADMIN"
  android:exported="true">` in `app/src/main/AndroidManifest.xml` (**REPO:** the manifest currently
  declares **zero receivers** — only three `<uses-permission>` at `:20-22` and one `<service>` at `:49`);
- `res/xml/device_admin.xml` with `<uses-policies><uses-policy>watch-login</uses-policy></uses-policies>`;
- the `ACTION_ADD_DEVICE_ADMIN` intent surface and its disclosure copy;
- the `LockGuardCapabilityProbe` that decides which of the five states is true;
- an event type for a confirmed Lock event — **your decision, not a default (Q-J-5, same as Q-I-3)**;
- an in-app deactivation path (§5 point 3).

---

## 8. Gate impact of an eventual implementation (REPO)

Stated now, before code, so no future batch can relax a gate implicitly:

- **No new `<uses-permission>`** (§2.4, §3.5) ⇒ `.github/scripts/g4b-review-check.sh:330-335`, which
  pins the manifest to exactly `FOREGROUND_SERVICE FOREGROUND_SERVICE_SPECIAL_USE POST_NOTIFICATIONS`,
  stays untouched. If a Lock implementation ever needs a permission, that is a new amendment, not an
  edit.
- **`BOOT_COMPLETED` stays barred** by A-10 and enforced at `g4b-review-check.sh:505`, `:577-581`,
  `:644-648`. Lock Guard must be built to be honest *without* it. If the owner ever amends A-10, that
  amendment must be written first and must say which of the §4 limits it removes.
- **The two-minute window is a trap.** `g4b-review-check.sh:780` greps
  `'4\.6\|0\.8\|20_000\|11\.0\|500L\|100L'` across `app/src/main/kotlin/com/shieldra/detection` and
  `.../runtime`, exempting only `ApprovedMotionCalibration.kt`. A literal `120_000L` inside `detection/`
  contains `20_000` as a substring and would **fail CI for the wrong reason**. The numbers already
  live in `domain/ApprovedGuardRules`, which is not scanned — an adapter must consume them, never
  restate them.
- **`group-c-contract-check.sh:27-29`** forbids `SecurityEvent|CONFIRMED_SECURITY_EVENT` anywhere in
  `detection/`. A Lock receiver in `detection/` must hand the Signal to the domain engine and build
  nothing itself — same shape Motion already uses.
- **No gate scans `<receiver>` or `android:exported`.** Verified by grep across
  `.github/scripts/*.sh`: the only receiver text is the comment at `g4b-review-check.sh:488`
  ("no boot receiver may appear while reboot auto-start is rejected (A-10)"). **So CI will not catch a
  wrong turn here; the guard is this report plus the owner decision.** That is a deliberate finding,
  not a comfort.
- **§31 / dashboard:** `LockGuardDashboardFactory` must keep deriving from real state. A granted
  admin is not "Active"; a detected burst is the only thing that may raise an event, and per A-18
  the dedup stays Motion-scoped so a Lock event can never absorb a Motion burst.
- **Docs are not scanned:** no gate greps `docs/`, which is why this report can quote the policy
  verbatim without tripping CI.

---

## 9. Decisions that are the owner's alone (they block implementation; I am not making them)

| ID | The decision | Why it is yours and not an engineering choice | My evidence, in one line |
|---|---|---|---|
| **Q-J-1** | **Should a consumer security app ask to become a device administrator at all?** Yes/No, and if yes with what disclosure sentence in EN + AR | It is a new **platform-capability + privacy + product-claim** grant under an API Google documents as enterprise (§3.1); §7 says this stops here rather than being "obvious" | §2 is a clean yes technically; §3.3-§5.3 is a real cost |
| **Q-J-2** | **Distribution** (re-asked from Q-C4, now on corrected text): Play build with Lock, Play build without Lock, or sideload/enterprise carrying Lock | The old premise ("Play covers device-admin use") is false; the true exposure is reversibility + claims (§5). Only you can weigh listing risk | §5, §6 |
| **Q-J-3** | **A-10 vs. deafness:** keep no `BOOT_COMPLETED` and ship Lock Guard as "runs only since the app last started", **or** amend A-10 for a boot receiver, **or** don't build Lock | A-10 is your authority amendment; the wording of the resulting claim is §31/§37 territory | §4 (delivery is silently refused when stopped) |
| **Q-J-4** | **Opt-in shape:** default-off with an onboarding step, or strictly on-demand behind a toggle, and whether the app may *re-prompt* | Repeated prompting is exactly what 5.2 ("mislead users into… modifying device settings") and 5.6 police; the answer changes the copy and the capability states | §5.2, §5.6 |
| **Q-J-5** | **What a confirmed Lock event may claim**, its severity, and whether Lock gets its own History row type at all | Severity/magnitude of a guard is yours (§37). The type is **the same open decision as Q-I-3**, seen from Lock's side: `SecurityEventType` has no Lock value, `g4b-review-check.sh:820-823` pins the three-name History mapping, and the G5 protocol said "do not invent new event types" — so `LOCK_CONFIRMED_EVENT` needs your answer **and** an amendment, the way Motion's type got A-18, rather than a quiet enum edit | `SecurityEventContracts.kt:13-37`, `OWNER_QUESTIONS.md:1023` |
| **Q-J-6** | The **`premium_free_summary`** Lock half (already raised as **Q-I-6**, `OWNER_QUESTIONS.md:1026`): "Free includes: Lock Guard · … · Email delivery." Four claims, one detector | If Q-J-1 is *no*, this sentence is a §5.5 misleading-claims violation today; if Q-J-1 is *yes*, it becomes accurate for Lock only after Q-J-2/Q-J-3 land | `values/strings.xml:209`, `values-ar/strings.xml:209`, used at `DemoData.kt:109` |

**Answer order:** Q-J-1 → Q-J-2 → Q-J-3 are one decision seen from three sides; nothing about Lock can
be built until all three exist. Q-J-4/Q-J-5 block the UI slice; Q-J-6 blocks the listing, not the code.

**Not asked, because it is already decided:** whether a Lock Signal may trigger camera, location,
network or cloud evidence (it may not — `docs/capabilities/lock-guard.md`, §21-§23, §30, and Batch H
owns camera); whether Lock may use accessibility (no, §5.7); whether an unsupported state may be
papered over with a guessed counter (no, §2.6 and `GuardBoundaries.kt`).

---

## 10. Explicitly NOT RUN

1. **Receipt of `ACTION_PASSWORD_FAILED` by an installed admin on this device.** `dumpsys activity
   broadcasts` contains **zero** occurrences of the action; the history covers this boot only.
   Proving delivery needs the owner to enter wrong PINs while a SHIELDRA admin is active — LOCK-02/03
   in `docs/capabilities/lock-guard.md`, which cannot be run before Q-J-1.
2. Counter reset semantics observed live (`getCurrentFailedPasswordAttempts()` after success/failure)
   — §2.5 reads the source; the source is not the device.
3. What the realme grant dialog, the "device admin apps" list, the deactivation flow and the
   uninstall-blocked prompt actually say on this build (§3.3, §3.4).
4. Whether an active device admin survives the OEM battery manager, app-auto-start restrictions, and
   a "clean" gesture.
5. Behaviour under a work profile / secondary user / `getParentProfileInstance` scope
   (`LOCK-09`).
6. Whether `passwordQuality=0x0` on the sibling admin proves anything about challenge type detection —
   it does not; not investigated.
7. Play Console Data-safety and content declarations for the four launch packages (§5.8).
8. Any performance measurement of receiver wake-up latency.

---

## 11. Sources, and how to reproduce them

Official Android (AOSP), `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/`:
- `core/java/android/app/admin/DeviceAdminReceiver.java` — 1260 lines; `:148-163`, `:683`, `:697`, `:1190`
- `core/java/android/app/admin/DeviceAdminInfo.java` — 600 lines; `:62-72`, `:74-83`
- `core/java/android/app/admin/DevicePolicyManager.java` — 18325 lines; `:6096-6108`, `:9573`
- `core/res/AndroidManifest.xml` — `:539` protected-broadcast, `:5916-5917` BIND_DEVICE_ADMIN
- `services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java` —
  24901 lines; `:3111-3146`, `:8389-8395`, `:8408`, `:8420-8422`, `:8346`, `:8493`, `:21597`

Each file above was re-downloaded with `base64 -d` from the `?format=TEXT` URL and the quoted line
numbers were re-grepped in that fresh copy; the line counts printed here are from those copies.
No WebFetch summarizer was trusted for any of this.

Official Google pages (fetched 2026-10-02, tag-stripped with `sed` and searched locally, so the
quotes above are the page text and not a model's paraphrase). The Play line numbers in §5 refer to
the text file this command produces, since the page has no stable line numbers of its own:

```sh
curl -sL "https://support.google.com/googleplay/android-developer/answer/18258653?hl=en" -o p.html
sed -e 's/></>\n</g' p.html | sed -e 's/<[^>]*>//g' \
  | grep -v '^[[:space:]]*$' \
  | sed 's/&nbsp;/ /g; s/&amp;/\&/g; s/&#39;/'"'"'/g; s/&quot;/"/g' > p.txt   # 8558 lines
```

The positive control behind §5's negative claims (a zero is only believable on a page whose body
actually arrived — Batch I's lesson):

```sh
for f in play_16559646.txt play_16909972.txt play_policy_full.txt bc13.txt bc16.txt; do
  echo "$f  device-admin=$(grep -ci 'device admin' $f)  DPM=$(grep -ci DevicePolicyManager $f)"
done
grep -n "unauthorized access or interference" play_16559646.txt   # proves the body is real prose
```
- Developer Program Policy — https://support.google.com/googleplay/android-developer/answer/18258653
- Device and Network Abuse — https://support.google.com/googleplay/android-developer/answer/16559646
- Preview: Permissions and APIs that Access Sensitive Information — https://support.google.com/googleplay/android-developer/answer/16909972
- User Data — https://support.google.com/googleplay/android-developer/answer/9888076
- Device administration overview — https://developer.android.com/work/device-admin
- Device admin deprecation — https://developers.google.com/android/work/device-admin-deprecation
- `DeviceAdminReceiver` reference — https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver

Device probes (read-only; the working copies under `%TEMP%` were deleted after reading, and these
commands reproduce them):

```sh
export MSYS_NO_PATHCONV=1
adb shell pm list features | grep -E 'device_admin|secure_lock_screen'
adb shell dumpsys device_policy | head -45
adb shell cmd package query-activities -a android.app.action.ADD_DEVICE_ADMIN --brief
adb shell dumpsys package permissions | grep -A3 'BIND_DEVICE_ADMIN\]'
adb shell dumpsys activity broadcasts | grep -c ACTION_PASSWORD_FAILED   # 0 on this boot
# framework-res.apk → protected-broadcast + permission attributes
adb shell cmd package path android
/c/.../Sdk/build-tools/36.0.0/aapt2.exe dump xmltree framework-res.apk --file AndroidManifest.xml
grep -n "ACTION_PASSWORD_FAILED\|BIND_DEVICE_ADMIN" that-dump
```

Repository state read for §2.6, §7, §8: commit `c3b2a4a777dc29cb4349f3e72d67c0b98969fd13`.
