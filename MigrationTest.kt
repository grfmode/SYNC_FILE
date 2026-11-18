package com.sync.app.domain.chat

import com.sync.app.data.local.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {

    @Test
    fun `versi target sepuluh`() {
        assertEquals(10, Migrations.CURRENT_VERSION)
    }

    @Test
    fun `migrasi pertama tersedia`() {
        assertTrue(Migrations.MIGRATION_1_2.startVersion == 1)
    }

    @Test
    fun `rantai migrasi berurutan tanpa lompatan`() {
        val chain = listOf(
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
            Migrations.MIGRATION_3_4,
            Migrations.MIGRATION_4_5,
            Migrations.MIGRATION_5_6,
            Migrations.MIGRATION_6_7,
            Migrations.MIGRATION_7_8,
            Migrations.MIGRATION_8_9,
            Migrations.MIGRATION_9_10
        )
        chain.forEachIndexed { index, migration ->
            assertEquals(index + 1, migration.startVersion)
            assertEquals(index + 2, migration.endVersion)
        }
    }
}