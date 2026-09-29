package com.shieldra.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Durable record of which evidence key generation is active (owner protocol §9/§10).
 *
 * It holds exactly three facts: the active key version, the versions that are
 * RETIRED, and when the last rotation completed. Room stays the authority on
 * which evidence still references a version; this record only says what the
 * rotation clock should be.
 *
 * The file lives in the application's no-backup root, deliberately outside the
 * encrypted evidence directory: [EncryptedEvidenceFileStore.reconcile] sweeps
 * `*.tmp` files there, and a rotation record must never be swept mid-write.
 *
 * Every read path fails closed. A damaged, truncated, or unknown-schema record
 * throws instead of assuming v1, because assuming a version is exactly how a
 * rotation could retire a key that evidence still needs.
 */
class FileBackedKeyLifecycle(
    private val file: File,
    private val nowMillis: () -> Long,
) : KeyLifecycleGateway {

    override fun snapshot(): KeyLifecycleSnapshot = load().toSnapshot()

    override fun nextKeyVersion(current: String): String? = when (current) {
        EvidenceKeyPolicy.KEY_VERSION_V1 -> EvidenceKeyPolicy.KEY_VERSION_V2
        else -> null
    }

    override fun completeRotation(fromKeyVersion: String, toKeyVersion: String, atEpochMs: Long) {
        require(atEpochMs >= 0L) { "Rotation completion timestamp cannot be negative" }
        val current = load()
        check(current.activeKeyVersion == fromKeyVersion) {
            "key lifecycle record is on '${current.activeKeyVersion}', not '$fromKeyVersion'"
        }
        check(nextKeyVersion(fromKeyVersion) == toKeyVersion) {
            "'$toKeyVersion' is not an approved successor to '$fromKeyVersion'"
        }
        check(toKeyVersion !in current.retiredKeyVersions) {
            "key version '$toKeyVersion' is already retired"
        }
        write(
            Record(
                activeKeyVersion = toKeyVersion,
                retiredKeyVersions = current.retiredKeyVersions + fromKeyVersion,
                lastRotationEpochMs = atEpochMs,
                createdEpochMs = current.createdEpochMs,
            ),
        )
    }

    private fun load(): Record {
        if (!file.exists()) {
            val now = nowMillis()
            val created = Record(
                activeKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
                retiredKeyVersions = emptySet(),
                lastRotationEpochMs = now,
                createdEpochMs = now,
            )
            write(created)
            return created
        }
        val lines = try {
            file.readText().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        } catch (error: Exception) {
            throw IllegalStateException("Key lifecycle record is unreadable: ${file.name}", error)
        }
        check(lines.firstOrNull() == SCHEMA) {
            "Key lifecycle record has an unsupported schema: ${lines.firstOrNull()}"
        }
        val fields = lines.drop(1).associate { line ->
            val separator = line.indexOf('=')
            check(separator > 0) { "Key lifecycle record has a malformed field: $line" }
            line.substring(0, separator) to line.substring(separator + 1)
        }
        check(fields.keys == REQUIRED_FIELDS) {
            "Key lifecycle record is incomplete: ${fields.keys.sorted()}"
        }
        val active = fields.getValue(FIELD_ACTIVE)
        val retired = fields.getValue(FIELD_RETIRED).split(',').filter { it.isNotBlank() }.toSet()
        val lastRotation = timestamp(fields.getValue(FIELD_LAST_ROTATION), FIELD_LAST_ROTATION)
        val created = timestamp(fields.getValue(FIELD_CREATED), FIELD_CREATED)
        check(active !in retired) { "Key version '$active' is both active and retired" }
        return Record(active, retired, lastRotation, created)
    }

    private fun timestamp(raw: String, field: String): Long {
        val value = raw.toLongOrNull()
        if (value == null || value < 0L) {
            throw IllegalStateException("Key lifecycle record has an invalid '$field': $raw")
        }
        return value
    }

    private fun Record.toSnapshot() = KeyLifecycleSnapshot(
        activeKeyVersion = activeKeyVersion,
        retiredKeyVersions = retiredKeyVersions,
        lastRotationEpochMs = lastRotationEpochMs,
    )

    private fun write(record: Record) {
        val text = buildString {
            append(SCHEMA).append('\n')
            append(FIELD_ACTIVE).append('=').append(record.activeKeyVersion).append('\n')
            append(FIELD_RETIRED).append('=').append(record.retiredKeyVersions.sorted().joinToString(",")).append('\n')
            append(FIELD_LAST_ROTATION).append('=').append(record.lastRotationEpochMs).append('\n')
            append(FIELD_CREATED).append('=').append(record.createdEpochMs).append('\n')
        }
        val parent = file.parentFile
        if (parent == null || (!parent.isDirectory && !parent.mkdirs())) {
            throw IllegalStateException("Key lifecycle record directory is unavailable: ${file.name}")
        }
        val temporary = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                if (!temporary.renameTo(file)) {
                    throw IllegalStateException("Atomic key lifecycle replacement is unavailable")
                }
            }
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(temporary.toPath()) }
            throw IllegalStateException("Key lifecycle record write failed: ${file.name}", error)
        }
    }

    private data class Record(
        val activeKeyVersion: String,
        val retiredKeyVersions: Set<String>,
        val lastRotationEpochMs: Long,
        val createdEpochMs: Long,
    )

    companion object {
        private const val SCHEMA = "shieldra-key-lifecycle/1"
        private const val FIELD_ACTIVE = "active"
        private const val FIELD_RETIRED = "retired"
        private const val FIELD_LAST_ROTATION = "lastRotationEpochMs"
        private const val FIELD_CREATED = "createdEpochMs"
        private val REQUIRED_FIELDS = setOf(FIELD_ACTIVE, FIELD_RETIRED, FIELD_LAST_ROTATION, FIELD_CREATED)
        const val FILE_NAME = "shieldra-key-lifecycle.bin"

        /**
         * Placement of the record inside the application's no-backup root. Kept out
         * of the evidence subdirectory so the evidence reconciliation sweep cannot
         * touch it or its temporary file.
         */
        fun fileIn(storageRoot: File): File = File(storageRoot, FILE_NAME)
    }
}
