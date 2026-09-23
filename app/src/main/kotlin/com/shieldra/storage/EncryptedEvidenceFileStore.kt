package com.shieldra.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

interface EvidenceCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

class KeystoreEvidenceCipher(
    private val encryptor: AndroidKeystoreEncryptor,
    private val keyAlias: String,
    private val policy: EncryptionPolicy,
    private val associatedData: ByteArray? = null,
) : EvidenceCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = encryptor
        .encrypt(plaintext, keyAlias, policy, associatedData)
        .toByteArray()

    override fun decrypt(ciphertext: ByteArray): ByteArray = encryptor.decrypt(
        EncryptedPayload.fromByteArray(ciphertext),
        keyAlias,
        policy,
        associatedData,
    )
}

class EvidenceUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Stores only authenticated ciphertext outside Room. Temporary files are never
 * returned as evidence and any decode/authentication failure is unavailable.
 */
class EncryptedEvidenceFileStore(
    private val rootDirectory: File,
    private val cipher: EvidenceCipher,
) {
    init {
        require(rootDirectory.exists() || rootDirectory.mkdirs()) {
            "Unable to create evidence directory"
        }
    }

    fun write(evidenceId: String, plaintext: ByteArray): File {
        validateEvidenceId(evidenceId)
        val target = fileFor(evidenceId)
        val temporary = File(rootDirectory, ".$evidenceId.tmp")
        try {
            val encrypted = cipher.encrypt(plaintext)
            FileOutputStream(temporary).use { output ->
                output.write(encrypted)
                output.flush()
                output.fd.sync()
            }
            atomicReplace(temporary, target)
            return target
        } catch (error: Exception) {
            temporary.delete()
            throw EvidenceUnavailableException("Evidence write failed", error)
        }
    }

    fun read(evidenceId: String): ByteArray {
        validateEvidenceId(evidenceId)
        val file = fileFor(evidenceId)
        if (!file.isFile) throw EvidenceUnavailableException("Evidence file is missing: $evidenceId")
        return try {
            cipher.decrypt(file.readBytes())
        } catch (error: Exception) {
            throw EvidenceUnavailableException("Evidence is unavailable or corrupted: $evidenceId", error)
        }
    }

    fun delete(evidenceId: String) {
        validateEvidenceId(evidenceId)
        fileFor(evidenceId).delete()
    }

    fun orphanedEvidence(referencedIds: Set<String>): Set<String> = rootDirectory
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
        .map { it.name.removeSuffix(FILE_SUFFIX) }
        .filterNot(referencedIds::contains)
        .toSet()

    private fun fileFor(evidenceId: String): File = File(rootDirectory, "$evidenceId$FILE_SUFFIX")

    private fun atomicReplace(temporary: File, target: File) {
        try {
            Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!temporary.renameTo(target)) {
                throw IllegalStateException("Atomic evidence replacement is unavailable")
            }
        }
    }

    private fun validateEvidenceId(evidenceId: String) {
        require(evidenceId.matches(EVIDENCE_ID_PATTERN)) {
            "Evidence id must be a bounded, path-safe identifier"
        }
    }

    companion object {
        private const val FILE_SUFFIX = ".shieldra"
        private val EVIDENCE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
