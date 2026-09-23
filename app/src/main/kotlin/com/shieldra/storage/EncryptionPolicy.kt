package com.shieldra.storage

/**
 * Explicit cryptographic choices required by the caller.
 * There is intentionally no default instance: product/security review must
 * select and record these values before wiring the adapter into production.
 */
data class EncryptionPolicy(
    val keySizeBits: Int,
    val requireUserAuthentication: Boolean,
    val userAuthenticationValiditySeconds: Int,
    val invalidateOnBiometricEnrollment: Boolean,
) {
    init {
        require(keySizeBits == 256) {
            "Only the reviewed AES-256 candidate is supported by this adapter"
        }
        require(userAuthenticationValiditySeconds >= 0) {
            "Authentication validity cannot be negative"
        }
        if (requireUserAuthentication) {
            require(userAuthenticationValiditySeconds > 0) {
                "User-authenticated keys require an explicit validity window"
            }
        }
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PROVIDER = "AndroidKeyStore"
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val PAYLOAD_VERSION = 1
    }
}

class StorageSecurityConfigurationException(message: String) : IllegalStateException(message)
class KeyMaterialUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
