package com.shieldra.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §15 wiring on a real device: the runtime install gate is off by default, so
 * the production storage path is never opened. This must create no database
 * file and add no Keystore key.
 *
 * The Keystore assertion is a before/after comparison rather than an absolute
 * "no alias exists" check, because other instrumented tests in the same run
 * legitimately create the evidence aliases; the only thing this gate owns is
 * that it changes nothing.
 */
@RunWith(AndroidJUnit4::class)
class StorageWiringGateOffInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun gate_is_off_by_default() {
        assertFalse(
            "local storage install must stay behind an owner-flipped approval",
            StorageRuntimeApproval.approved,
        )
    }

    @Test
    fun default_off_never_invokes_open_and_creates_no_database() {
        var opened = false
        val decision = decideStorageInstall(approved = StorageRuntimeApproval.approved) {
            opened = true
            LocalShieldraStorage.openOrRecovery(context)
        }

        assertTrue("off gate must yield NotApproved: $decision", decision is StorageInstallDecision.NotApproved)
        assertFalse("off gate must not attempt an open", opened)
        assertFalse(
            "the production database must not be created when the gate is off",
            context.getDatabasePath(PRODUCTION_DB).exists(),
        )
    }

    @Test
    fun default_off_adds_no_keystore_key() {
        val before = evidenceAliasesPresent()
        decideStorageInstall(approved = StorageRuntimeApproval.approved) {
            LocalShieldraStorage.openOrRecovery(context)
        }
        val after = evidenceAliasesPresent()
        assertEquals("the gate-off path must not create or remove any evidence key", before, after)
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
