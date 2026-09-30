package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The runtime install gate for §15: local storage may only be attached behind
 * an explicit approval flag, and the decision must be a pure function so the
 * three outcomes are testable without an Application or a Keystore.
 *
 * The four cases locked here: default-off never opens storage; approved + a
 * successful open installs the value; approved + a failed open surfaces
 * RecoveryRequired with no installed storage (and no destructive fallback);
 * and the shipped constant itself records the owner's §14 production flip,
 * which may only happen after the first real detector passes device
 * verification (Q-C2 option 1, protocol §11/§14).
 */
class StorageRuntimeGateTest {

    @Test
    fun `the owner has flipped the production storage gate`() {
        assertTrue(
            StorageRuntimeApproval.approved,
            "§14/§15: this gate flips only in a visible owner commit, after the first detector's device verification (Q-C2 option 1)",
        )
    }

    @Test
    fun `not approved returns NotApproved and never calls open`() {
        var opened = false
        val decision = decideStorageInstall(approved = false) {
            opened = true
            StorageOpenResult.Ready(FakeStorage("unused"))
        }
        assertIs<StorageInstallDecision.NotApproved>(decision)
        assertTrue(!opened, "open must not be invoked when the gate is off")
    }

    @Test
    fun `approved with a successful open installs the storage`() {
        val storage = FakeStorage("installed")
        val decision = decideStorageInstall(approved = true) {
            StorageOpenResult.Ready(storage)
        }
        val installed = assertIs<StorageInstallDecision.Installed<FakeStorage>>(decision)
        assertEquals(storage, installed.storage)
    }

    @Test
    fun `approved with a failed open surfaces recovery with no installed storage`() {
        val decision = decideStorageInstall(approved = true) {
            StorageOpenResult.RecoveryRequired<FakeStorage>(
                StorageRecoveryReason.MIGRATION_FAILURE,
                "migration 1->2 failed",
            )
        }
        val recovery = assertIs<StorageInstallDecision.RecoveryRequired>(decision)
        assertEquals(StorageRecoveryReason.MIGRATION_FAILURE, recovery.reason)
        assertEquals("migration 1->2 failed", recovery.detail)
    }

    private class FakeStorage(val name: String)
}
