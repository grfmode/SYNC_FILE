package com.sync.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata kecil milik database sendiri.
 *
 * [fingerprint] diisi saat database dibuka dan dipakai validator backup untuk
 * memastikan snapshot berasal dari skema yang sama.
 */
@Entity(tableName = "schema_meta")
data class SchemaMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)