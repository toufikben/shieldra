# A-45 Snatch Ladder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a confirmed Motion run a ladder — lock and demand an unlock at once, then escalating evidence bursts — mute the escalation (never the detection) inside a safe zone, and show the holder a theft notice the app composes in Arabic, English and French.

**Architecture:** Three new pure Kotlin policy objects (`SnatchLadder`, `ZonePolicy`, `TheftNoticeText`, `OwnerContact`) carry every decision, so CI's JVM suite can test them without a device. Android-side code (`TableModeResponder`, `TheftDemand`, `ZoneStateStore`, `GeofenceTicker`) only schedules and renders what the pure layer decided. `MotionMonitoringService` keeps detecting exactly as today; the change is what a confirmation *does*.

**Tech Stack:** Kotlin 2.x, Android SDK 36 / minSdk 26, Jetpack Compose (Material3), JUnit4 JVM tests, Gradle via GitHub Actions only, `Notification`/`DevicePolicyManager`/`AlarmManager` APIs.

**Spec:** `docs/superpowers/specs/2026-11-04-snatch-ladder-design.md` — this plan implements decisions D1–D5 and the constants table there. Read both together.

## Global Constraints

- **No local builds.** `./gradlew` must never be run on this machine (2 cores / 4 GB / HDD). CI (`group-a-diagnostics.yml`) is the only verifier of compilation and tests. Shell gate scripts may be run locally with `bash`.
- Verification loop: commit on `fix/audit-round-1`, push, then `gh workflow run group-a-diagnostics.yml --ref fix/audit-round-1 --repo toufikben/shieldra`, then `gh run watch <id> --repo toufikben/shieldra`. CI runs `./gradlew clean test lint assembleDebug assembleDebugAndroidTest`, then `group-c-contract-check.sh`, `g4b-review-check.sh`, `phase3-review-check.sh`, then `test-summary.sh`.
- Do not burn one CI run per task. The plan has four CI checkpoints (after Tasks 4, 6, 7, 9); tasks between checkpoints are verified by reading the code and by the gate scripts that run locally.
- The branch is `fix/audit-round-1`. Never touch `main`. Never delete a branch, never change repo visibility, never upload the signed release (the owner deferred it).
- Copy rules: every new user-facing string goes in `app/src/main/res/values/strings.xml` (English) **and** `values-ar/strings.xml` **and** `values-fr/strings.xml`; no hard-coded text in composables; no `!!` claims in copy — the notice states what the app does, never what it cannot do.
- Play rules this design must not cross: no full-screen-intent (Android 14 revokes it for non-calling/alarm apps), no wipe capability, no hostage-style wording, and the notice text must be truthful about what is sent.
- Do not add any permission. `ACTIVITY_RECOGNITION` and `ACCESS_BACKGROUND_LOCATION` are out of scope by D5; `ProtectionService` already has `foregroundServiceType="camera|location|microphone"` and `SCHEDULE_EXACT_ALARM` is already declared.
- Do not change `ApprovedMotionCalibration` thresholds in this plan (D4 says they wait for real measurements from Task 7).
- Never ask for or enter the phone's screen lock PIN; never reboot the phone; leave the shared phone with our app force-stopped.
- Evidence and QA artifacts stay outside the repo tree (`qa/` in the session folder), never committed.
- Commit style: `feat:`/`fix:`/`test:`/`docs:` + what changed and why, one commit per task, no `--no-verify`, no amend of an earlier commit.

---

### Task 1: Ladder timing (pure)

**Files:**
- Create: `app/src/main/kotlin/com/shieldra/protection/SnatchLadder.kt`
- Test: `app/src/test/kotlin/com/shieldra/protection/SnatchLadderTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class LadderAction { NONE, EVIDENCE_BURST, SETTLED }`, `data class LadderTick(val action: LadderAction, val delayMs: Long, val burst: Int)`, `object SnatchLadder { const val DEMAND_MS: Long; const val BURST_INTERVAL_MS: Long; const val FAST_BURSTS: Int; const val SLOW_INTERVAL_MS: Long; const val LADDER_WINDOW_MS: Long; fun burstAtMs(burst: Int): Long; fun next(elapsedMs: Long, burstsDone: Int): LadderTick }`. Task 3 and Task 8 call `next()`; Task 8 shows `DEMAND_MS / 1000` in the notice.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/shieldra/protection/SnatchLadderTest.kt`:

```kotlin
package com.shieldra.protection

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A-45 D1: the shape of the snatch ladder. The first evidence burst waits out the owner-unlock
 * demand, the fast phase runs every minute for five bursts, the slow phase every five minutes, and
 * the whole thing stops at the 30-minute window. Timing only — no device, no Android.
 */
class SnatchLadderTest {

    @Test
    fun `the first burst is the demand window`() {
        assertEquals(30_000L, SnatchLadder.burstAtMs(1))
    }

    @Test
    fun `the fast phase is one minute apart`() {
        assertEquals(90_000L, SnatchLadder.burstAtMs(2))
        assertEquals(330_000L, SnatchLadder.burstAtMs(6))
    }

    @Test
    fun `the slow phase is five minutes apart`() {
        assertEquals(630_000L, SnatchLadder.burstAtMs(7))
        assertEquals(1_530_000L, SnatchLadder.burstAtMs(10))
    }

    @Test
    fun `a burst that would fall past the window is never scheduled`() {
        // burstAtMs(11) = 1_830_000 ms, past the 30-minute window.
        val tick = SnatchLadder.next(elapsedMs = 1_531_000L, burstsDone = 10)
        assertEquals(LadderAction.SETTLED, tick.action)
        assertEquals(0L, tick.delayMs)
    }

    @Test
    fun `elapsed inside the demand window waits for the rest of it`() {
        val tick = SnatchLadder.next(elapsedMs = 12_000L, burstsDone = 0)
        assertEquals(LadderAction.EVIDENCE_BURST, tick.action)
        assertEquals(18_000L, tick.delayMs)
        assertEquals(1, tick.burst)
    }

    @Test
    fun `elapsed past a due burst schedules the next one, not a pile of them`() {
        // 200 s elapsed, three bursts fired: burst 4 is due at 210 s.
        val tick = SnatchLadder.next(elapsedMs = 200_000L, burstsDone = 3)
        assertEquals(LadderAction.EVIDENCE_BURST, tick.action)
        assertEquals(10_000L, tick.delayMs)
        assertEquals(4, tick.burst)
    }

    @Test
    fun `a restarted ladder begins at the demand again`() {
        val tick = SnatchLadder.next(elapsedMs = 0L, burstsDone = 0)
        assertEquals(SnatchLadder.DEMAND_MS, tick.delayMs)
    }

    @Test
    fun `the window is thirty minutes and the demand is thirty seconds`() {
        assertEquals(30_000L, SnatchLadder.DEMAND_MS)
        assertEquals(1_800_000L, SnatchLadder.LADDER_WINDOW_MS)
        assertEquals(5, SnatchLadder.FAST_BURSTS)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails for the right reason**

Run: `cd /c/src/tmp/shieldra-work && grep -c "SnatchLadder" app/src/main/kotlin/com/shieldra/protection/*.kt || true`
Expected: `0` matches — the object does not exist, so CI would fail compilation. (The JVM test itself cannot run locally: no local builds. Task 4's CI checkpoint is its first execution.)

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/com/shieldra/protection/SnatchLadder.kt`:

```kotlin
package com.shieldra.protection

/** What the ladder does next after a confirmed Motion. */
enum class LadderAction { EVIDENCE_BURST, SETTLED }

/** The next step: what to run, how long from now, and which burst number it is. */
data class LadderTick(val action: LadderAction, val delayMs: Long, val burst: Int)

/**
 * The timing of the A-45 snatch ladder (design D1).
 *
 * The owner's complaint about the old behaviour was that a single shot at the moment of the snatch is
 * worth little: the frame is a blur and the thief is already running. So the ladder locks and demands
 * an unlock first, and only spends the camera, the microphone and the alert train once nobody has
 * unlocked. The fast phase exists because the first minutes are where a thief is still moving and a
 * frame can still identify him; the slow phase exists because after that the useful thing is where the
 * phone is going, and every extra capture costs battery on a phone the owner may never get back.
 *
 * Pure: elapsed time and bursts already fired in, the next delay out. Tested without a device.
 */
object SnatchLadder {

    /** How long the holder is given to unlock before the first evidence burst. */
    const val DEMAND_MS = 30_000L

    /** The gap between bursts in the fast phase. */
    const val BURST_INTERVAL_MS = 60_000L

    /** How many bursts run at the fast cadence after the first one. */
    const val FAST_BURSTS = 5

    /** The gap between bursts once the fast phase is spent. */
    const val SLOW_INTERVAL_MS = 300_000L

    /** Nothing is scheduled past this age of the incident: the ladder settles. */
    const val LADDER_WINDOW_MS = 1_800_000L

    /** When burst number [burst] is due, measured from the start of the incident. */
    fun burstAtMs(burst: Int): Long = when {
        burst <= 1 -> DEMAND_MS
        burst <= FAST_BURSTS + 1 -> DEMAND_MS + (burst - 1) * BURST_INTERVAL_MS
        else -> DEMAND_MS + FAST_BURSTS * BURST_INTERVAL_MS +
            (burst - FAST_BURSTS - 1) * SLOW_INTERVAL_MS
    }

    /**
     * The next step for an incident that started [elapsedMs] ago and has already run [burstsDone]
     * bursts. A burst that is already overdue collapses into "run it now"; a burst that would fall
     * outside the window never happens.
     */
    fun next(elapsedMs: Long, burstsDone: Int): LadderTick {
        val burst = burstsDone + 1
        val due = burstAtMs(burst)
        if (due >= LADDER_WINDOW_MS) return LadderTick(LadderAction.SETTLED, 0L, burstsDone)
        return LadderTick(LadderAction.EVIDENCE_BURST, (due - elapsedMs).coerceAtLeast(0L), burst)
    }
}
```

- [ ] **Step 4: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/protection/SnatchLadder.kt app/src/test/kotlin/com/shieldra/protection/SnatchLadderTest.kt
git commit -m "feat: give a confirmed motion an evidence ladder instead of one shot"
```

---

### Task 2: What the holder is told (pure text) and what the contact means (pure)

**Files:**
- Create: `app/src/main/kotlin/com/shieldra/protection/TheftNoticeText.kt`
- Create: `app/src/main/kotlin/com/shieldra/protection/OwnerContact.kt`
- Test: `app/src/test/kotlin/com/shieldra/protection/TheftNoticeTextTest.kt`
- Test: `app/src/test/kotlin/com/shieldra/protection/OwnerContactTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object TheftNoticeText { fun compose(arabic: String, english: String, french: String, ownerMessage: String?, contactLine: String?): String }`; `enum class ContactKind { PHONE, EMAIL, UNKNOWN }`; `object OwnerContact { fun kind(raw: String): ContactKind; fun normalized(raw: String): String; fun dialUri(raw: String): String? }`. Task 3 renders them into a notification; Task 8 calls `TheftNoticeText.compose`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/shieldra/protection/TheftNoticeTextTest.kt`:

```kotlin
package com.shieldra.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A-45 D3: whoever holds the stolen phone does not open the owner's settings, so one language is not
 * enough. The notice is composed by the app from its own three locales, with the contact repeated once
 * at the end and the owner's own sentence only when he wrote one.
 */
class TheftNoticeTextTest {

    @Test
    fun `all three languages appear, in that order`() {
        val text = TheftNoticeText.compose(
            arabic = "هاتف مقفل",
            english = "Locked phone",
            french = "Telephone verrouille",
            ownerMessage = null,
            contactLine = null,
        )
        assertEquals(listOf("هاتف مقفل", "Locked phone", "Telephone verrouille"), text.lines().filter(String::isNotBlank))
    }

    @Test
    fun `the contact is written once and last`() {
        val text = TheftNoticeText.compose("AR", "EN", "FR", ownerMessage = null, contactLine = "Owner contact: +213555123456")
        assertEquals(1, text.split("Owner contact").size - 1)
        assertTrue(text.endsWith("Owner contact: +213555123456"))
    }

    @Test
    fun `a blank owner sentence is skipped rather than leaving an empty block`() {
        val text = TheftNoticeText.compose("AR", "EN", "FR", ownerMessage = "   ", contactLine = "call me")
        assertFalse(text.lines().any { it.isBlank() && it.isNotEmpty() })
        assertEquals(listOf("AR", "EN", "FR", "call me"), text.lines().filter(String::isNotBlank))
    }

    @Test
    fun `the owner sentence sits before the contact`() {
        val text = TheftNoticeText.compose("AR", "EN", "FR", ownerMessage = "Please return it", contactLine = "Owner contact: x")
        assertTrue(text.indexOf("Please return it") < text.indexOf("Owner contact: x"))
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}
```

Create `app/src/test/kotlin/com/shieldra/protection/OwnerContactTest.kt`:

```kotlin
package com.shieldra.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The owner contact is shown to a stranger and used for a call action, so "what did he type" must have
 * one answer: a phone, an address, or something the app will not dial.
 */
class OwnerContactTest {

    @Test
    fun `a spaced international number is a phone and dials without the spaces`() {
        val raw = "+213 555 12 34 56"
        assertEquals(ContactKind.PHONE, OwnerContact.kind(raw))
        assertEquals("+213555123456", OwnerContact.normalized(raw))
        assertEquals("tel:+213555123456", OwnerContact.dialUri(raw))
    }

    @Test
    fun `a zero-prefixed number with dashes is still a phone`() {
        val raw = "00213-555-12-34-56"
        assertEquals(ContactKind.PHONE, OwnerContact.kind(raw))
        assertEquals("00213555123456", OwnerContact.normalized(raw))
    }

    @Test
    fun `an address is lowercased and mailed`() {
        val raw = "  Toufik@Example.COM "
        assertEquals(ContactKind.EMAIL, OwnerContact.kind(raw))
        assertEquals("toufik@example.com", OwnerContact.normalized(raw))
        assertEquals("mailto:toufik@example.com", OwnerContact.dialUri(raw))
    }

    @Test
    fun `anything else is unknown and never turned into an intent`() {
        assertEquals(ContactKind.UNKNOWN, OwnerContact.kind("my friend"))
        assertNull(OwnerContact.dialUri("my friend"))
        assertEquals(ContactKind.UNKNOWN, OwnerContact.kind("+213"))
        assertEquals(ContactKind.UNKNOWN, OwnerContact.kind(""))
    }
}
```

- [ ] **Step 2: Confirm they cannot compile yet**

Run: `cd /c/src/tmp/shieldra-work && ls app/src/main/kotlin/com/shieldra/protection/ | grep -E "TheftNoticeText|OwnerContact" || echo MISSING`
Expected: `MISSING`.

- [ ] **Step 3: Write the implementations**

Create `app/src/main/kotlin/com/shieldra/protection/TheftNoticeText.kt`:

```kotlin
package com.shieldra.protection

/**
 * Assembles the theft notice the holder of a locked phone reads (design D3).
 *
 * The app cannot know who picks up the phone, so the notice carries all three languages it ships:
 * Arabic, English and French. The owner's own sentence is appended when he wrote one, and his contact
 * is written once, at the end, so it is the last line either way.
 *
 * Pure: strings in, one string out.
 */
object TheftNoticeText {

    fun compose(
        arabic: String,
        english: String,
        french: String,
        ownerMessage: String?,
        contactLine: String?,
    ): String = buildString {
        val lines = mutableListOf(arabic.trim(), english.trim(), french.trim())
        ownerMessage?.trim()?.takeIf { it.isNotEmpty() }?.let(lines::add)
        contactLine?.trim()?.takeIf { it.isNotEmpty() }?.let(lines::add)
        append(lines.filter(String::isNotEmpty).joinToString("\n\n"))
    }
}
```

Create `app/src/main/kotlin/com/shieldra/protection/OwnerContact.kt`:

```kotlin
package com.shieldra.protection

/** What the owner typed into the contact field actually is. */
enum class ContactKind { PHONE, EMAIL, UNKNOWN }

/**
 * The single reading of the owner's contact (design D3). The value is shown to a stranger and turned
 * into a call action, so the app must know whether it can honestly offer to dial it: a phone number,
 * an address, or neither. A contact the app cannot classify is displayed but never made actionable.
 */
object OwnerContact {

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$")

    /** Digits with an optional leading `+`, after separators are dropped, between 6 and 15 long. */
    private val PHONE = Regex("^\\+?[0-9]{6,15}$")

    fun kind(raw: String): ContactKind {
        val trimmed = raw.trim()
        return when {
            EMAIL.matches(trimmed) -> ContactKind.EMAIL
            PHONE.matches(trimmed.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }) -> ContactKind.PHONE
            else -> ContactKind.UNKNOWN
        }
    }

    /** The form that is displayed and dialled: separators out, address lowercased. */
    fun normalized(raw: String): String = when (kind(raw)) {
        ContactKind.EMAIL -> raw.trim().lowercase()
        ContactKind.PHONE -> raw.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
        ContactKind.UNKNOWN -> raw.trim()
    }

    /** The intent data for the notice's call action, or null when there is nothing honest to dial. */
    fun dialUri(raw: String): String? = when (kind(raw)) {
        ContactKind.PHONE -> "tel:${normalized(raw)}"
        ContactKind.EMAIL -> "mailto:${normalized(raw)}"
        ContactKind.UNKNOWN -> null
    }
}
```

- [ ] **Step 4: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/protection/TheftNoticeText.kt app/src/main/kotlin/com/shieldra/protection/OwnerContact.kt app/src/test/kotlin/com/shieldra/protection/TheftNoticeTextTest.kt app/src/test/kotlin/com/shieldra/protection/OwnerContactTest.kt
git commit -m "feat: compose the holder-facing theft notice in three languages"
```

---

### Task 3: The demand notice on the screen (Android side)

**Files:**
- Create: `app/src/main/kotlin/com/shieldra/protection/TheftDemand.kt`
- Modify: `app/src/main/res/values/strings.xml` (add the six `theft_demand_*` keys after `lost_contact`, around line 38)
- Modify: `app/src/main/res/values-ar/strings.xml` (same keys, Arabic)
- Create: `app/src/main/res/values-fr/strings.xml`
- Test: `app/src/test/kotlin/com/shieldra/protection/TheftDemandCopyTest.kt`

**Interfaces:**
- Consumes: `TheftNoticeText.compose(...)` and `OwnerContact.dialUri(...)` from Task 2; `SnatchLadder.DEMAND_MS` from Task 1.
- Produces: `object TheftDemand { const val NOTIFICATION_ID: Int; fun show(context: Context, contact: String, ownerMessage: String); fun hide(context: Context) }`. Task 8 calls both; Task 9's gate greps `TheftDemand.show`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/shieldra/protection/TheftDemandCopyTest.kt` — this is the JVM guard that the three copies say the same thing and make no promise the app cannot keep:

```kotlin
package com.shieldra.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notice is the only text this app shows to a person it does not know, in a moment of conflict.
 * Three locale files must carry the same keys, none may claim a wipe or a police call, and the demand
 * must state the real consequence: the owner gets the phone's location and its pictures.
 */
class TheftDemandCopyTest {

    private val res = File("app/src/main/res")

    @Test
    fun `every locale carries the same theft notice keys`() {
        val keys = listOf(
            "theft_demand_channel", "theft_demand_title", "theft_demand_body",
            "theft_demand_owner_message", "theft_demand_contact", "theft_demand_call",
        )
        for (locale in listOf("values", "values-ar", "values-fr")) {
            val text = File(res, "$locale/strings.xml").readText()
            for (key in keys) {
                assertTrue("$locale is missing $key", text.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the notice never claims something the app cannot do`() {
        for (locale in listOf("values", "values-ar", "values-fr")) {
            val text = File(res, "$locale/strings.xml").readText().lowercase()
            val body = text.substringAfter("name=\"theft_demand_body\"").substringBefore("</string>")
            assertTrue("$locale must not promise a wipe", !body.contains("wipe") && !body.contains("effacer"))
            assertTrue("$locale must not promise the police", !body.contains("police") && !body.contains("شرطة"))
        }
    }

    @Test
    fun `the contact key takes one placeholder`() {
        val body = File(res, "values/strings.xml").readText()
            .substringAfter("name=\"theft_demand_contact\"").substringBefore("</string>")
        assertEquals(1, Regex("%1\\$s").findAll(body).count())
    }

    @Test
    fun `the owner placeholder exists for his own sentence`() {
        val body = File(res, "values/strings.xml").readText()
            .substringAfter("name=\"theft_demand_owner_message\"").substringBefore("</string>")
        assertEquals(1, Regex("%1\\$s").findAll(body).count())
    }
}
```

- [ ] **Step 2: Confirm it fails**

Run: `cd /c/src/tmp/shieldra-work && grep -c "theft_demand_title" app/src/main/res/values/strings.xml || echo 0`
Expected: `0`.

- [ ] **Step 3: Add the English keys**

Modify `app/src/main/res/values/strings.xml`, inserting directly after the existing `<string name="lost_contact">…</string>` line:

```xml
    <string name="theft_demand_channel">Locked-phone notice</string>
    <string name="theft_demand_title">This phone locked after a confirmed movement</string>
    <string name="theft_demand_body">If you are the owner, unlock the phone: the alerts stop. If nobody unlocks it, the phone sends its location and its pictures to its owner, and keeps doing so.</string>
    <string name="theft_demand_owner_message">Owner’s note: %1$s</string>
    <string name="theft_demand_contact">Owner contact: %1$s</string>
    <string name="theft_demand_call">Call the owner</string>
```

- [ ] **Step 4: Add the Arabic keys**

Modify `app/src/main/res/values-ar/strings.xml`, at the same place in that file:

```xml
    <string name="theft_demand_channel">إشعار قفل الهاتف</string>
    <string name="theft_demand_title">قُفل هذا الهاتف بعد حركة مؤكَّدة</string>
    <string name="theft_demand_body">إن كنت المالك فافتح الهاتف لتتوقف التنبيهات. وإن لم يفتحه أحد، أرسل الهاتف موقعه وصوره إلى مالكه ويستمر في ذلك.</string>
    <string name="theft_demand_owner_message">ملاحظة المالك: %1$s</string>
    <string name="theft_demand_contact">جهة اتصال المالك: %1$s</string>
    <string name="theft_demand_call">اتصل بالمالك</string>
```

- [ ] **Step 5: Create the French file**

Create `app/src/main/res/values-fr/strings.xml` (the notice is the only French copy in this app for now: it is written for whoever holds the phone, not for the owner's own UI, which stays Arabic/English):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="theft_demand_channel">Avis de téléphone verrouillé</string>
    <string name="theft_demand_title">Ce téléphone s’est verrouillé après un mouvement confirmé</string>
    <string name="theft_demand_body">Si vous êtes le propriétaire, déverrouillez le téléphone : les alertes s’arrêtent. Sinon, le téléphone envoie sa position et ses photos à son propriétaire, et continue.</string>
    <string name="theft_demand_owner_message">Note du propriétaire : %1$s</string>
    <string name="theft_demand_contact">Contact du propriétaire : %1$s</string>
    <string name="theft_demand_call">Appeler le propriétaire</string>
</resources>
```

- [ ] **Step 6: Write the notice itself**

Create `app/src/main/kotlin/com/shieldra/protection/TheftDemand.kt`:

```kotlin
package com.shieldra.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import com.shieldra.app.R

/**
 * The notice the holder of a locked phone sees (A-45 D3). A public, ongoing notification with a live
 * timer: a full-screen takeover is refused to apps that are not calling or alarm apps since Android 14,
 * and this app is neither. It says what the phone is going to do, in the three languages the app ships,
 * because the app cannot know who is reading it.
 *
 * It is separate from [LostNotice]: that one is the owner's own message for a finder, turned on from
 * Telegram. This one is the consequence of a confirmed movement.
 */
object TheftDemand {

    private const val CHANNEL_ID = "shieldra_theft_demand"
    const val NOTIFICATION_ID = 20_021

    private val LOCALES = listOf("ar", "en", "fr")

    fun show(context: Context, contact: String, ownerMessage: String) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    app.getString(R.string.theft_demand_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val localized = LOCALES.map { locale -> app.forLocale(locale) }
        val body = TheftNoticeText.compose(
            arabic = localized[0].getString(R.string.theft_demand_body),
            english = localized[1].getString(R.string.theft_demand_body),
            french = localized[2].getString(R.string.theft_demand_body),
            ownerMessage = ownerMessage.trim().takeIf { it.isNotEmpty() }?.let { own ->
                app.getString(R.string.theft_demand_owner_message, own)
            },
            contactLine = contact.trim().takeIf { it.isNotEmpty() }?.let {
                app.getString(R.string.theft_demand_contact, OwnerContact.normalized(it))
            },
        )
        val notice = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(app.getString(R.string.theft_demand_title))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() + SnatchLadder.DEMAND_MS)
            .apply {
                OwnerContact.dialUri(contact)?.let { uri ->
                    addAction(
                        Notification.Action.Builder(
                            null,
                            app.getString(R.string.theft_demand_call),
                            PendingIntent.getActivity(
                                app,
                                0,
                                Intent(Intent.ACTION_DIAL, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ).build(),
                    )
                }
            }
            .build()
        manager.notify(NOTIFICATION_ID, notice)
    }

    fun hide(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** The same resources read in one fixed locale, so all three languages can be shown at once. */
    private fun Context.forLocale(tag: String): Context {
        val configuration = Configuration(resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(tag))
        }
        return createConfigurationContext(configuration)
    }
}
```

Note for the implementer: if CI reports the channel block fails on API 26 guards, keep the existing pattern used by `LostNotice.kt:21` (the `Build.VERSION.SDK_INT` check) rather than removing the check — do not delete a version guard to make a compile pass.

- [ ] **Step 7: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/protection/TheftDemand.kt app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml app/src/main/res/values-fr/strings.xml app/src/test/kotlin/com/shieldra/protection/TheftDemandCopyTest.kt
git commit -m "feat: tell the holder of a locked phone what happens and in three languages"
```

---

### Task 4: CI checkpoint for the pure layer

**Files:**
- Modify: nothing (verification task).

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: a green CI run id that Tasks 5–9 quote, and the test count for the roadmap.

- [ ] **Step 1: Push the branch**

```bash
cd /c/src/tmp/shieldra-work && git push origin fix/audit-round-1
```

- [ ] **Step 2: Dispatch CI**

```bash
cd /c/src/tmp/shieldra-work && gh workflow run group-a-diagnostics.yml --ref fix/audit-round-1 --repo toufikben/shieldra && sleep 15 && gh run list --repo toufikben/shieldra --branch fix/audit-round-1 --limit 1
```

- [ ] **Step 3: Watch it and read the real numbers**

```bash
cd /c/src/tmp/shieldra-work && gh run watch <run-id> --repo toufikben/shieldra --exit-status
gh run view <run-id> --repo toufikben/shieldra --log 2>/dev/null | grep -E "CI TEST SUMMARY|Group C boundary|PASS: A-4|G4-B|PHASE 3" | tail -10
```
Expected: `CI TEST SUMMARY: <N> tests, <N> passed, 0 failures, 0 errors` with `<N>` = the previous 578 + 19 new JVM tests (8 + 4 + 4 + 3 = 19 → 597), `G4-B LOCAL REVIEW PASS`, `PHASE 3 STATIC REVIEW PASS`. If a number differs, quote what CI printed, not what was planned.

- [ ] **Step 4: If a test fails, fix the cause and re-run — never delete the assertion**

A red ladder test means the timing is wrong or the test states a different intent than D1. Read the assertion, decide which one is wrong against `docs/superpowers/specs/2026-11-04-snatch-ladder-design.md`, fix that, commit `fix:` and dispatch again.

---

### Task 5: Zone policy (pure) and the zone state the guard can read

**Files:**
- Create: `app/src/main/kotlin/com/shieldra/protection/ZonePolicy.kt`
- Create: `app/src/main/kotlin/com/shieldra/protection/ZoneStateStore.kt`
- Modify: `app/src/main/kotlin/com/shieldra/protection/ProtectionSettings.kt` (add nothing to the settings data class — the zone sample is a separate store, so a settings `Save` can never rewrite it; this is the A-44 lesson)
- Test: `app/src/test/kotlin/com/shieldra/protection/ZonePolicyTest.kt`

**Interfaces:**
- Consumes: nothing from Android.
- Produces: `enum class ZoneResponse { FULL, QUIET, MUTED }`; `object ZonePolicy { fun responseFor(zonesExist: Boolean, stateKnown: Boolean, inside: Boolean, charging: Boolean): ZoneResponse }`; `data class ZoneSample(val inside: Boolean, val atMs: Long) { fun isFresh(nowMs: Long, staleAfterMs: Long): Boolean }`; `class ZoneStateStore(context: Context) { fun write(inside: Boolean, atMs: Long); fun read(): ZoneSample?; fun clear() }`. Task 8 and Task 6 use them.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/shieldra/protection/ZonePolicyTest.kt`:

```kotlin
package com.shieldra.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-45 D2: a safe zone mutes escalation, never detection, and an unknown zone fails closed. The most
 * likely snatch is a phone taken off a bedside table — inside the zone — so silence there would be the
 * whole feature turned off. What is muted is the noise aimed at the neighbourhood, not the evidence.
 */
class ZonePolicyTest {

    @Test
    fun `no zones at all means the full ladder`() {
        assertEquals(ZoneResponse.FULL, ZonePolicy.responseFor(zonesExist = false, stateKnown = true, inside = true, charging = false))
    }

    @Test
    fun `an unknown zone state fails closed to the full ladder`() {
        assertEquals(ZoneResponse.FULL, ZonePolicy.responseFor(zonesExist = true, stateKnown = false, inside = true, charging = true))
    }

    @Test
    fun `outside a zone is the full ladder even while charging`() {
        assertEquals(ZoneResponse.FULL, ZonePolicy.responseFor(zonesExist = true, stateKnown = true, inside = false, charging = true))
    }

    @Test
    fun `inside a zone and charging is the only fully quiet case`() {
        assertEquals(ZoneResponse.MUTED, ZonePolicy.responseFor(zonesExist = true, stateKnown = true, inside = true, charging = true))
    }

    @Test
    fun `inside a zone and unplugged still locks and still sends evidence`() {
        assertEquals(ZoneResponse.QUIET, ZonePolicy.responseFor(zonesExist = true, stateKnown = true, inside = true, charging = false))
    }

    @Test
    fun `a stale sample is not a fresh one and a fresh one is not stale`() {
        val sample = ZoneSample(inside = true, atMs = 1_000_000L)
        assertTrue(sample.isFresh(nowMs = 1_000_001L, staleAfterMs = 60_000L))
        assertFalse(sample.isFresh(nowMs = 1_060_001L, staleAfterMs = 60_000L))
    }

    @Test
    fun `a clock that jumped backwards does not make a stale sample look fresh`() {
        val sample = ZoneSample(inside = true, atMs = 1_000_000L)
        assertFalse(sample.isFresh(nowMs = 900_000L, staleAfterMs = 60_000L))
    }
}
```

- [ ] **Step 2: Confirm it cannot compile**

Run: `cd /c/src/tmp/shieldra-work && ls app/src/main/kotlin/com/shieldra/protection/ | grep -E "ZonePolicy|ZoneStateStore" || echo MISSING`
Expected: `MISSING`.

- [ ] **Step 3: Write the policy**

Create `app/src/main/kotlin/com/shieldra/protection/ZonePolicy.kt`:

```kotlin
package com.shieldra.protection

/** What the ladder is allowed to do given where the phone is. */
enum class ZoneResponse {
    /** The full ladder: lock, demand, bursts, repeats. */
    FULL,

    /** Lock and send the evidence, but no siren and no repeating burst train. */
    QUIET,

    /** Record the event and do nothing else. Only inside a zone and plugged in. */
    MUTED,
}

/**
 * The safe-zone decision (design D2).
 *
 * A zone is a statement about *who is near the phone*, not about whether it can be stolen: a phone
 * taken off a bedside table is taken inside a zone, and a thief who grabs a charging phone from a night
 * stand is the case the owner asked to keep quiet about being alerted all night. So detection is never
 * muted and the only fully silent case is inside-and-charging, where the phone is where it sleeps.
 *
 * Unknown fails closed. A missing permission, an old sample or a failed fix must not turn the guard off
 * — the owner chose the zones, but never chose "no protection when the phone cannot see itself".
 */
object ZonePolicy {

    fun responseFor(zonesExist: Boolean, stateKnown: Boolean, inside: Boolean, charging: Boolean): ZoneResponse = when {
        !zonesExist || !stateKnown || !inside -> ZoneResponse.FULL
        charging -> ZoneResponse.MUTED
        else -> ZoneResponse.QUIET
    }
}
```

- [ ] **Step 4: Write the store**

Create `app/src/main/kotlin/com/shieldra/protection/ZoneStateStore.kt`:

```kotlin
package com.shieldra.protection

import android.content.Context

/** One reading of "is the phone inside a safe zone", with the clock that came with it. */
data class ZoneSample(val inside: Boolean, val atMs: Long) {

    /** Fresh means recent and not from a clock that jumped backwards. */
    fun isFresh(nowMs: Long, staleAfterMs: Long): Boolean {
        if (nowMs < atMs) return false
        return nowMs - atMs <= staleAfterMs
    }
}

/**
 * Where the phone last was, written by the safe-zone sampler and read by the Motion ladder.
 *
 * It lives in its own preferences file, not in the protection settings, for the reason A-44 taught:
 * a settings `Save` rewrites a whole snapshot and must never be able to overwrite a live decision made
 * somewhere else. The sampler has exactly one writer here and the ladder only reads.
 */
class ZoneStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE)

    fun write(inside: Boolean, atMs: Long) {
        prefs.edit().putBoolean(KEY_INSIDE, inside).putLong(KEY_AT_MS, atMs).apply()
    }

    fun read(): ZoneSample? {
        if (!prefs.contains(KEY_AT_MS) || !prefs.contains(KEY_INSIDE)) return null
        return ZoneSample(inside = prefs.getBoolean(KEY_INSIDE, false), atMs = prefs.getLong(KEY_AT_MS, 0L))
    }

    fun clear() {
        prefs.edit().remove(KEY_INSIDE).remove(KEY_AT_MS).apply()
    }

    private companion object {
        const val PREFERENCE = "shieldra_zone_state"
        const val KEY_INSIDE = "zone_inside"
        const val KEY_AT_MS = "zone_at_ms"
    }
}
```

- [ ] **Step 5: Register the new preferences file with the erase gate**

`eraseAll` must wipe it, or a factory-reset-style erase leaves the last known zone behind. Modify the erase source that `g4b-review-check.sh` reads (the file defining `OWNER_PREFERENCES = listOf(` and `DEVICE_PROTECTED_PREFERENCE = `): add `"shieldra_zone_state"` to the `OWNER_PREFERENCES` list.

Run first to find it: `cd /c/src/tmp/shieldra-work && grep -rln "OWNER_PREFERENCES = listOf(" app/src/main/kotlin`
Then add the entry inside the list, next to the other `"shieldra_…"` names.

- [ ] **Step 6: Confirm the local gate still passes**

Run: `cd /c/src/tmp/shieldra-work && bash .github/scripts/g4b-review-check.sh 2>&1 | tail -15`
Expected: `G4-B LOCAL REVIEW PASS — runtime/device evidence still required` and no `FAIL` line about preferences stores. The B-3 block in that script fails loudly if a declared store is missing from `eraseAll`, so this is the check that the registration worked.

- [ ] **Step 7: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/protection/ZonePolicy.kt app/src/main/kotlin/com/shieldra/protection/ZoneStateStore.kt app/src/test/kotlin/com/shieldra/protection/ZonePolicyTest.kt && git add -p  # then stage the erase-list file edited in Step 5
git commit -m "feat: make safe zones mute escalation and fail closed when unknown"
```

---

### Task 6: Make the zone sampler trustworthy

**Files:**
- Modify: `app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt:95-134` (the geofence block)
- Create: `app/src/main/kotlin/com/shieldra/protection/GeofenceTicker.kt`
- Modify: `app/src/main/AndroidManifest.xml` (register the new receiver)
- Modify: `app/src/main/res/values/strings.xml` + `values-ar/strings.xml` (one new key `safe_zone_sampler_off`)

**Interfaces:**
- Consumes: `GeofenceLogic.hasLeftSafeZone(...)`, `GeofenceLogic.nextInsideState(...)`, `ProtectionSettingsStore(app).load().safeZones()` and `.geofenceIntervalMinutes`, `ZoneStateStore` from Task 5.
- Produces: `object GeofenceTicker { const val REQUEST_CODE: Int; const val STALE_FACTOR: Long; fun schedule(context: Context, intervalMinutes: Int); fun cancel(context: Context) }` and the alarm receiver `GeofenceTickReceiver`. Task 8 reads only `ZoneStateStore`.

- [ ] **Step 1: Write the ticker**

Create `app/src/main/kotlin/com/shieldra/protection/GeofenceTicker.kt`:

```kotlin
package com.shieldra.protection

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * The safe-zone clock, kept outside the service process.
 *
 * The old sampler was a `Handler` inside `ProtectionService`: it died with the process, drifted in Doze,
 * and its "was inside" flag lived in a field, so every restart silently assumed the phone was at home
 * again. A ladder decision that reads that state must not depend on how long the service has happened to
 * be alive, so the tick is an alarm and the state is on disk.
 *
 * Exact alarms are used when the owner allowed them (`SCHEDULE_EXACT_ALARM` is declared) and inexact
 * ones when not: a late zone reading is a worse ladder decision than no decision at all, which is why
 * [STALE_FACTOR] exists and why an unknown zone fails closed in [ZonePolicy].
 */
object GeofenceTicker {

    const val REQUEST_CODE = 4102
    /** A sample older than this many intervals is no longer trusted. */
    const val STALE_FACTOR: Long = 2L

    fun nextDelayMs(intervalMinutes: Int): Long = intervalMinutes.coerceAtLeast(1) * 60_000L

    fun staleAfterMs(intervalMinutes: Int): Long = nextDelayMs(intervalMinutes) * STALE_FACTOR

    fun schedule(context: Context, intervalMinutes: Int) {
        val manager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + nextDelayMs(intervalMinutes)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setWindow(
                    AlarmManager.WINDOW_INFERRED_TRIGGER_ATTEMPT_TIME,
                    AlarmManager.WINDOW_MAX_WINDOW_LENGTH_MILLIS,
                    triggerAt,
                    pendingIntent(context),
                )
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
            }
        }.onFailure { Log.w(TAG, "the safe-zone tick could not be scheduled: ${it.message}") }
    }

    fun cancel(context: Context) {
        val manager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }

    fun pendingIntent(context: Context): android.app.PendingIntent = android.app.PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, GeofenceTickReceiver::class.java),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val TAG = "GeofenceTicker"
}

/** Runs one safe-zone reading when the alarm fires. */
class GeofenceTickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        if (!settings.armed || !settings.geofenceEnabled) return
        ProtectionService.sampleSafeZoneNow(app)
        GeofenceTicker.schedule(app, settings.geofenceIntervalMinutes)
    }
}
```

- [ ] **Step 2: Register it**

Modify `app/src/main/AndroidManifest.xml`, adding beside the other `<receiver>` entries inside `<application>`:

```xml
        <receiver
            android:name="com.shieldra.protection.GeofenceTickReceiver"
            android:exported="false" />
```

- [ ] **Step 3: Replace the in-process sampler**

Modify `app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt`. Delete the `geofenceHandler` recursion in the block that currently starts at line 95 and ends at line 134, and replace it with a sampling function the service calls once at start and the receiver calls afterwards. The replacement must: skip when there are no zones; treat a missing fix as "unknown" (write nothing, leave the stored sample to go stale on its own); write the new inside state plus its timestamp to `ZoneStateStore`; keep the existing one-shot "left a safe zone" alert exactly as it is; and schedule the next tick through `GeofenceTicker`:

```kotlin
        /**
         * One safe-zone reading (A-45): where the phone is, whether it just left, and when this was
         * known. A fix that is older than two intervals is refused rather than trusted, because the
         * ladder now reads this state and a stale "inside" would mute a snatch at the door.
         */
        fun sampleSafeZoneNow(context: Context) {
            val app = context.applicationContext
            val settings = ProtectionSettingsStore(app).load()
            val zones = settings.safeZones()
            if (zones.isEmpty()) return
            val fix = runCatching { LocationFix(app).current() }.getOrNull() ?: return
            val nowMs = System.currentTimeMillis()
            if (fix.fixedAt != null && nowMs - fix.fixedAt.toEpochMilli() > GeofenceTicker.staleAfterMs(settings.geofenceIntervalMinutes)) {
                Log.w(TAG, "the safe-zone fix was too old to trust, so the zone state was left to go stale")
                return
            }
            val wasInside = ZoneStateStore(app).read()
            val inside = GeofenceLogic.nextInsideState(fix.latitude, fix.longitude, fix.accuracyMeters, zones, wasInside?.inside ?: true)
            val left = GeofenceLogic.hasLeftSafeZone(fix.latitude, fix.longitude, fix.accuracyMeters, zones, wasInside?.inside ?: true)
            ZoneStateStore(app).write(inside = inside, atMs = nowMs)
            if (left) {
                scope.launch {
                    val base = AlertRuntime.snapshot(app, AlertKind.TRACKING, null)
                    AlertRuntime.alert(app, base.copy(occurredAt = Instant.now(), location = fix, evidenceAttempted = false, photoEvidenceId = null))
                }
            }
        }
```

Wire the start path: in `onStartCommand`, where the old block was scheduled, call `if (loadedSettings.geofenceEnabled && loadedSettings.safeZones().isNotEmpty()) { sampleSafeZoneNow(applicationContext); GeofenceTicker.schedule(applicationContext, loadedSettings.geofenceIntervalMinutes) }`, and in `onDestroy` keep `GeofenceTicker.cancel` out — the alarm must survive the service so the state stays fresh for the next arm — while `disarm()` in `ProtectionRuntime` calls `GeofenceTicker.cancel(applicationContext)` and `ZoneStateStore(applicationContext).clear()`.

Verify the argument names you must match before pasting: `cd /c/src/tmp/shieldra-work && grep -n "fun hasLeftSafeZone\|fun nextInsideState" app/src/main/kotlin/com/shieldra/protection/GeofenceLogic.kt && grep -n "val fixedAt\|accuracyMeters\|class LocationFix\|fun current" app/src/main/kotlin/com/shieldra/protection/LocationFix.kt && grep -n "fun safeZones" app/src/main/kotlin/com/shieldra/protection/ProtectionSettings.kt`
If those names differ from the snippet, use the real names from the grep output — do not rename production APIs to fit this plan.

- [ ] **Step 4: Make the silent failure visible**

The old sampler did nothing forever when there was no location permission. Add one string and show it where the zones are managed.

`values/strings.xml`: `<string name="safe_zone_sampler_off">No zone readings yet: the phone cannot get a location right now, so the guards behave as if it were outside.</string>`
`values-ar/strings.xml`: `<string name="safe_zone_sampler_off">لا قراءات للمنطقة بعد: لا يستطيع الهاتف تحديد موقعه الآن، فتتصرّف الحرّاس كأنه خارجها.</string>`

Modify `app/src/main/kotlin/com/shieldra/app/presentation/screen/map/MapScreen.kt`: under the existing safe-zone switch row, show that text as a small attention-coloured line when `geofenceEnabled` is on and `ZoneStateStore(context).read()` is null or older than `GeofenceTicker.staleAfterMs(settings.geofenceIntervalMinutes)`.

- [ ] **Step 5: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/protection/GeofenceTicker.kt app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt app/src/main/kotlin/com/shieldra/protection/ProtectionRuntime.kt app/src/main/AndroidManifest.xml app/src/main/kotlin/com/shieldra/app/presentation/screen/map/MapScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml
git commit -m "fix: the safe-zone state now survives the service and goes stale honestly"
```

---

### Task 7: CI checkpoint two, then the recorder

**Files:**
- Create: `app/src/androidTest/kotlin/com/shieldra/detection/MotionLiftRecorderInstrumentedTest.kt`
- Create: `app/src/main/kotlin/com/shieldra/detection/MotionTiltProbe.kt`
- Test: `app/src/test/kotlin/com/shieldra/detection/MotionTiltProbeTest.kt`

**Interfaces:**
- Consumes: `MotionSensorAdapter` (existing) for raw samples.
- Produces: `object MotionTiltProbe { const val STANDARD_GRAVITY_MS2: Double; fun magnitude(x: Double, y: Double, z: Double): Double; fun angleDegrees(base: DoubleArray, sample: DoubleArray): Double; fun restingBaseline(samples: List<DoubleArray>): DoubleArray?; fun noiseSigma(samples: List<DoubleArray>): Double }` and a logcat line format `SHIELDRA_LIFT t=<ms> dev=<m/s2> tilt=<deg> mag=<m/s2>`.

- [ ] **Step 1: Push and dispatch CI for Tasks 5–6**

```bash
cd /c/src/tmp/shieldra-work && git push origin fix/audit-round-1 && gh workflow run group-a-diagnostics.yml --ref fix/audit-round-1 --repo toufikben/shieldra && sleep 15 && gh run list --repo toufikben/shieldra --branch fix/audit-round-1 --limit 1
```
Expected at the end: `CI TEST SUMMARY` with 597 + 7 (ZonePolicy) = 604 tests, 0 failures, and both static reviews passing.

- [ ] **Step 2: Write the failing tilt test**

Create `app/src/test/kotlin/com/shieldra/detection/MotionTiltProbeTest.kt`:

```kotlin
package com.shieldra.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The measurement A-45 D4 needs before any threshold moves: how far the gravity direction has turned
 * from the pose the phone was resting in. Orientation-free, and it must read 0 degrees for the same
 * pose flipped end over end, because a phone lying face-down is still lying.
 */
class MotionTiltProbeTest {

    private val gravityUp = doubleArrayOf(0.0, 0.0, 9.81)
    private val gravityOnSide = doubleArrayOf(9.81, 0.0, 0.0)

    @Test
    fun `the same pose is zero degrees`() {
        assertEquals(0.0, MotionTiltProbe.angleDegrees(gravityUp, gravityUp), 0.001)
    }

    @Test
    fun `a quarter turn is ninety degrees`() {
        assertEquals(90.0, MotionTiltProbe.angleDegrees(gravityUp, gravityOnSide), 0.5)
    }

    @Test
    fun `an upside down phone is one hundred and eighty degrees however heavy it reads`() {
        assertEquals(180.0, MotionTiltProbe.angleDegrees(gravityUp, doubleArrayOf(0.0, 0.0, -9.81)), 0.5)
    }

    @Test
    fun `magnitude does not care which way the axes point`() {
        assertEquals(9.81, MotionTiltProbe.magnitude(9.81, 0.0, 0.0), 0.001)
        assertEquals(9.81, MotionTiltProbe.magnitude(0.0, 0.0, 9.81), 0.001)
    }

    @Test
    fun `a baseline needs samples and returns their mean pose`() {
        assertNull(MotionTiltProbe.restingBaseline(emptyList()))
        val baseline = MotionTiltProbe.restingBaseline(listOf(doubleArrayOf(0.0, 0.0, 9.81), doubleArrayOf(0.0, 0.0, 9.91)))
        assertEquals(9.86, baseline!![2], 0.001)
    }

    @Test
    fun `noise is spread around the baseline, not a size`() {
        val quiet = MotionTiltProbe.restingBaseline(listOf(doubleArrayOf(0.0, 0.0, 9.81), doubleArrayOf(0.0, 0.0, 9.82)))!!
        val sigma = MotionTiltProbe.noiseSigma(listOf(quiet, doubleArrayOf(0.0, 0.0, 9.83)))
        assertTrue(sigma < 0.1)
    }

    @Test
    fun `a zero-length vector never divides`() {
        assertEquals(0.0, MotionTiltProbe.angleDegrees(doubleArrayOf(0.0, 0.0, 0.0), gravityUp), 0.001)
    }
}
```

- [ ] **Step 3: Write the probe**

Create `app/src/main/kotlin/com/shieldra/detection/MotionTiltProbe.kt`:

```kotlin
package com.shieldra.detection

import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Gravity-relative measurement for the Motion guard (A-45 D4).
 *
 * The shipped rule measures how hard the phone is moved, which cannot tell a lift from a bump: the
 * literature on a single force threshold puts sensitivity and specificity in a trade-off no constant
 * resolves. A change in the *direction* of gravity is a different fact — a phone standing still on its
 * side and a phone standing still flat have the same magnitude and a 90 degree difference in pose — and
 * it is what "someone picked it up" actually means. AOSP's own tilt detector is defined exactly this way
 * (a 2-second average gravity vector that moved by 35 degrees or more).
 *
 * Pure, and deliberately unused by the shipped rule until Task 9's measurements say what the constant
 * should be.
 */
object MotionTiltProbe {

    const val STANDARD_GRAVITY_MS2 = 9.80665

    fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    /** The angle in degrees between two acceleration vectors; 0 when either is degenerate. */
    fun angleDegrees(base: DoubleArray, sample: DoubleArray): Double {
        if (base.size != 3 || sample.size != 3) return 0.0
        val baseMag = magnitude(base[0], base[1], base[2])
        val sampleMag = magnitude(sample[0], sample[1], sample[2])
        if (baseMag < 0.001 || sampleMag < 0.001) return 0.0
        val dot = (base[0] * sample[0] + base[1] * sample[1] + base[2] * sample[2]) / (baseMag * sampleMag)
        return Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
    }

    /** The resting pose: the mean vector of the samples taken while the phone lay still. */
    fun restingBaseline(samples: List<DoubleArray>): DoubleArray? {
        if (samples.isEmpty()) return null
        val axes = DoubleArray(3)
        for (sample in samples) {
            if (sample.size != 3) return null
            for (i in 0..2) axes[i] += sample[i]
        }
        return DoubleArray(3) { axes[it] / samples.size }
    }

    /** The scatter around the resting pose, in m/s2 — the noise floor of this table, this time of day. */
    fun noiseSigma(samples: List<DoubleArray>): Double {
        val baseline = restingBaseline(samples) ?: return 0.0
        if (samples.size < 2) return 0.0
        val sum = samples.sumOf { sample ->
            (0..2).sumOf { axis -> (sample[axis] - baseline[axis]) * (sample[axis] - baseline[axis]) }
        }
        return sqrt(sum / (max(1, samples.size * 3)))
    }
}
```

- [ ] **Step 4: Write the recorder**

Create `app/src/androidTest/kotlin/com/shieldra/detection/MotionLiftRecorderInstrumentedTest.kt`. It follows the existing instrumented style in this folder (a listener, a latch, a log line per sample) and is measurement-only: it must assert nothing about the device, or it becomes a test that fails on a quiet desk.

```kotlin
package com.shieldra.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

/**
 * Records real lift and snatch samples on the owner's handset so the Motion constants can be set from
 * measurement instead of from a guess (A-45 D4). The shipped calibration question is still unanswered on
 * this device — `MotionCalibrationInstrumentedTest` prints `q_g1=UNANSWERED` — so until this recorder
 * has been run by a human lifting a phone, no threshold change is trustworthy.
 *
 * The operator's script, read out over adb while the test runs: let the phone lie still for 8 seconds,
 * lift it slowly, put it back, wait 4 seconds, then take it quickly off the table and turn it over.
 * Every sample becomes one log line so the pause, the lift and the snatch can be told apart afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MotionLiftRecorderInstrumentedTest {

    @Test
    fun records_a_resting_pose_then_every_sample_until_the_window_closes() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = context.getSystemService(SensorManager::class.java)!!
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        val deadlineMs = TimeUnit.SECONDS.toMillis(RECORD_SECONDS)
        val startedAt = System.currentTimeMillis()
        val restSamples = mutableListOf<DoubleArray>()
        val latch = CountDownLatch(1)
        var baseline: DoubleArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                val vector = doubleArrayOf(event.values[0], event.values[1], event.values[2])
                if (now - startedAt < REST_SECONDS * 1000) {
                    restSamples.add(vector)
                    return
                }
                if (baseline == null) {
                    baseline = MotionTiltProbe.restingBaseline(restSamples)
                    Log.i(TAG, "SHIELDRA_LIFT_BASELINE n=${restSamples.size} x=${baseline!![0]} y=${baseline!![1]} z=${baseline!![2]} sigma=${MotionTiltProbe.noiseSigma(restSamples)}")
                }
                val magnitude = MotionTiltProbe.magnitude(vector[0], vector[1], vector[2])
                Log.i(
                    TAG,
                    "SHIELDRA_LIFT t=${now - startedAt} dev=${abs(magnitude - MotionTiltProbe.STANDARD_GRAVITY_MS2)} " +
                        "tilt=${MotionTiltProbe.angleDegrees(baseline!!, vector)} mag=$magnitude",
                )
                if (now - startedAt >= deadlineMs) latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, accelerometer, 20_000)
        val finished = latch.await(RECORD_SECONDS + 15L, TimeUnit.SECONDS)
        manager.unregisterListener(listener)
        Log.i(TAG, "SHIELDRA_LIFT_DONE rest_samples=${restSamples.size} finished=$finished")
    }

    private companion object {
        const val TAG = "ShieldraMotion"
        const val REST_SECONDS = 8L
        const val RECORD_SECONDS = 30L
    }
}
```

- [ ] **Step 5: Commit and push (CI compiles androidTest too)**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/detection/MotionTiltProbe.kt app/src/test/kotlin/com/shieldra/detection/MotionTiltProbeTest.kt app/src/androidTest/kotlin/com/shieldra/detection/MotionLiftRecorderInstrumentedTest.kt
git commit -m "feat: measure the gravity tilt a lift produces, ready for real samples"
```

---

### Task 8: Run the ladder (the responder)

**Files:**
- Modify: `app/src/main/kotlin/com/shieldra/protection/TableModeResponder.kt` (whole file rewritten below)
- Modify: `app/src/main/kotlin/com/shieldra/detection/MotionMonitoringService.kt:265-280` (a bound repeat runs the ladder too)
- Modify: `app/src/main/kotlin/com/shieldra/protection/ProtectionRuntime.kt` (disarm cancels the ladder and the notice)

**Interfaces:**
- Consumes: `SnatchLadder.next/burstAtMs/DEMAND_MS`, `ZonePolicy.responseFor`, `ZoneStateStore.read`, `ZoneSample.isFresh`, `TheftDemand.show/hide`, `ProtectionService.captureForEvent(eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)`, `AlertRuntime.snapshot/alert`, `DeviceScreenLock.now`, `Siren.start/stop`, `ProtectionSettingsStore(app).load()`.
- Produces: `object TableModeResponder { fun onMotionConfirmed(context: Context, eventId: String); fun cancel(context: Context) }` — same two call sites as today, so nothing else needs to change to compile.

- [ ] **Step 1: Rewrite the responder**

Replace `app/src/main/kotlin/com/shieldra/protection/TableModeResponder.kt` with:

```kotlin
package com.shieldra.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime
import com.shieldra.domain.SecurityEventType

/**
 * Runs the owner's response when the Motion guard confirms the phone was moved (A-33, restructured by
 * A-45 D1).
 *
 * The ladder, not one shot: the screen locks and the holder is told what happens next, and only if
 * nobody unlocks does the camera, the microphone and the fix start being spent — then on a widening
 * cadence, because a frame of a running thief is worth less than the tenth position the owner can act
 * on. An owner who unlocks stops all of it.
 *
 * Three things are preserved from the earlier behaviour and are pinned by g4b: the lock goes through
 * [DeviceScreenLock] and only while the stored armed flag says protection is on; the grace response is
 * still the only one whose lock waits for the owner; and the capture still goes through the live
 * [ProtectionService] so the photo and the fix attach to the Motion event rather than floating free.
 */
object TableModeResponder {

    private const val TAG = "TableModeResponder"

    private val handler = Handler(Looper.getMainLooper())
    private var step: Runnable? = null
    private var unlockReceiver: BroadcastReceiver? = null

    private const val SIREN_MAX_MS = 30_000L

    /** The incident the ladder is running for, and when it began. */
    private var incidentStartedAt = 0L
    private var burstsDone = 0

    @Synchronized
    fun onMotionConfirmed(context: Context, eventId: String) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        if (!settings.armed) {
            Log.i(TAG, "the confirmed Motion found protection disarmed, so nothing was run")
            return
        }
        when (zoneResponse(app, settings)) {
            ZoneResponse.MUTED -> {
                Log.i(TAG, "the confirmed Motion was inside a safe zone with the phone charging: recorded, not escalated")
                return
            }
            ZoneResponse.QUIET -> startQuiet(app, settings, eventId)
            ZoneResponse.FULL -> startFull(app, settings, eventId)
        }
    }

    /** D2: a zone mutes escalation only, and an unknown zone is treated as outside. */
    private fun zoneResponse(context: Context, settings: ProtectionSettings): ZoneResponse {
        val zones = settings.safeZones()
        val sample = ZoneStateStore(context).read()
        val staleAfterMs = GeofenceTicker.staleAfterMs(settings.geofenceIntervalMinutes)
        val known = sample != null && sample.isFresh(System.currentTimeMillis(), staleAfterMs)
        return ZonePolicy.responseFor(
            zonesExist = zones.isNotEmpty() && settings.geofenceEnabled,
            stateKnown = known,
            inside = sample?.inside ?: false,
            charging = BatteryLevel.isCharging(context),
        )
    }

    /** Inside a zone, unplugged: the phone is still taken; the neighbours just are not alerted. */
    private fun startQuiet(context: Context, settings: ProtectionSettings, eventId: String) {
        captureAndAlert(context, eventId, siren = false)
        if (MotionResponsePlan.of(settings.motionResponse).lockAtOnce) lockTheScreen(context)
        TheftDemand.show(context, settings.ownerContact, settings.lostMessage)
        registerUnlockCancel(context)
    }

    /** The full ladder: demand now, burst at 30 s, then the widening train. */
    private fun startFull(context: Context, settings: ProtectionSettings, eventId: String) {
        val plan = MotionResponsePlan.of(settings.motionResponse)
        incidentStartedAt = SystemClock.elapsedRealtime()
        burstsDone = 0

        // t=0: lock (unless the owner chose the grace response), tell the holder, and let the owner know
        // now rather than in half a minute. The camera is deliberately not part of this step.
        if (plan.sirenNow) {
            Siren.start(context)
            handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
        }
        AlertRuntime.snapshot(context, AlertKind.MOTION, eventId).let { base ->
            AlertRuntime.alert(context, base)
        }
        if (plan.waitForOwnerUnlock) {
            // The grace response keeps its meaning: the lock comes after the owner's own window, and
            // unlocking inside it still cancels everything. A-45 makes the window visible, which is what
            // made it unusable before — a holder with no notice cannot be expected to unlock.
            TheftDemand.show(context, settings.ownerContact, settings.lostMessage)
        } else {
            lockTheScreen(context)
            TheftDemand.show(context, settings.ownerContact, settings.lostMessage)
        }
        registerUnlockCancel(context)
        scheduleNext(context, eventId)
    }

    private fun scheduleNext(context: Context, eventId: String) {
        val elapsedMs = SystemClock.elapsedRealtime() - incidentStartedAt
        val tick = SnatchLadder.next(elapsedMs, burstsDone)
        if (tick.action == LadderAction.SETTLED) {
            Log.i(TAG, "the motion ladder settled after $burstsDone burst(s)")
            return
        }
        step?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            burstsDone = tick.burst
            captureAndAlert(context, eventId, siren = MotionResponsePlan.of(
                ProtectionSettingsStore(context).load().motionResponse,
            ).sirenAfterGrace)
            // The grace response's lock is its own decision, and it now lands with the first burst.
            val plan = MotionResponsePlan.of(ProtectionSettingsStore(context).load().motionResponse)
            if (plan.lockAfterGrace && burstsDone == 1) lockTheScreen(context)
            scheduleNext(context, eventId)
        }
        step = runnable
        handler.postDelayed(runnable, tick.delayMs)
    }

    /** One step of evidence: a live service attaches photo and fix to the incident, text otherwise. */
    private fun captureAndAlert(context: Context, eventId: String, siren: Boolean) {
        if (siren) {
            Siren.start(context)
            handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
        }
        if (!ProtectionService.captureForEvent(eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)) {
            AlertRuntime.snapshot(context, AlertKind.MOTION, eventId).let { AlertRuntime.alert(context, it) }
        }
    }

    /** Locks only while protection is really armed: a late ladder step must not outlive the owner's choice. */
    private fun lockTheScreen(context: Context) {
        if (!ProtectionSettingsStore(context).load().armed) return
        val result = DeviceScreenLock.now(context)
        if (result != DeviceScreenLock.Result.LOCKED) {
            Log.w(TAG, "the confirmed Motion could not lock the screen: $result")
        }
    }

    /** Any owner unlock ends the ladder: the holder proved they are the owner. */
    @Synchronized
    private fun registerUnlockCancel(context: Context) {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = cancel(ctx.applicationContext)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            unlockReceiver = receiver
        }
    }

    private fun unregisterUnlockCancel(context: Context) {
        unlockReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        unlockReceiver = null
    }

    /** Stops a running ladder, silences the siren and takes the demand off the screen. */
    @Synchronized
    fun cancel(context: Context) {
        step?.let { handler.removeCallbacks(it) }
        step = null
        burstsDone = 0
        incidentStartedAt = 0L
        unregisterUnlockCancel(context)
        Siren.stop(context)
        TheftDemand.hide(context)
    }
}
```

Before committing, check the two helpers this file assumes exist; if either is missing, add it as a one-line reader rather than inlining a `BatteryManager` call in the policy path:

Run: `cd /c/src/tmp/shieldra-work && grep -rn "isCharging" app/src/main/kotlin/com/shieldra/protection/ | head -5`
If there is no `BatteryLevel.isCharging`, create `app/src/main/kotlin/com/shieldra/protection/BatteryLevel.kt` with:

```kotlin
package com.shieldra.protection

import android.content.Context
import android.os.BatteryManager

/** The charging question the zone policy asks, read from the sticky battery broadcast. */
object BatteryLevel {
    fun isCharging(context: Context): Boolean {
        val status = runCatching {
            context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        }.getOrNull()
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
}
```
(add `import android.content.Intent`, `import android.content.IntentFilter` to that file.)

- [ ] **Step 2: A bound repeat must run the ladder too**

Modify `app/src/main/kotlin/com/shieldra/detection/MotionMonitoringService.kt`. The `when (confirmation)` block currently sends `BoundToActive` to a silent branch. Change it so a bound movement also runs the response, and keep exactly one local notification per incident (the new incident already posts one; the repeat must not stack a second):

```kotlin
            is MotionConfirmation.BoundToActive -> {
                // A-45 D1: a movement that joined the open incident is not silence. A phone being
                // carried produces one of these after another, and the old behaviour was that the very
                // next movement after the confirmation did nothing at all — which is precisely the
                // snatch the owner described. It re-runs the ladder for the incident it belongs to;
                // no second local notification, because it is still the same incident.
                TableModeResponder.onMotionConfirmed(applicationContext, confirmation.event.id.value.toString())
            }
```

Match the real property path on `BoundToActive` before pasting:
Run: `cd /c/src/tmp/shieldra-work && grep -n "class BoundToActive\|BoundToActive(" app/src/main/kotlin/com/shieldra/domain/MotionIncidentBinder.kt`

- [ ] **Step 3: Disarm must stop the ladder and the notice**

Modify `ProtectionRuntime.disarm(...)` so the existing `TableModeResponder.cancel(context)` call (grace clearing) also clears the zone state, and the notice hiding happens inside `cancel` (Task 8 Step 1 already hides it there — do not call `TheftDemand.hide` from two places):

Run: `cd /c/src/tmp/shieldra-work && grep -n "fun disarm" -A 18 app/src/main/kotlin/com/shieldra/protection/ProtectionRuntime.kt`
Add `GeofenceTicker.cancel(applicationContext)` and `ZoneStateStore(applicationContext).clear()` next to the existing responder cancel.

- [ ] **Step 4: Re-run the static gate locally**

Run: `cd /c/src/tmp/shieldra-work && bash .github/scripts/g4b-review-check.sh 2>&1 | grep -E "FAIL|A-43|A-44" | head -10`
Expected: no `FAIL` line naming A-43 or A-44 pins. If A-43's "single guarded caller" pin fails, the lock call site moved — fix the call site, not the gate.

- [ ] **Step 5: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/protection/TableModeResponder.kt app/src/main/kotlin/com/shieldra/protection/BatteryLevel.kt app/src/main/kotlin/com/shieldra/detection/MotionMonitoringService.kt app/src/main/kotlin/com/shieldra/protection/ProtectionRuntime.kt
git commit -m "feat: a confirmed motion now locks, demands an unlock and escalates"
```

---

### Task 9: The contact field and the settings copy

**Files:**
- Modify: `app/src/main/kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt:331-348`
- Modify: `app/src/main/res/values/strings.xml`, `values-ar/strings.xml` (three keys: `protection_owner_contact_hint`, `protection_owner_contact_error`, `protection_theft_notice_note`)
- Test: `app/src/test/kotlin/com/shieldra/app/presentation/screen/settings/OwnerContactCopyTest.kt`

**Interfaces:**
- Consumes: `OwnerContact.kind/normalized` from Task 2, `SnatchLadder.DEMAND_MS` from Task 1.
- Produces: nothing consumed later.

- [ ] **Step 1: Write the failing copy test**

Create `app/src/test/kotlin/com/shieldra/app/presentation/screen/settings/OwnerContactCopyTest.kt`:

```kotlin
package com.shieldra.app.presentation.screen.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contact field is where the owner writes the thing a stranger will dial, so the hint has to name
 * the two accepted forms and the error has to say what the app will not do with anything else.
 */
class OwnerContactCopyTest {

    @Test
    fun `both locales carry the field copy`() {
        val keys = listOf("protection_owner_contact_hint", "protection_owner_contact_error", "protection_theft_notice_note")
        for (locale in listOf("values", "values-ar", "values-fr")) {
            val text = File("app/src/main/res/$locale/strings.xml").readText()
            for (key in keys) assertTrue("$locale is missing $key", text.contains("name=\"$key\""))
        }
    }

    @Test
    fun `the hint names a phone and an address`() {
        val text = File("app/src/main/res/values/strings.xml").readText()
            .substringAfter("name=\"protection_owner_contact_hint\"").substringBefore("</string>")
        assertTrue("the hint must name the country-code form", text.contains("+"))
        assertTrue("the hint must name an email", text.contains("@"))
    }
}
```

- [ ] **Step 2: Add the copy**

`values/strings.xml` (next to `protection_owner_contact`):
```xml
    <string name="protection_owner_contact_hint">A phone number with its country code (like +213 555 12 34 56) or an email address</string>
    <string name="protection_owner_contact_error">Neither a phone number nor an address: the notice will show it, but there will be no call button.</string>
    <string name="protection_theft_notice_note">When the Motion guard locks the phone, this is what its holder is shown — in Arabic, English and French, with your contact once.</string>
```
`values-ar/strings.xml`:
```xml
    <string name="protection_owner_contact_hint">رقم هاتف بمفتاح الدولة (مثل ‎+213 555 12 34 56‎) أو بريد إلكتروني</string>
    <string name="protection_owner_contact_error">ليس رقم هاتف ولا بريدا: سيظهر الإشعار نصّك لكن بلا زر اتصال.</string>
    <string name="protection_theft_notice_note">حين يقفل حارس الحركة الهاتف، هذا ما يُعرض على حامله — بالعربية والإنجليزية والفرنسية، مع جهة اتصالك مرة واحدة.</string>
```
`values-fr/strings.xml`:
```xml
    <string name="protection_owner_contact_hint">Un numéro avec l’indicatif pays (comme +213 555 12 34 56) ou une adresse e-mail</string>
    <string name="protection_owner_contact_error">Ni numéro ni adresse : l’avis affichera votre texte, sans bouton d’appel.</string>
    <string name="protection_theft_notice_note">Quand le garde-mouvement verrouille le téléphone, voici ce que voit son porteur — en arabe, en anglais et en français, avec votre contact une fois.</string>
```

- [ ] **Step 3: Wire the field**

Modify `ProtectionSetupScreen.kt`: the contact `ShieldraTextField` gains the hint as its supporting text and a red error line when `OwnerContact.kind(draft.ownerContact)` is `ContactKind.UNKNOWN` and the value is not blank; the saved value is normalized on save. Replace the existing two-field block (lines 331–348) with:

```kotlin
        if (draft.remoteCommandsEnabled) {
            Text(
                text = stringResource(R.string.protection_remote_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.s))
            val contactKind = com.shieldra.protection.OwnerContact.kind(draft.ownerContact)
            ShieldraTextField(
                value = draft.ownerContact,
                onValueChange = { draft = draft.copy(ownerContact = it) },
                label = stringResource(R.string.protection_owner_contact),
            )
            Text(
                text = if (draft.ownerContact.isBlank() || contactKind != com.shieldra.protection.ContactKind.UNKNOWN)
                    stringResource(R.string.protection_owner_contact_hint)
                else
                    stringResource(R.string.protection_owner_contact_error),
                style = MaterialTheme.typography.bodySmall,
                color = if (draft.ownerContact.isNotBlank() && contactKind == com.shieldra.protection.ContactKind.UNKNOWN)
                    ShieldraTheme.semantic.attention
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.s))
            ShieldraTextField(
                value = draft.lostMessage,
                onValueChange = { draft = draft.copy(lostMessage = it) },
                label = stringResource(R.string.protection_lost_message),
            )
            Text(
                text = stringResource(R.string.protection_theft_notice_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

and in the save path of the same screen, keep the contact as typed (do not silently rewrite what the owner wrote) — normalizing belongs to the notice, which already calls `OwnerContact.normalized` in Task 3.

- [ ] **Step 4: Commit**

```bash
cd /c/src/tmp/shieldra-work && git add app/src/main/kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml app/src/main/res/values-fr/strings.xml app/src/test/kotlin/com/shieldra/app/presentation/screen/settings/OwnerContactCopyTest.kt
git commit -m "feat: say what the owner contact accepts and what the holder will see"
```

---

### Task 10: Gates, locale parity, CI, docs

**Files:**
- Modify: `.github/scripts/g4b-review-check.sh` (add section 5g)
- Create: `.github/scripts/locale-parity-check.sh`
- Modify: `.github/workflows/group-a-diagnostics.yml` (run the new script)
- Modify: `docs/ROADMAP.md`, `docs/reports/audit-round-1-motion-snatch-lock.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the pins that keep A-45 from quietly regressing, and the CI numbers the owner is owed.

- [ ] **Step 1: Add the A-45 pins to g4b**

Modify `.github/scripts/g4b-review-check.sh`, inserting after the A-44 block (around line 541), following the same `fail`/`pass` style the A-43 block uses:

```bash
# 5g. A-45 (2026-11-04): the owner's ladder. The promise is that a confirmed Motion is not one shot:
# it locks and tells the holder first, and only spends the camera after nobody unlocked. The zone may
# mute escalation, but an unknown zone must never mute the guard.
LADDER="app/src/main/kotlin/com/shieldra/protection/SnatchLadder.kt"
ZONE_POLICY="app/src/main/kotlin/com/shieldra/protection/ZonePolicy.kt"
RESPONDER="app/src/main/kotlin/com/shieldra/protection/TableModeResponder.kt"
[ -s "$LADDER" ] || fail "the pure snatch ladder is missing (A-45)"
grep -q 'DEMAND_MS = 30_000L' "$LADDER" || fail "the owner-unlock demand window changed from the approved 30 s (A-45)"
grep -q 'LADDER_WINDOW_MS = 1_800_000L' "$LADDER" || fail "the ladder window changed from the approved 30 min (A-45)"
grep -q 'SnatchLadder.next' "$RESPONDER" || fail "the responder no longer runs the ladder (A-45)"
grep -q 'TheftDemand.show' "$RESPONDER" || fail "a locked phone no longer tells its holder what is happening (A-45)"
grep -q 'DeviceScreenLock.now' "$RESPONDER" || fail "the ladder no longer locks through the single guarded caller (A-45)"
grep -q 'settings.armed' "$RESPONDER" || fail "the ladder is not gated on the armed flag (A-45)"
[ -s "$ZONE_POLICY" ] || fail "the pure safe-zone policy is missing (A-45)"
grep -q '!stateKnown || !inside -> ZoneResponse.FULL' "$ZONE_POLICY" \
  || fail "an unknown or outside safe-zone state no longer fails closed (A-45)"
grep -q 'ZoneResponse.MUTED' "$RESPONDER" || fail "the safe zone no longer has its own response path (A-45)"
grep -q 'TableModeResponder.onMotionConfirmed' app/src/main/kotlin/com/shieldra/detection/MotionMonitoringService.kt \
  || fail "a movement that joins an open incident is silent again (A-45)"
pass "A-45: a confirmed Motion locks, demands an unlock, escalates, and a zone never fails open"
```

- [ ] **Step 2: Write the locale parity gate**

Create `.github/scripts/locale-parity-check.sh`. It checks only the keys that must exist in every locale (a full translation of the whole app is not in scope for A-45), so the list is explicit rather than aspirational:

```bash
#!/usr/bin/env bash
# Which strings must exist in every locale the app ships copy for. The theft notice is the one text
# this app shows to a person it does not know, so a missing French or Arabic line is a safety gap, not
# a translation backlog.
set -uo pipefail
cd "$(dirname "$0")/../.."
fail=0
KEYS=(theft_demand_channel theft_demand_title theft_demand_body theft_demand_owner_message theft_demand_contact theft_demand_call protection_owner_contact_hint protection_owner_contact_error protection_theft_notice_note)
for locale in values values-ar values-fr; do
    file="app/src/main/res/$locale/strings.xml"
    [ -s "$file" ] || { echo "FAIL: $file is missing or empty"; fail=1; continue; }
    for key in "${KEYS[@]}"; do
        grep -q "name=\"$key\"" "$file" || { echo "FAIL: $file is missing $key"; fail=1; }
    done
done
# No placeholder drift: the same keys must take the same number of arguments in every locale.
for key in theft_demand_owner_message theft_demand_contact; do
    counts="$(for locale in values values-ar values-fr; do
        grep -A0 "name=\"$key\"" "app/src/main/res/$locale/strings.xml" | grep -oc '%1\$s' || true
    done | sort -u | tr -d '[:space:]')"
    [ "$counts" = "1" ] || { echo "FAIL: $key placeholder count differs across locales ($counts)"; fail=1; }
done
if (( fail > 0 )); then
    echo "LOCALE PARITY FAILED" >&2
    exit 1
fi
echo "LOCALE PARITY PASS — ar, en and fr carry every holder-facing key"
```

- [ ] **Step 3: Run it in CI**

Modify `.github/workflows/group-a-diagnostics.yml`, adding after the Phase 3 static review step:

```yaml
      - name: Run locale parity check
        shell: bash
        run: bash .github/scripts/locale-parity-check.sh
```

- [ ] **Step 4: Run both gates locally**

```bash
cd /c/src/tmp/shieldra-work && bash .github/scripts/locale-parity-check.sh && bash .github/scripts/g4b-review-check.sh 2>&1 | tail -6
```
Expected: `LOCALE PARITY PASS …`, then `G4-B LOCAL REVIEW PASS — runtime/device evidence still required` with the new `A-45:` line printed above it. Also run `bash .github/scripts/merged-manifest-check.sh` if it exists locally, then `chmod +x .github/scripts/locale-parity-check.sh` and stage the mode change.

- [ ] **Step 5: Push and dispatch CI (checkpoint three)**

```bash
cd /c/src/tmp/shieldra-work && git add -A .github && git commit -m "test: pin the ladder, the fail-closed zone policy and the holder-facing locales"
git push origin fix/audit-round-1 && gh workflow run group-a-diagnostics.yml --ref fix/audit-round-1 --repo toufikben/shieldra && sleep 15 && gh run list --repo toufikben/shieldra --branch fix/audit-round-1 --limit 1
gh run watch <run-id> --repo toufikben/shieldra --exit-status
```
Expected: success, `CI TEST SUMMARY: 616 tests, 616 passed, 0 failures, 0 errors` (578 + 8 ladder + 4 notice text + 4 contact + 3 demand copy + 7 zone + 7 tilt + 2 contact-field copy = 613; quote whatever CI actually prints, not this sum), `A-45:` pin passing, `LOCALE PARITY PASS`.

- [ ] **Step 6: Write the round up while CI is green**

Modify `docs/reports/audit-round-1-motion-snatch-lock.md`: append a section `### A-45 — the snatch ladder (design approved 2026-11-04)` recording the measured table from the spec (the six constants today, the silent bound repeat, the invisible grace, the zone that mutes nothing), the four owner decisions, the CI run id with its printed numbers, and an explicit NOT RUN list for anything not yet measured on the phone.

Modify `docs/ROADMAP.md`: add the A-45 line under the current stage with the CI run id, and state plainly that the motion thresholds are unchanged until Task 7's recorder has real samples from the owner's handset.

- [ ] **Step 7: Commit docs and push**

```bash
cd /c/src/tmp/shieldra-work && git add docs/ROADMAP.md docs/reports/audit-round-1-motion-snatch-lock.md docs/superpowers
git commit -m "docs: record the A-45 ladder design, its decisions and its CI run"
git push origin fix/audit-round-1
```

---

### Task 11: Phone round for A-45 (blocked until the shared phone is free)

**Files:**
- Create: `qa/phone-checklist-snatch-ladder-2026-11-04.md` (session folder, outside the repo)
- Evidence: `qa/artifacts/device-snatch-ladder-2026-11-04/` (outside the repo)

**Interfaces:**
- Consumes: a CI run that published the debug pair to `test-<run_id>` with `publish_phone_apks` set.
- Produces: measured confirmation of the ladder and the notice, and the lift samples that let Task 12 set thresholds.

- [ ] **Step 1: Confirm the phone is free before touching it**

Run: `adb shell dumpsys activity activities | grep topResumedActivity && adb shell pidof com.shieldra.app`
Proceed only when the foreground is the launcher and no other inspector's app is showing. Install without launching. Never reboot, never enter the screen lock, force-stop our app whenever the phone is handed back.

- [ ] **Step 2: The checklist to write and then run**

Items, each with the expected evidence: (1) arming starts both services as before; (2) a movement while armed posts the demand notice with the three languages and the contact once, and `pidof`/`dumpsys notification` show id 20021; (3) the first evidence burst lands ~30 s later, not at the movement (compare the event's photo timestamp to the confirmation timestamp in History); (4) the burst train follows the ladder (60 s × 5, then 5 min) and stops at 30 min or at unlock; (5) an owner unlock (the owner presses the power button and enters their own code — never us) clears the notice and stops the train; (6) with a safe zone on and the phone charging, a movement records an event but produces no lock and no notice; (7) with a safe zone on and unplugged, the lock happens and the siren does not; (8) with `geofenceEnabled` on but the sampler state stale, the full ladder runs (fail closed); (9) the ladder repeats on a second movement instead of going silent; (10) the notice appears after a reboot if the ladder was still running — record honestly that the phone is not rebooted by us, so this item is verified only if a restart happens on its own; (11) Task 7's recorder run with the owner lifting the phone, with the log lines saved; (12) force-stop, empty `pidof`, launcher twice.

- [ ] **Step 3: Fill the results and say what was NOT RUN**

Write measured values, not expectations. Anything unverified stays labelled NOT RUN with the reason.

---

### Task 12: Set the Motion constants from measurement (only after Task 11 Step 2 item 11)

**Files:**
- Modify: `app/src/main/kotlin/com/shieldra/detection/ApprovedMotionCalibration.kt`
- Modify: `app/src/main/kotlin/com/shieldra/detection/MotionDetection.kt` (add the tilt route)
- Modify: `app/src/main/kotlin/com/shieldra/detection/MotionSignal.kt`, `MotionGuardPipeline.kt`
- Test: `app/src/test/kotlin/com/shieldra/detection/ApprovedMotionCalibrationTest.kt`, `MotionWindowEvaluatorTest.kt`, `MotionGuardPipelineTest.kt`
- Modify: `.github/scripts/g4b-review-check.sh` (pin the measured numbers the same way A-43 pinned the guard)

**Interfaces:**
- Consumes: `MotionTiltProbe` (Task 7) and the recorded `SHIELDRA_LIFT` lines (Task 11).
- Produces: `data class MotionCalibration` with a tilt rule, and a `stillBaseline` requirement.

This task is deliberately left with its steps to write once the samples exist: the design (D4) says the constants come from the measurements, so writing them now would be inventing numbers and then "confirming" them with tests. Its shape is fixed here — a resting pose established over the first `REST_SECONDS` of stillness, a confirmed lift when the gravity vector has turned by `TILT_ALERT_DEGREES` or the existing force rule fires, and the quiet-window and noise-floor rules from the spec — but the values are filled from the recorded lifts, and every filled value must be quotable to a specific recorded line.

---

## Self-review notes (already applied)

Spec coverage: D1 → Tasks 1, 3, 8, 10, 11; D2 → Tasks 5, 6, 8, 10, 11; D3 → Tasks 2, 3, 9, 10, 11; D4 → Tasks 7, 11 (item 11), 12; D5 → verified by no task adding a permission, and Task 6 reuses `SCHEDULE_EXACT_ALARM`. The two claims the owner hears about — that a locked phone tells its holder, and that the owner gets more than one picture — are covered by Task 8 and pinned by Task 10 Step 1.

Known risk to report honestly after the first CI run: `TheftDemand` and `GeofenceTicker` are Android-side and have no JVM test, so a compile error there surfaces only in CI; and `TableModeResponder` is covered today only by instrumented tests, so the ladder's real timing is a phone-round item (Task 11), not a green CI run.
