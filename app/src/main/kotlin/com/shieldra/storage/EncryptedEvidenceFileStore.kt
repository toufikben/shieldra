package com.shieldra.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/**
 * Evidence-specific cryptographic API.
 *
 * AAD is MANDATORY for all evidence operations. No optional or bypass path exists.
 * This interface structurally prevents omitting AAD.
 */
interface EvidenceCipher {
    /**
     * Encrypts [plaintext] with mandatory AAD bound to the evidence identity.
     *
     * @param plaintext The evidence plaintext to encrypt.
     * @param aad The canonical AAD bytes for this evidence (version || class || id).
     * @return Encrypted payload envelope.
     * @throws IllegalArgumentException if AAD is null or empty.
     */
    fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray

    /**
     * Decrypts [ciphertext] with mandatory AAD bound to the evidence identity.
     *
     * @param ciphertext The encrypted payload envelope.
     * @param aad The canonical AAD bytes for this evidence (version || class || id).
     * @return Decrypted plaintext.
     * @throws EvidenceUnavailableException if authentication fails (wrong AAD, corruption, etc.).
     * @throws EvidenceKeyRecoveryRequiredException if the ciphertext is present but the
     * protecting Keystore key is missing (§6 / A-4 — operator recovery, not corrupt evidence).
     * @throws IllegalArgumentException if AAD is null or empty.
     */
    fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray
}

class EvidenceUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * §6 / amendment A-4: the evidence ciphertext is still present but the Keystore
 * key that protects it is missing through no deliberate action of the owner.
 * This is a storage/key-state recovery problem, not corrupt evidence, so it is
 * deliberately NOT a subtype of [EvidenceUnavailableException] — the read path
 * must not launder it into "unavailable". A confirmed factory reset (§5.7)
 * removes the files too, so post-reset reads stay [EvidenceUnavailableException].
 */
class EvidenceKeyRecoveryRequiredException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

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
    rootDirectory: File,
    private val cipher: EvidenceCipher,
    private val evidenceIdProvider: (String) -> ByteArray,
) {
    private val rootDirectory: File = rootDirectory.canonicalFile

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
            val aad = evidenceIdProvider(evidenceId)
            val encrypted = cipher.encrypt(plaintext, aad)
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
        if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) {
            throw EvidenceUnavailableException("Evidence file is missing or is a symlink: $evidenceId")
        }
        val aad = evidenceIdProvider(evidenceId)
        return try {
            cipher.decrypt(file.readBytes(), aad)
        } catch (error: EvidenceKeyRecoveryRequiredException) {
            // §6/A-4: do not launder a missing-key recovery signal into "unavailable".
            // The file is present; only the key is gone, so the operator must act.
            throw error
        } catch (error: Exception) {
            throw EvidenceUnavailableException("Evidence is unavailable or corrupted: $evidenceId", error)
        }
    }

    /**
     * Removes one evidence payload. True when a file was actually deleted, false
     * when there was nothing there to delete — a retention sweep must be able to
     * tell the two apart instead of reporting a deletion that did not happen.
     */
    fun delete(evidenceId: String): Boolean {
        validateEvidenceId(evidenceId)
        return deleteIfExists(fileFor(evidenceId), "Evidence delete failed")
    }

    /**
     * Removes one quarantined payload (§5.4, Q-K-2). Confined to the quarantine
     * directory by construction, so no id can reach an active evidence file.
     */
    fun deleteQuarantined(evidenceId: String): Boolean {
        validateEvidenceId(evidenceId)
        return deleteIfExists(quarantineFileFor(evidenceId), "Quarantined evidence delete failed")
    }

    /**
     * Top-level payloads with the age signal the store actually has: the file's
     * own modification time, which is when the payload was written. A time the
     * filesystem refuses to report becomes a null age, which the retention plan
     * treats as undeletable rather than guessing.
     */
    fun evidenceAges(): List<RetainedArtifact> = storedAges(rootDirectory)

    /** Quarantined payloads and their ages; quarantining is a rename, so the write time survives. */
    fun quarantinedAges(): List<RetainedArtifact> = storedAges(File(rootDirectory, QUARANTINE_DIRECTORY))

    private fun storedAges(directory: File): List<RetainedArtifact> = directory
        .listFiles()
        .orEmpty()
        .filter { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) && it.name.endsWith(FILE_SUFFIX) }
        .map { file ->
            RetainedArtifact(
                id = file.name.removeSuffix(FILE_SUFFIX),
                storedAtEpochMs = file.lastModified().takeIf { it > 0L },
            )
        }

    fun orphanedEvidence(referencedIds: Set<String>): Set<String> = rootDirectory
        .listFiles()
        .orEmpty()
        .filter { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) && it.name.endsWith(FILE_SUFFIX) }
        .map { it.name.removeSuffix(FILE_SUFFIX) }
        .filterNot(referencedIds::contains)
        .toSet()

    fun reconcile(referencedIds: Set<String>, quarantineOrphans: Boolean = true): EvidenceReconciliationReport {
        referencedIds.forEach(::validateEvidenceId)
        val existingIds = rootDirectory.listFiles()
            .orEmpty()
            .filter { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) && it.name.endsWith(FILE_SUFFIX) }
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

    private fun quarantineFileFor(evidenceId: String): File =
        File(File(rootDirectory, QUARANTINE_DIRECTORY), "$evidenceId$FILE_SUFFIX")

    private fun quarantine(evidenceId: String): Boolean {
        val quarantineDirectory = File(rootDirectory, QUARANTINE_DIRECTORY)
        if (!quarantineDirectory.exists() && !quarantineDirectory.mkdirs()) return false
        val source = fileFor(evidenceId)
        val target = quarantineFileFor(evidenceId)
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
        /** Shared with [FactoryReset], which must reach the same directory to empty it. */
        internal const val QUARANTINE_DIRECTORY = ".quarantine"
        private val EVIDENCE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
