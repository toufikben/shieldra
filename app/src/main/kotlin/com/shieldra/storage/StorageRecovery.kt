package com.shieldra.storage

import java.io.File

/**
 * Why a Room database could not be opened (decisions §4.2–§4.4). Every value
 * maps to the same operator outcome: RecoveryRequired with the database kept.
 */
enum class StorageRecoveryReason {
    DOWNGRADE,
    MIGRATION_FAILURE,
    UNSUPPORTED_VERSION,
    UNREADABLE_STATE,
}

/** Outcome of opening local storage. Never a partially usable database. */
sealed class StorageOpenResult<out T> {
    data class Ready<out T>(val value: T) : StorageOpenResult<T>()

    data class RecoveryRequired<out T>(
        val reason: StorageRecoveryReason,
        val detail: String,
    ) : StorageOpenResult<T>()
}

/**
 * Turns an open failure into a decision §4 outcome.
 *
 * The reason is best-effort: Room and the framework SQLite layer only expose
 * free-form messages, so the marker strings below are the observed wording,
 * not a stable API. The guarantee that does not depend on them is that any
 * throwable at this boundary yields RecoveryRequired and nothing destructive.
 */
object StorageRecovery {
    fun classify(error: Throwable): StorageRecoveryReason {
        val text = error.messageChain()
        return when {
            // Checked first: Room's downgrade failure also mentions migrations.
            text.contains("downgrade") -> StorageRecoveryReason.DOWNGRADE
            text.contains("migrat") || text.contains("validation error whilst opening") ->
                StorageRecoveryReason.MIGRATION_FAILURE
            text.contains("cannot verify the data integrity") || text.contains("unsupported database version") ->
                StorageRecoveryReason.UNSUPPORTED_VERSION
            else -> StorageRecoveryReason.UNREADABLE_STATE
        }
    }

    /**
     * Diagnosis text for the operator. Exception messages at this layer carry
     * schema wording (table and column names), never stored event or evidence
     * content, so this is safe to surface in a recovery screen or log.
     */
    fun detail(error: Throwable): String = "${error.javaClass.name}: ${error.message ?: "<no message>"}"

    private fun Throwable.messageChain(): String {
        val builder = StringBuilder()
        var current: Throwable? = this
        // Cause cycles are legal in constructed throwables; cap the walk.
        repeat(MAX_CAUSE_DEPTH) {
            val node = current ?: return builder.toString().lowercase()
            node.message?.let { builder.append(it).append('\n') }
            current = node.cause
        }
        return builder.toString().lowercase()
    }

    private const val MAX_CAUSE_DEPTH = 16
}

/**
 * Runs an open action and converts failure into [StorageOpenResult].
 *
 * This is the single place that decides what happens when storage cannot be
 * opened, and it deliberately catches [Throwable]: no open failure may escape
 * as a crash that tempts a later caller into recreating or deleting the
 * database. The preserved file is only observed, never written or removed.
 */
object StorageRecoveryGuard {
    fun <T> open(preservedFile: File?, open: () -> T): StorageOpenResult<T> {
        val existedBefore = preservedFile?.exists() == true
        return try {
            StorageOpenResult.Ready(open())
        } catch (failure: Throwable) {
            val disappeared = existedBefore && preservedFile != null && !preservedFile.exists()
            val reason = if (disappeared) {
                StorageRecoveryReason.UNREADABLE_STATE
            } else {
                StorageRecovery.classify(failure)
            }
            val detail = if (disappeared) {
                "${StorageRecovery.detail(failure)}; database file disappeared during the failed open"
            } else {
                StorageRecovery.detail(failure)
            }
            StorageOpenResult.RecoveryRequired(reason, detail)
        }
    }
}
