package com.shieldra.storage

/**
 * Chooses the cipher for an evidence operation by key generation.
 *
 * Writes always use the currently active generation; reads use the generation stored with the
 * evidence's own metadata row. This is what keeps evidence readable after a key rotation: the
 * file store alone only knows the evidence id, so the version has to come from the metadata
 * authority (Room), never from one fixed alias.
 */
interface EvidenceCipherResolver {
    /** Cipher of the active key generation, used for every new payload. */
    fun writeCipher(): EvidenceCipher

    /**
     * Cipher of the generation [evidenceId] was sealed with.
     * Implementations must fail closed when the stored version is unknown.
     */
    fun readCipher(evidenceId: String): EvidenceCipher
}
