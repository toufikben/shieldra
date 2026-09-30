package com.shieldra.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.app.ShieldraApp
import com.shieldra.domain.PanicActivation
import com.shieldra.domain.PanicIncidentCoordinator
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §15 wiring on a real device after the owner flipped the production gate
 * (amendment A-5, protocol §11/§14): the running application itself installs
 * local storage, the real database and the §9 rotation clock record appear,
 * a Panic press persists a real event through the production storage
 * instance — and the install alone still creates no Keystore key, because a
 * key exists only once real evidence is encrypted.
 */
@RunWith(AndroidJUnit4::class)
class StorageRuntimeInstallInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun gate_is_on_by_default() {
        assertTrue(
            "§14/§15: the owner flips this gate only in a visible commit (amendment A-5)",
            StorageRuntimeApproval.approved,
        )
    }

    @Test
    fun the_running_application_installs_storage_through_production_wiring() {
        val app = context as ShieldraApp
        assertTrue(
            "ShieldraApp must install storage once the gate is approved: ${app.storageDecision}",
            app.storageDecision is StorageInstallDecision.Installed,
        )
        checkNotNull(app.installedStorage) {
            "an approved install must keep the opened storage attached to the application"
        }
    }

    @Test
    fun an_installed_storage_creates_the_real_database() {
        val storage = productionStorage()
        // Room touches the file only on the first query, so run one synchronously here.
        storage.eventRepository.listRecent(1)
        assertTrue(
            "an approved open must put the production database on the device",
            context.getDatabasePath(PRODUCTION_DB).exists(),
        )
    }

    @Test
    fun the_rotation_clock_record_exists_once_storage_is_installed() {
        val storage = productionStorage()
        // The production §9 entry point; the instrumentation thread is off the main thread.
        storage.rotateEvidenceIfNeeded(manual = false)
        assertTrue(
            "protocol §9: the rotation record may only appear after an approved storage install",
            FileBackedKeyLifecycle.fileIn(context.noBackupFilesDir).exists(),
        )
    }

    @Test
    fun a_panic_press_persists_a_real_event_through_production_storage() {
        val storage = productionStorage()
        val coordinator = PanicIncidentCoordinator(repository = storage.eventRepository)

        val first = coordinator.activate()
        val firstEvent = when (first) {
            is PanicActivation.Recorded -> first.event
            // A previous suite run may have left a still-open incident on the device.
            // Panic deliberately has no cooldown and no time window, so binding to it
            // is the correct behavior, not a failure.
            is PanicActivation.BoundToActive -> first.event
            is PanicActivation.Rejected -> throw IllegalStateException(
                "a panic press must never be rejected against installed storage: ${first.reason}",
            )
        }
        val repeat = coordinator.activate()
        val repeatEvent = checkNotNull((repeat as? PanicActivation.BoundToActive)?.event) {
            "a repeat press must bind to the running incident, but was $repeat"
        }

        assertEquals(
            "a repeat press must reuse the same incident id",
            firstEvent.id,
            repeatEvent.id,
        )
        assertEquals(
            "exactly one event may be persisted for the incident",
            1,
            storage.eventRepository.listRecent(50).count { it.id == firstEvent.id },
        )
    }

    @Test
    fun an_install_alone_creates_no_keystore_key() {
        val before = evidenceAliasesPresent()
        decideStorageInstall(approved = StorageRuntimeApproval.approved) {
            LocalShieldraStorage.openOrRecovery(context)
        }
        val after = evidenceAliasesPresent()
        assertEquals(
            "opening storage must not create or remove any evidence key; a key exists only when real evidence is encrypted",
            before,
            after,
        )
    }

    private fun productionStorage(): LocalShieldraStorage = checkNotNull(
        (context as ShieldraApp).installedStorage,
    ) {
        "the production application must have installed storage before this test ran"
    }

    private fun evidenceAliasesPresent(): Set<String> {
        val keyStore = KeyStore.getInstance(EncryptionPolicy.PROVIDER).apply { load(null) }
        return listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2)
            .filter { keyStore.containsAlias(it) }
            .toSet()
    }

    private companion object {
        private const val PRODUCTION_DB = "shieldra.db"
    }
}
