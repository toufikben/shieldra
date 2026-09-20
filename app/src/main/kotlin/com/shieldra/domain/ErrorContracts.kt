package com.shieldra.domain

enum class ContractErrorType {
    INVALID_STATE,
    UNAVAILABLE_RESOURCE,
    PERMISSION_RESTRICTION,
    AUTHENTICATION_REQUIRED,
    DELIVERY_FAILURE,
    EVIDENCE_VALIDATION_FAILURE,
    STORAGE_FAILURE,
    PLATFORM_LIMITATION,
}

data class ContractError(
    val type: ContractErrorType,
    val message: String? = null,
)
