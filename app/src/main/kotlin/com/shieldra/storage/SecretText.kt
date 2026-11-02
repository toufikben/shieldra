package com.shieldra.storage

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Seals one short secret into a single preference-safe line, and opens it back.
 *
 * This is the AES-256-GCM machinery the evidence and delivery secrets already use, wrapped for the
 * value-sized case: the field name is bound as additional authenticated data, so a sealed value cannot
 * be moved into another slot or another app, and the key has its own alias so rotating or losing the
 * evidence key cannot unread it. Two rules make it safe on a hot path:
 *
 *  - sealing never falls back to plain text — if the key cannot be used, the caller is told with null
 *    and must store nothing rather than write the secret in the clear;
 *  - opening never throws — a line that cannot be opened reads as absent, which is what an app does
 *    with a channel it cannot reach.
 *
 * It deliberately does NOT share a namespace with `DeliverySettingsStore.sealSecret`, whose AAD
 * ("shieldra-delivery-v1:…") and key alias are already written into installs in the wild. Unifying the
 * two would unread every configured bot token and app password, so the two spellings coexist.
 */
class SecretText private constructor(
    private val encryptor: AndroidKeystoreEncryptor,
    private val keyAlias: String,
    private val field: String,
) {
    /** The line the app writes for one secret value. */
    constructor(context: Context, field: String) : this(
        AndroidKeystoreEncryptor(applicationContext = context.applicationContext),
        KEY_ALIAS,
        field,
    )

    /** For a caller that already holds an encryptor, and for the JVM tests that stand one in. */
    constructor(encryptor: AndroidKeystoreEncryptor, field: String) : this(encryptor, KEY_ALIAS, field)

    /** Null when the value could not be sealed: the caller stores nothing rather than plain text. */
    fun seal(plaintext: String): String? = runCatching {
        val payload = encryptor.encrypt(
            plaintext = plaintext.toByteArray(StandardCharsets.UTF_8),
            keyAlias = keyAlias,
            policy = EvidenceKeyPolicy.EVIDENCE_POLICY,
            associatedData = aad(),
        )
        SEALED_PREFIX + Base64.getEncoder().encodeToString(payload.toByteArray())
    }.getOrNull()

    /** Null for an unmarked, damaged, or unopenable line — never a guess and never an exception. */
    fun open(stored: String): String? {
        if (!isSealed(stored)) return null
        return runCatching {
            val payload = EncryptedPayload.fromByteArray(
                Base64.getDecoder().decode(stored.substring(SEALED_PREFIX.length)),
            )
            String(
                encryptor.decrypt(payload, keyAlias, EvidenceKeyPolicy.EVIDENCE_POLICY, aad()),
                StandardCharsets.UTF_8,
            )
        }.getOrNull()
    }

    companion object {
        /** Marks a sealed line. Anything without it was written before this build existed. */
        const val SEALED_PREFIX = "s1:"

        /** Its own key, so losing another cannot unread these values — and [eraseAll] destroys it. */
        const val KEY_ALIAS = "shieldra_secret_v1"

        fun isSealed(stored: String): Boolean = stored.startsWith(SEALED_PREFIX)
    }

    private fun aad(): ByteArray = "shieldra-secret-v1:$field".toByteArray(StandardCharsets.UTF_8)
}
