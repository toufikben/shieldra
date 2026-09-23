package com.shieldra.storage.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SecurityEventEntity::class,
        EvidenceReferenceEntity::class,
        DeliveryAttemptEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ShieldraDatabase : RoomDatabase() {
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun evidenceReferenceDao(): EvidenceReferenceDao
    abstract fun deliveryAttemptDao(): DeliveryAttemptDao
}
