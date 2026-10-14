package com.shieldra.protection

/**
 * A reading of the phone's SIM/network state at one moment. [simIds] are stable per-SIM identifiers
 * (subscription id or ICCID) the platform reported; [airplaneOn] and [hasService] describe the
 * radio. All three come from Android; this file never reads them itself.
 */
data class SimSnapshot(
    val simIds: Set<String>,
    val airplaneOn: Boolean,
    val hasService: Boolean,
)

/**
 * Classifies a change in SIM/network state against the owner's baseline (A-33). The baseline is the
 * set of SIMs present when the owner armed protection. A thief who swaps the SIM, pulls it, or cuts
 * the network to stop tracking triggers exactly one event each; the phone sliding into a tunnel is
 * reported as a network loss, not a theft, and a return to the baseline is no event at all.
 *
 * Pure: baseline in, snapshot in, verdict out. Unit-tested without a device.
 */
class SimGuardLogic(private val baseline: Set<String>) {
    sealed interface Verdict {
        /** A known SIM was pulled and no foreign one took its place. */
        data object Removed : Verdict

        /** A SIM the owner never armed with is now present (carrier/number in the alert text). */
        data object ForeignInserted : Verdict

        /** Airplane mode on, or no service: could be a tunnel or a thief cutting the network. */
        data object NetworkLost : Verdict

        /** Back to the armed SIMs with service: nothing to report. */
        data object Normal : Verdict
    }

    fun classify(now: SimSnapshot): Verdict {
        val foreign = now.simIds - baseline
        val missing = baseline - now.simIds
        return when {
            foreign.isNotEmpty() -> Verdict.ForeignInserted
            missing.isNotEmpty() && now.simIds.isEmpty() -> Verdict.Removed
            missing.isNotEmpty() -> Verdict.Removed
            now.airplaneOn || !now.hasService -> Verdict.NetworkLost
            else -> Verdict.Normal
        }
    }
}
