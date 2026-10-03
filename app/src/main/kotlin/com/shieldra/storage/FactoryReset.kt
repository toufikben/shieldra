package com.shieldra.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/**
 * Outcome of an explicit security-storage reset (decision §5.7, amendment A-3).
 */
sealed interface FactoryResetResult {

    /** Storage absence was proven; the reset is done. */
    data class Completed(
        val deletedAliases: List<String>,
        val removedFileCount: Int,
    ) : FactoryResetResult

    /**
     * Removal started but some step failed. A-3: this is never reported as
     * success. The failures are tombstoned so the caller can surface them.
     */
    data class Tombstoned(
        val deletedAliases: List<String>,
        val failures: List<String>,
    ) : FactoryResetResult

    /** Reset was not authorized; nothing was touched. */
    data class Refused(val reason: String) : FactoryResetResult
}

/**
 * Explicit, confirmed, destructive security-storage reset (decision §5.7).
 *
 * Order (amendment A-3): removal of evidence files, Room metadata and the
 * Keystore key happens first; storage absence is verified afterwards. A
 * failure at any removal step is tombstoned rather than reported as success,
 * because "reset succeeded" is a claim the user will act on.
 *
 * Nothing here runs implicitly. The class performs no work in its constructor
 * and [execute] requires [confirmed], so §5.6 (no silent deletion during
 * startup, migration, key initialization, failed decryption, missing reference
 * or recovery) holds by construction rather than by convention.
 */
class FactoryReset(
    val evidenceDirectory: File,
    private val metadataDatabase: File,
    private val keyDeletion: (String) -> Boolean,
) {

    /**
     * @param aliases Keystore aliases to delete, including the rotation target
     *   (amendment A-2 makes this mandatory, not optional).
     * @param confirmed explicit destructive confirmation from the caller.
     * @param survivingFiles files that must be gone for the reset to count as
     *   verified. Defaults to the contents of [evidenceDirectory].
     */
    fun execute(
        aliases: List<String>,
        confirmed: Boolean = false,
        survivingFiles: List<File> = evidenceDirectory.listFiles().orEmpty().toList(),
    ): FactoryResetResult {
        if (!confirmed) {
            return FactoryResetResult.Refused("Factory reset requires an explicit destructive confirmation")
        }
        if (aliases.isEmpty()) {
            return FactoryResetResult.Refused("Factory reset requires at least one evidence key alias to delete")
        }

        val failures = mutableListOf<String>()
        var removed = 0

        // Step 3: evidence files.
        for (file in evidenceDirectory.listFiles().orEmpty()) {
            if (file.isDirectory) continue
            try {
                if (Files.deleteIfExists(file.toPath())) removed++
            } catch (error: Exception) {
                failures += "evidence file ${file.name} could not be removed: ${error.message}"
            }
        }
        // §5.2: quarantine is not a valid evidence namespace and must not
        // survive a reset either. The store names it ".quarantine"; this used to
        // empty "quarantine", so quarantined ciphertext outlived every reset. The
        // emptied directory goes too, so nothing of the evidence tree is left.
        for (directory in quarantineDirectories()) {
            for (quarantined in directory.listFiles().orEmpty()) {
                try {
                    if (Files.deleteIfExists(quarantined.toPath())) removed++
                } catch (error: Exception) {
                    failures += "quarantined file ${quarantined.name} could not be removed: ${error.message}"
                }
            }
            try {
                Files.deleteIfExists(directory.toPath())
            } catch (error: Exception) {
                failures += "quarantine directory ${directory.name} could not be removed: ${error.message}"
            }
        }
        // Checked after the quarantine is gone: the default list is the
        // directory's contents, which include the quarantine directory itself.
        for (file in survivingFiles) {
            if (file.exists()) {
                failures += "evidence file ${file.name} is still present after removal"
            }
        }

        // Step 4: Room event metadata, with the files SQLite keeps beside it.
        // Room writes in WAL mode, so recent rows can live in "-wal" until a
        // checkpoint; deleting only the main file left them readable.
        for (databaseFile in databaseFiles()) {
            try {
                if (Files.deleteIfExists(databaseFile.toPath())) removed++
            } catch (error: Exception) {
                failures += "Room database file ${databaseFile.name} could not be removed: ${error.message}"
            }
        }

        // Step 6: Keystore evidence key. A-2: mandatory.
        val deletedAliases = mutableListOf<String>()
        for (alias in aliases) {
            try {
                if (keyDeletion(alias)) deletedAliases += alias
            } catch (error: Exception) {
                failures += "Keystore key $alias could not be deleted: ${error.message}"
            }
        }

        // Step 7: verify storage absence — only meaningful after removal. The
        // check walks subdirectories too, so nothing in quarantine can hide.
        val evidenceStillPresent = evidenceDirectory.walkTopDown()
            .any { it != evidenceDirectory && Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) }
        if (evidenceStillPresent) {
            failures += "evidence storage is still present after reset"
        }
        for (databaseFile in databaseFiles()) {
            if (databaseFile.exists()) {
                failures += "Room database file ${databaseFile.name} is still present after reset"
            }
        }

        return if (failures.isEmpty()) {
            FactoryResetResult.Completed(deletedAliases, removed)
        } else {
            FactoryResetResult.Tombstoned(deletedAliases, failures)
        }
    }

    /** The store's quarantine, plus the misspelt one earlier builds emptied, in case it exists. */
    private fun quarantineDirectories(): List<File> = listOf(
        File(evidenceDirectory, EncryptedEvidenceFileStore.QUARANTINE_DIRECTORY),
        File(evidenceDirectory, "quarantine"),
    )

    private fun databaseFiles(): List<File> = listOf("", "-wal", "-shm", "-journal")
        .map { suffix -> File(metadataDatabase.path + suffix) }
}
