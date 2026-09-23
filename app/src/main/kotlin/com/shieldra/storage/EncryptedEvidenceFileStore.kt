package com.shieldra.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
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

data class EvidenceReconciliationReport(
    val missingReferencedIds: Set<String>,
    val orphanedFileIds: Set<String>,
    val quarantinedFileIds: Set<String>,
    val deletedTemporaryFiles: Set<String>,
)

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
            try {
                deleteIfExists(temporary, "Temporary evidence cleanup failed")
            } catch (cleanupError: EvidenceUnavailableException) {
                cleanupError.addSuppressed(error)
                throw cleanupError
            }
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
        deleteIfExists(fileFor(evidenceId), "Evidence delete failed")
    }

    fun orphanedEvidence(referencedIds: Set<String>): Set<String> = rootDirectory
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
        .map { it.name.removeSuffix(FILE_SUFFIX) }
        .filterNot(referencedIds::contains)
        .toSet()

    fun reconcile(referencedIds: Set<String>, quarantineOrphans: Boolean = true): EvidenceReconciliationReport {
        referencedIds.forEach(::validateEvidenceId)
        val existingIds = rootDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
            .map { it.name.removeSuffix(FILE_SUFFIX) }
            .toSet()
        val orphaned = existingIds - referencedIds
        val quarantined = if (quarantineOrphans) {
            orphaned.filter { quarantine(it) }.toSet()
        } else {
            emptySet()
        }
        val deletedTemporary = rootDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(TEMP_SUFFIX) }
            .mapNotNull { temporary ->
                if (deleteIfExists(temporary, "Temporary evidence cleanup failed")) {
                    temporary.name
                } else {
                    null
                }
            }
            .toSet()
        return EvidenceReconciliationReport(
            missingReferencedIds = referencedIds - existingIds,
            orphanedFileIds = orphaned,
            quarantinedFileIds = quarantined,
            deletedTemporaryFiles = deletedTemporary,
        )
    }

    private fun fileFor(evidenceId: String): File = File(rootDirectory, "$evidenceId$FILE_SUFFIX")

    private fun quarantine(evidenceId: String): Boolean {
        val quarantineDirectory = File(rootDirectory, QUARANTINE_DIRECTORY)
        if (!quarantineDirectory.exists() && !quarantineDirectory.mkdirs()) return false
        val source = fileFor(evidenceId)
        val target = File(quarantineDirectory, "$evidenceId$FILE_SUFFIX")
        if (target.exists()) {
            throw EvidenceUnavailableException(
                "Quarantine target already exists and will not be replaced: ${target.name}",
            )
        }
        return try {
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE)
            true
        } catch (_: AtomicMoveNotSupportedException) {
            try {
                Files.move(source.toPath(), target.toPath())
                true
            } catch (error: FileAlreadyExistsException) {
                throw EvidenceUnavailableException(
                    "Quarantine target already exists and will not be replaced: ${target.name}",
                    error,
                )
            } catch (error: Exception) {
                throw EvidenceUnavailableException("Evidence quarantine failed: $evidenceId", error)
            }
        } catch (error: FileAlreadyExistsException) {
            throw EvidenceUnavailableException(
                "Quarantine target already exists and will not be replaced: ${target.name}",
                error,
            )
        } catch (error: Exception) {
            throw EvidenceUnavailableException("Evidence quarantine failed: $evidenceId", error)
        }
    }

    private fun deleteIfExists(file: File, operation: String): Boolean = try {
        Files.deleteIfExists(file.toPath())
    } catch (error: Exception) {
        throw EvidenceUnavailableException("$operation: ${file.name}", error)
    }

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
        private const val TEMP_SUFFIX = ".tmp"
        private const val QUARANTINE_DIRECTORY = ".quarantine"
        private val EVIDENCE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
