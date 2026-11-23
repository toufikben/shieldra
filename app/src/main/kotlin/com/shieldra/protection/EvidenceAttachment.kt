package com.shieldra.protection

import com.shieldra.domain.EventId
import com.shieldra.domain.EventRepository
import com.shieldra.domain.EvidenceReference
import com.shieldra.domain.RepositoryResult

/** What the store may safely do with an evidence payload after its reference write. */
internal enum class EvidenceAttachmentState {
    /** The reference is durably on the event; the payload must be kept. */
    ATTACHED,

    /** The reference is provably absent; the payload is an orphan and may be deleted. */
    ORPHAN,

    /** The write's outcome could not be read back; deleting could destroy live evidence. */
    UNKNOWN,
}

/**
 * Decides whether an evidence payload is attached without trusting the write's return value alone.
 *
 * A write can persist and still report failure — the Room insert commits before the persistence
 * callback runs, so a throwing callback surfaces as an exception with the reference already stored.
 * Only a confirmed absence permits deletion, because deleting an attached payload loses the photo
 * the incident already claims to hold.
 */
internal fun classifyEvidenceAttachment(
    repository: EventRepository,
    eventId: EventId,
    reference: EvidenceReference,
    record: () -> RepositoryResult,
): EvidenceAttachmentState {
    val recorded = runCatching { record() }.getOrNull()
    if (recorded == RepositoryResult.Applied) return EvidenceAttachmentState.ATTACHED
    val persisted = runCatching {
        repository.find(eventId)?.evidenceReferences?.any { it == reference }
    }.getOrNull() ?: return EvidenceAttachmentState.UNKNOWN
    return if (persisted) EvidenceAttachmentState.ATTACHED else EvidenceAttachmentState.ORPHAN
}
