package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the production rotation trigger and the old-key retirement lifecycle
 * (owner protocol §9/§10, decisions §3.1/§3.3/§3.4).
 *
 * Trigger policy is "90 days OR explicit manual trigger". Retirement ordering is
 * fixed: identify every eligible item, migrate all of them, verify each migration,
 * prove zero remaining references to the old key version, and only then mark that
 * version RETIRED. Destruction is never part of this path (§10, open decision Q-C8).
 */
class EvidenceRotationOrchestratorTest {

    private class Clock(var now: Long = START) {
        val millis: () -> Long get() = { now }

        fun advance(days: Long) {
            now += days * MILLIS_PER_DAY
        }

        companion object {
            const val START = 1_700_000_000_000L
            const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        }
    }

    /** One Keystore generation: only its own tag decrypts its payload, like AES-GCM auth. */
    private class FakeCipher(private val tag: String) : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray =
            prefix() + aad + byteArrayOf(0) + plaintext

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
            val header = prefix()
            if (ciphertext.size < header.size + 1 ||
                !ciphertext.copyOfRange(0, header.size).contentEquals(header)
            ) {
                throw EvidenceUnavailableException("payload is not bound to key '$tag'")
            }
            var separator = -1
            for (index in header.size until ciphertext.size) {
                if (ciphertext[index] == 0.toByte()) {
                    separator = index
                    break
                }
            }
            if (separator < 0) throw EvidenceUnavailableException("corrupt payload framing")
            val storedAad = ciphertext.copyOfRange(header.size, separator)
            if (!storedAad.contentEquals(aad)) throw EvidenceUnavailableException("AAD mismatch")
            return ciphertext.copyOfRange(separator + 1, ciphertext.size)
        }

        private fun prefix(): ByteArray = "$tag\u0000".toByteArray(Charsets.UTF_8)
    }

    private class FakeFiles : RotationFileGateway {
        val committed = mutableMapOf<String, ByteArray>()
        val temps = mutableMapOf<String, ByteArray>()

        override fun readCommitted(evidenceId: String): ByteArray =
            committed[evidenceId]?.copyOf()
                ?: throw EvidenceUnavailableException("missing committed evidence: $evidenceId")

        override fun hasTemp(evidenceId: String): Boolean = temps.containsKey(evidenceId)

        override fun writeTemp(evidenceId: String, payload: ByteArray) {
            temps[evidenceId] = payload.copyOf()
        }

        override fun readTemp(evidenceId: String): ByteArray =
            temps[evidenceId] ?: throw EvidenceUnavailableException("missing rotation temp")

        override fun commitTemp(evidenceId: String) {
            val payload = temps.remove(evidenceId) ?: error("no rotation temp to commit")
            committed[evidenceId] = payload
        }

        override fun discardTemp(evidenceId: String) {
            temps.remove(evidenceId)
        }
    }

    /** Backs rotation metadata and the eligibility scan; [appearsLater] models a concurrent insert. */
    private class FakeStore : RotationMetadataGateway, RotationScopeGateway {
        val rows = mutableMapOf<String, RotationMetadata>()
        var failNextMark: String? = null
        var hiddenUntilSecondScan: String? = null
        private var scans = 0

        override fun versionsOf(evidenceId: String): RotationMetadata? = rows[evidenceId]

        override fun markMigrated(evidenceId: String, keyVersion: String, aadVersion: String) {
            if (failNextMark == evidenceId) {
                failNextMark = null
                throw IllegalStateException("metadata write rejected")
            }
            rows[evidenceId] = RotationMetadata(keyVersion, aadVersion)
        }

        override fun all(): Map<String, RotationMetadata> {
            scans += 1
            val hidden = hiddenUntilSecondScan
            return if (scans == 1 && hidden != null) rows.filterKeys { it != hidden } else rows.toMap()
        }
    }

    /** In-memory key-version lifecycle table. */
    private class FakeLifecycle : KeyLifecycleGateway {
        var active: String = EvidenceKeyPolicy.KEY_VERSION_V1
        val retired = mutableListOf<String>()

        /**
         * The 90-day clock starts when the key generation is created, not at the
         * unix epoch (decision §3.1). A never-rotated installation therefore models
         * "created at install time", so the interval trigger cannot fire on first
         * launch.
         */
        var lastRotationEpochMs: Long = Clock.START
        var successor: String? = EvidenceKeyPolicy.KEY_VERSION_V2
        var failComplete: Boolean = false

        override fun snapshot(): KeyLifecycleSnapshot =
            KeyLifecycleSnapshot(active, retired.toSet(), lastRotationEpochMs)

        override fun nextKeyVersion(current: String): String? =
            if (current == EvidenceKeyPolicy.KEY_VERSION_V1) successor else null

        override fun completeRotation(fromKeyVersion: String, toKeyVersion: String, atEpochMs: Long) {
            if (failComplete) throw IllegalStateException("lifecycle write rejected")
            require(active == fromKeyVersion) { "active version changed underneath the rotation" }
            active = toKeyVersion
            retired += fromKeyVersion
            lastRotationEpochMs = atEpochMs
        }
    }

    /** Deterministic ASCII AADs; evidence ids never contain a NUL byte. */
    private class FakeAad : RotationAadFactory {
        override fun aadFor(evidenceId: String, aadVersion: String): ByteArray =
            "$aadVersion|$evidenceId".toByteArray(Charsets.UTF_8)
    }

    private class Harness(evidenceIds: List<String> = listOf("ev-1", "ev-2")) {
        val clock = Clock()
        val files = FakeFiles()
        val store = FakeStore()
        val lifecycle = FakeLifecycle()
        val aad = FakeAad()
        val ciphers = object : RotationCipherFactory {
            private val created = mutableMapOf<String, EvidenceCipher>()
            override fun cipherFor(keyVersion: String): EvidenceCipher =
                created.getOrPut(keyVersion) { FakeCipher(keyVersion) }
        }
        val orchestrator = EvidenceRotationOrchestrator(
            rotator = EvidenceKeyRotator(files, store, ciphers, aad),
            scope = store,
            lifecycle = lifecycle,
            nowMillis = clock.millis,
        )
        val evidenceIds = evidenceIds

        init {
            evidenceIds.forEach { id ->
                store.rows[id] = RotationMetadata(
                    EvidenceKeyPolicy.KEY_VERSION_V1,
                    EvidenceKeyPolicy.AAD_VERSION_V1,
                )
                files.committed[id] = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V1)
                    .encrypt("plaintext-of-$id".toByteArray(), aad.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1))
            }
        }

        fun advancePastInterval() = clock.advance(EvidenceKeyPolicy.ROTATION_INTERVAL_DAYS)

        fun decryptableUnder(keyVersion: String, id: String): Boolean =
            runCatching {
                ciphers.cipherFor(keyVersion).decrypt(files.committed.getValue(id), aad.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1))
            }.isSuccess
    }

    @Test
    fun nothing_happens_before_the_interval_without_a_manual_trigger() {
        val harness = Harness()
        harness.clock.advance(EvidenceKeyPolicy.ROTATION_INTERVAL_DAYS - 1)

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.NOT_DUE, report.outcome)
        assertEquals(emptyList(), report.migrated)
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, harness.lifecycle.active)
        assertTrue(harness.lifecycle.retired.isEmpty(), "no key retires without a rotation")
    }

    @Test
    fun clock_skew_into_the_past_never_triggers_a_rotation() {
        val harness = Harness()
        harness.lifecycle.lastRotationEpochMs = harness.clock.now + Clock.MILLIS_PER_DAY

        assertEquals(RotationTriggerOutcome.NOT_DUE, harness.orchestrator.run(manual = false).outcome)
    }

    @Test
    fun the_interval_trigger_migrates_every_item_then_retires_the_old_key() {
        val harness = Harness()
        harness.advancePastInterval()

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.ROTATED, report.outcome)
        assertEquals(RotationTrigger.INTERVAL_ELAPSED, report.trigger)
        assertEquals(harness.evidenceIds.sorted(), report.migrated.sorted())
        assertEquals(
            EvidenceKeyPolicy.KEY_VERSION_V2,
            harness.lifecycle.active,
            "the new version goes active only after every item verifies",
        )
        assertEquals(listOf(EvidenceKeyPolicy.KEY_VERSION_V1), harness.lifecycle.retired)
        assertEquals(harness.clock.now, harness.lifecycle.lastRotationEpochMs)
        harness.evidenceIds.forEach { id ->
            assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, harness.store.rows.getValue(id).keyVersion)
            assertTrue(harness.decryptableUnder(EvidenceKeyPolicy.KEY_VERSION_V2, id), "$id must read under the new key")
        }
    }

    @Test
    fun a_manual_trigger_rotates_before_the_interval_elapses() {
        val harness = Harness()

        val report = harness.orchestrator.run(manual = true)

        assertEquals(RotationTriggerOutcome.ROTATED, report.outcome)
        assertEquals(RotationTrigger.MANUAL, report.trigger)
        assertEquals(listOf(EvidenceKeyPolicy.KEY_VERSION_V1), harness.lifecycle.retired)
    }

    @Test
    fun one_corrupted_item_blocks_retirement_and_keeps_the_old_key_in_service() {
        val harness = Harness()
        harness.files.committed["ev-2"] = byteArrayOf(9, 9, 9)
        harness.advancePastInterval()

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.PARTIALLY_MIGRATED, report.outcome)
        assertEquals(listOf("ev-2"), report.failed)
        assertEquals(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            harness.lifecycle.active,
            "a failure must never retire a key other evidence still needs",
        )
        assertTrue(harness.lifecycle.retired.isEmpty(), "retirement requires zero failures")
        assertTrue(
            harness.decryptableUnder(EvidenceKeyPolicy.KEY_VERSION_V2, "ev-1"),
            "completed work is never rolled back because a sibling item failed",
        )
    }

    @Test
    fun a_failed_metadata_write_blocks_retirement() {
        val harness = Harness()
        harness.store.failNextMark = "ev-1"
        harness.advancePastInterval()

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.PARTIALLY_MIGRATED, report.outcome)
        assertEquals(listOf("ev-1"), report.failed)
        assertTrue(harness.lifecycle.retired.isEmpty())
    }

    @Test
    fun an_interrupted_rotation_resumes_and_retires_once_clean() {
        val harness = Harness()
        harness.store.failNextMark = "ev-2"
        harness.advancePastInterval()

        assertEquals(RotationTriggerOutcome.PARTIALLY_MIGRATED, harness.orchestrator.run(manual = false).outcome)
        val second = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.ROTATED, second.outcome)
        assertEquals(listOf(EvidenceKeyPolicy.KEY_VERSION_V1), harness.lifecycle.retired)
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, harness.store.rows.getValue("ev-1").keyVersion)
        assertTrue(harness.files.temps.isEmpty(), "no rotation temp survives a completed run")
    }

    @Test
    fun a_run_with_no_evidence_rotates_no_key_and_retires_nothing() {
        val harness = Harness(evidenceIds = emptyList())
        harness.advancePastInterval()

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.NO_ELIGIBLE_EVIDENCE, report.outcome)
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, harness.lifecycle.active)
        assertTrue(harness.lifecycle.retired.isEmpty())
    }

    @Test
    fun a_reference_still_pointing_at_the_old_version_blocks_retirement() {
        val harness = Harness()
        // Evidence written while the rotation ran: invisible to the eligibility scan,
        // visible to the final reference check.
        harness.store.hiddenUntilSecondScan = "ev-2"
        harness.advancePastInterval()

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.RETIREMENT_BLOCKED, report.outcome)
        assertEquals(listOf("ev-1"), report.migrated)
        assertTrue(harness.lifecycle.retired.isEmpty(), "a surviving v1 reference forbids retirement")
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, harness.lifecycle.active)
    }

    @Test
    fun a_rejected_lifecycle_write_leaves_the_old_key_active() {
        val harness = Harness()
        harness.lifecycle.failComplete = true
        harness.advancePastInterval()

        val report = harness.orchestrator.run(manual = false)

        assertEquals(RotationTriggerOutcome.RETIREMENT_BLOCKED, report.outcome)
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, harness.lifecycle.active)
        assertTrue(harness.lifecycle.retired.isEmpty())
    }

    @Test
    fun an_active_version_with_no_approved_successor_fails_closed() {
        val harness = Harness()
        harness.advancePastInterval()
        harness.orchestrator.run(manual = false)

        val report = harness.orchestrator.run(manual = true)

        assertEquals(RotationTriggerOutcome.NO_APPROVED_SUCCESSOR, report.outcome)
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, harness.lifecycle.active)
        assertEquals(listOf(EvidenceKeyPolicy.KEY_VERSION_V1), harness.lifecycle.retired)
    }

    @Test
    fun the_orchestrator_exposes_no_key_destruction_surface() {
        val members = KeyLifecycleGateway::class.java.methods.map { it.name.lowercase() }
        assertTrue(
            members.none { name -> name.contains("delete") || name.contains("destroy") || name.contains("remove") },
            "the key lifecycle must never offer destruction: $members",
        )
    }
}
