package com.shieldra.evidence

import com.shieldra.domain.Age
import java.time.Instant

enum class EvidenceKind {
    PHOTO,
    LOCATION,
    PHOTO_AND_LOCATION,
}

enum class LocationFreshness {
    CURRENT,
    LAST_KNOWN,
}

enum class LocationSource {
    PLATFORM_PROVIDER,
    CACHED_PROVIDER,
    USER_PROVIDED,
}

data class LocationEvidence(
    val freshness: LocationFreshness,
    val capturedAt: Instant,
    val age: Age,
    val accuracyMeters: Double,
    val source: LocationSource,
) {
    init {
        require(accuracyMeters >= 0) { "Accuracy cannot be negative" }
    }
}

sealed interface Evidence {
    val id: String
    val kind: EvidenceKind

    data class Photo(
        override val id: String,
        val contentReference: String,
    ) : Evidence {
        override val kind: EvidenceKind = EvidenceKind.PHOTO
    }

    data class Location(
        override val id: String,
        val value: LocationEvidence,
    ) : Evidence {
        override val kind: EvidenceKind = EvidenceKind.LOCATION
    }

    data class PhotoAndLocation(
        override val id: String,
        val photoContentReference: String,
        val location: LocationEvidence,
    ) : Evidence {
        override val kind: EvidenceKind = EvidenceKind.PHOTO_AND_LOCATION
    }
}

enum class EvidenceValidationStatus {
    VALID,
    INVALID,
}

data class EvidenceValidationResult(
    val status: EvidenceValidationStatus,
    val reason: String? = null,
)

interface CaptureManager

interface EvidenceValidator {
    fun validate(evidence: Evidence): EvidenceValidationResult
}

interface EncryptedVault
