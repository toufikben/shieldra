package com.shieldra.protection

/**
 * Reads several adverse signals together (A-33): a SIM swap on its own may be innocent, but a SIM
 * swap while the screen is off AND the phone is offline is the classic move of someone hiding a
 * stolen phone from recovery — pull the owner's SIM, keep the screen dark, cut the network. When
 * they coincide the phone treats it as high-risk tampering and may escalate past quiet evidence.
 *
 * Pure: the signals in, a yes/no out. Unit-tested.
 */
object CompositeTamper {

    data class Signals(
        val simAbnormal: Boolean,
        val screenInteractive: Boolean,
        val online: Boolean,
    )

    fun isHighRisk(s: Signals): Boolean = s.simAbnormal && !s.screenInteractive && !s.online
}
