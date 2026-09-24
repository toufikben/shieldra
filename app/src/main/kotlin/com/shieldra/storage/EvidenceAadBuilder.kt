package com.shieldra.storage

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Canonical AAD serializer for SHIELDRA evidence encryption.
 *
 * Format (AAD-v1, decision §1.1):
 *
 *   versionByte (0x01)
 *   || objectClassLength (UInt32 Big Endian)
 *   || objectClassBytes (UTF-8)
 *   || evidenceIdLength (UInt32 Big Endian)
 *   || evidenceIdBytes (UTF-8)
 *
 * Rules enforced:
 * - Serialization is 100% deterministic.
 * - No JSON, no Base64, no concatenation without lengths.
 * - No platform default charset; always UTF-8.
 * - No locale-dependent conversion.
 * - Any future format change must use a new AAD version constant.
 * - build() fails closed if any field is missing or empty.
 */
object EvidenceAadBuilder {

    /** Current canonical AAD version byte. */
    const val VERSION_BYTE: Byte = 0x01

    /** Human-readable label for the current version (informational). */
    const val VERSION_LABEL: String = "AAD-v1"

    /**
     * Canonical object class for evidence entries (decision §2.1).
     * Must be canonical lowercase UTF-8.
     */
    const val OBJECT_CLASS_EVIDENCE: String = "evidence"

    /**
     * Builds the canonical AAD byte array for an evidence entry.
     *
     * @param objectClass Canonical lowercase UTF-8 object class (e.g. "evidence").
     * @param evidenceId  Canonical UTF-8 evidence identifier.
     * @throws IllegalArgumentException if either field is blank.
     */
    fun build(objectClass: String, evidenceId: String): ByteArray {
        require(objectClass.isNotBlank()) {
            "AAD objectClass must not be blank"
        }
        require(evidenceId.isNotBlank()) {
            "AAD evidenceId must not be blank"
        }
        require(objectClass == objectClass.lowercase()) {
            "AAD objectClass must be canonical lowercase: $objectClass"
        }

        val classBytes = objectClass.toByteArray(StandardCharsets.UTF_8)
        val idBytes = evidenceId.toByteArray(StandardCharsets.UTF_8)

        // 1 (version) + 4 (class length) + classBytes + 4 (id length) + idBytes
        val buffer = ByteBuffer.allocate(1 + 4 + classBytes.size + 4 + idBytes.size)
        buffer.put(VERSION_BYTE)
        buffer.putInt(classBytes.size)   // UInt32 Big Endian (ByteBuffer default)
        buffer.put(classBytes)
        buffer.putInt(idBytes.size)      // UInt32 Big Endian
        buffer.put(idBytes)

        return buffer.array()
    }

    /**
     * Convenience builder for the canonical evidence object class.
     */
    fun forEvidence(evidenceId: String): ByteArray =
        build(OBJECT_CLASS_EVIDENCE, evidenceId)
}
