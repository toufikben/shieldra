package com.shieldra.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Per-evidence key rotation state (decision §3.4).
 * PENDING → IN_PROGRESS → VERIFIED, or IN_PROGRESS → FAILED.
 */
enum class RotationItemState {
    PENDING,
    IN_PROGRESS,
    VERIFIED,
    FAILED,
}

/** Stored rotation metadata for one evidence entry (decision §3.2). */
data class RotationMetadata(
    val keyVersion: String,
    val aadVersion: String,
)

/** Rotation request: migrate one evidence from one key/AAD version to another. */
data class RotationRequest(
    val evidenceId: String,
    val fromKeyVersion: String,
    val toKeyVersion: String,
    val fromAadVersion: String,
    val toAadVersion: String,
)

/** Outcome of rotating one evidence entry. */
sealed interface RotationResult {
    data class Migrated(val evidenceId: String, val resumed: Boolean = false) : RotationResult
    data class AlreadyMigrated(val evidenceId: String) : RotationResult
    data class Failed(val evidenceId: String, val reason: String) : RotationResult
}

/** Raw committed/temporary file access for rotation. Temp files are never valid evidence. */
interface RotationFileGateway {
    fun readCommitted(evidenceId: String): ByteArray
    fun hasTemp(evidenceId: String): Boolean
    fun writeTemp(evidenceId: String, payload: ByteArray)
    fun readTemp(evidenceId: String): ByteArray
    fun commitTemp(evidenceId: String)
    fun discardTemp(evidenceId: String)
}

/** Rotation metadata access: versions read from Room, marked only after verified commit. */
interface RotationMetadataGateway {
    fun versionsOf(evidenceId: String): RotationMetadata?
    fun markMigrated(evidenceId: String, keyVersion: String, aadVersion: String)
}

/** Provides the cipher bound to a stored key version. Never deletes keys. */
interface RotationCipherFactory {
    fun cipherFor(keyVersion: String): EvidenceCipher
}

/** Builds canonical AAD for an evidence entry at a stored AAD version. */
interface RotationAadFactory {
    fun aadFor(evidenceId: String, aadVersion: String): ByteArray
}

/**
 * Resumable per-evidence key rotation (decisions §3.3, §3.4).
 *
 * Algorithm per evidence:
 * ```
 * READ metadata → DECRYPT with old key → VERIFY → ENCRYPT with new key + new AAD
 * → WRITE temp → VERIFY temp → atomic COMMIT → VERIFY replacement → MARK migrated
 * ```
 *
 * Resume rules (§3.4):
 * - Completed items (metadata already at target) are skipped, never redone.
 * - A leftover temp is verified with the new cipher: valid → commit it;
 *   invalid → discard and redo. Never restarts completed work.
 * - If the old cipher rejects the committed file but the new cipher accepts
 *   it, a previous run committed without marking: mark and finish (adopt).
 * - Corrupted evidence, missing keys, and missing metadata fail closed with
 *   the original evidence untouched. The old key is never deleted here.
 */
class EvidenceKeyRotator(
    private val files: RotationFileGateway,
    private val metadata: RotationMetadataGateway,
    private val ciphers: RotationCipherFactory,
    private val aad: RotationAadFactory,
) {
    private val states = mutableMapOf<String, RotationItemState>()
    private val lock = Any()

    fun stateOf(evidenceId: String): RotationItemState = synchronized(lock) {
        states[evidenceId] ?: RotationItemState.PENDING
    }

    private fun setState(evidenceId: String, state: RotationItemState) = synchronized(lock) {
        states[evidenceId] = state
    }

    fun rotate(request: RotationRequest): RotationResult {
        val id = request.evidenceId
        val stored = try {
            metadata.versionsOf(id)
        } catch (error: Exception) {
            return fail(id, "rotation metadata unreadable: ${error.message}")
        } ?: return fail(id, "rotation metadata missing")
        if (stored.keyVersion == request.toKeyVersion && stored.aadVersion == request.toAadVersion) {
            setState(id, RotationItemState.VERIFIED)
            return RotationResult.AlreadyMigrated(id)
        }
        if (stored.keyVersion != request.fromKeyVersion || stored.aadVersion != request.fromAadVersion) {
            return fail(id, "unexpected stored versions: ${stored.keyVersion}/${stored.aadVersion}")
        }
        setState(id, RotationItemState.IN_PROGRESS)

        val oldCipher: EvidenceCipher
        val newCipher: EvidenceCipher
        val oldAad: ByteArray
        val newAad: ByteArray
        try {
            oldCipher = ciphers.cipherFor(request.fromKeyVersion)
            newCipher = ciphers.cipherFor(request.toKeyVersion)
            oldAad = aad.aadFor(id, request.fromAadVersion)
            newAad = aad.aadFor(id, request.toAadVersion)
        } catch (error: Exception) {
            return fail(id, "rotation endpoint unresolvable: ${error.message}")
        }

        // Resume path 1: a leftover temp from an interrupted run.
        if (files.hasTemp(id)) {
            try {
                val tempBytes = files.readTemp(id)
                val tempPlain = newCipher.decrypt(tempBytes, newAad)
                files.commitTemp(id)
                return verifyAndMark(id, request, newCipher, newAad, tempPlain, resumed = true)
            } catch (error: Exception) {
                try {
                    files.discardTemp(id)
                } catch (discardError: Exception) {
                    return fail(id, "stale rotation temp unverifiable and not discardable")
                }
            }
        }

        // Fresh path: read committed bytes, decrypt with the old key.
        val committed: ByteArray = try {
            files.readCommitted(id)
        } catch (error: Exception) {
            return fail(id, "committed evidence unreadable: ${error.message}")
        }
        val plaintext: ByteArray = try {
            oldCipher.decrypt(committed, oldAad)
        } catch (oldError: Exception) {
            // Resume path 2: a previous run committed the new file but did not mark.
            return adoptIfAlreadyRotated(id, request, newCipher, newAad, committed)
        }

        return try {
            val rotated = newCipher.encrypt(plaintext, newAad)
            files.writeTemp(id, rotated)
            val tempPlain = newCipher.decrypt(files.readTemp(id), newAad)
            require(tempPlain.contentEquals(plaintext)) { "rotated temp verification failed" }
            files.commitTemp(id)
            verifyAndMark(id, request, newCipher, newAad, plaintext, resumed = false)
        } catch (error: Exception) {
            try {
                files.discardTemp(id)
            } catch (_: Exception) {
            }
            fail(id, "rotation step failed: ${error.message}")
        }
    }

    fun rotateAll(ids: Collection<String>, requestFor: (String) -> RotationRequest): Map<String, RotationResult> =
        ids.associateWith { rotate(requestFor(it)) }

    private fun verifyAndMark(
        evidenceId: String,
        request: RotationRequest,
        newCipher: EvidenceCipher,
        newAad: ByteArray,
        expectedPlaintext: ByteArray,
        resumed: Boolean,
    ): RotationResult {
        val committed: ByteArray = try {
            files.readCommitted(evidenceId)
        } catch (error: Exception) {
            return fail(evidenceId, "replacement unreadable after commit: ${error.message}")
        }
        try {
            val roundTrip = newCipher.decrypt(committed, newAad)
            require(roundTrip.contentEquals(expectedPlaintext)) { "replacement verification failed" }
        } catch (error: Exception) {
            return fail(evidenceId, "replacement verification failed: ${error.message}")
        }
        return try {
            metadata.markMigrated(evidenceId, request.toKeyVersion, request.toAadVersion)
            setState(evidenceId, RotationItemState.VERIFIED)
            RotationResult.Migrated(evidenceId, resumed)
        } catch (error: Exception) {
            fail(evidenceId, "mark migrated failed after verified commit: ${error.message}")
        }
    }

    private fun adoptIfAlreadyRotated(
        evidenceId: String,
        request: RotationRequest,
        newCipher: EvidenceCipher,
        newAad: ByteArray,
        committed: ByteArray,
    ): RotationResult {
        return try {
            newCipher.decrypt(committed, newAad)
            try {
                metadata.markMigrated(evidenceId, request.toKeyVersion, request.toAadVersion)
            } catch (error: Exception) {
                return fail(evidenceId, "adopt mark failed: ${error.message}")
            }
            setState(evidenceId, RotationItemState.VERIFIED)
            RotationResult.Migrated(evidenceId, resumed = true)
        } catch (_: Exception) {
            fail(evidenceId, "evidence undecryptable with old and new keys")
        }
    }

    private fun fail(evidenceId: String, reason: String): RotationResult {
        setState(evidenceId, RotationItemState.FAILED)
        return RotationResult.Failed(evidenceId, reason)
    }
}

/**
 * Filesystem rotation gateway over an evidence directory.
 * Committed files use the `.shieldra` suffix; rotation temps use
 * `.rotate.tmp` so they are never valid evidence and are cleaned by
 * reconciliation sweeps that delete `*.tmp` files.
 */
class FileSystemRotationGateway(rootDirectory: File) : RotationFileGateway {
    private val root: File = rootDirectory.canonicalFile

    init {
        require(root.exists() || root.mkdirs()) { "Unable to create evidence directory" }
    }

    override fun readCommitted(evidenceId: String): ByteArray {
        validateEvidenceId(evidenceId)
        val file = fileFor(evidenceId)
        if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) {
            throw EvidenceUnavailableException("Evidence file is missing or is a symlink: $evidenceId")
        }
        return file.readBytes()
    }

    override fun hasTemp(evidenceId: String): Boolean {
        validateEvidenceId(evidenceId)
        return tempFor(evidenceId).isFile
    }

    override fun writeTemp(evidenceId: String, payload: ByteArray) {
        validateEvidenceId(evidenceId)
        val temp = tempFor(evidenceId)
        FileOutputStream(temp).use { output ->
            output.write(payload)
            output.flush()
            output.fd.sync()
        }
    }

    override fun readTemp(evidenceId: String): ByteArray {
        validateEvidenceId(evidenceId)
        return tempFor(evidenceId).readBytes()
    }

    override fun commitTemp(evidenceId: String) {
        validateEvidenceId(evidenceId)
        val temp = tempFor(evidenceId)
        val target = fileFor(evidenceId)
        try {
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!temp.renameTo(target)) {
                throw IllegalStateException("Atomic rotation replacement is unavailable")
            }
        }
    }

    override fun discardTemp(evidenceId: String) {
        validateEvidenceId(evidenceId)
        Files.deleteIfExists(tempFor(evidenceId).toPath())
    }

    private fun validateEvidenceId(evidenceId: String) {
        require(evidenceId.matches(EVIDENCE_ID_PATTERN)) {
            "Evidence id must be a bounded, path-safe identifier"
        }
    }

    private fun fileFor(evidenceId: String): File = File(root, "$evidenceId.shieldra")

    private fun tempFor(evidenceId: String): File = File(root, ".$evidenceId.rotate.tmp")

    companion object {
        private val EVIDENCE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}

/** Production AAD factory: only the canonical AAD-v1 version is known; others fail closed. */
object RotationAadVersions : RotationAadFactory {
    override fun aadFor(evidenceId: String, aadVersion: String): ByteArray {
        require(aadVersion == EvidenceKeyPolicy.AAD_VERSION_V1) {
            "Unsupported evidence AAD version: $aadVersion"
        }
        return EvidenceAadBuilder.forEvidence(evidenceId)
    }
}

/**
 * Production cipher factory: resolves stored key versions to versioned
 * Keystore aliases. Key creation happens inside the encryptor on first use;
 * keys are never deleted by rotation.
 */
class KeystoreRotationCipherFactory(
    private val encryptor: AndroidKeystoreEncryptor,
    private val policy: EncryptionPolicy = EvidenceKeyPolicy.EVIDENCE_POLICY,
) : RotationCipherFactory {
    override fun cipherFor(keyVersion: String): EvidenceCipher =
        KeystoreEvidenceCipherWithAad(
            encryptor,
            EvidenceKeyPolicy.aliasForKeyVersion(keyVersion),
            policy,
        )
}
