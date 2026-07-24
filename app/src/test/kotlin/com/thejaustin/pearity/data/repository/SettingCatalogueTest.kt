package com.thejaustin.pearity.data.repository

import com.thejaustin.pearity.data.model.SettingAccessor
import com.thejaustin.pearity.data.model.SettingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural checks on the hardcoded catalogue. These can't verify a key is the *real* Android
 * settings key (that needs a device) but they do catch the class of mistake a plain typo or
 * copy-paste error introduces — duplicate ids, blank fields, malformed values — the same shape
 * of bug a 2026-07-24 audit found by hand across the whole file.
 */
class SettingCatalogueTest {

    private val all = SettingCatalogue.all

    @Test
    fun `catalogue is not empty`() {
        assertTrue(all.isNotEmpty())
    }

    @Test
    fun `all ids are unique`() {
        val duplicates = all.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("Duplicate ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `ids are snake_case, matching the DataStore key convention`() {
        val pattern = Regex("^[a-z][a-z0-9_]*$")
        all.forEach { s -> assertTrue("${s.id}: not snake_case", pattern.matches(s.id)) }
    }

    @Test
    fun `no blank id, title, subtitle, or default value`() {
        all.forEach { s ->
            assertTrue("${s.id}: blank title", s.title.isNotBlank())
            assertTrue("${s.id}: blank subtitle", s.subtitle.isNotBlank())
            assertTrue("${s.id}: blank androidDefaultValue", s.androidDefaultValue.isNotBlank())
            assertTrue("${s.id}: blank iosDefaultValue", s.iosDefaultValue.isNotBlank())
        }
    }

    @Test
    fun `accessor keys are non-blank with no shell metacharacters`() {
        // Defense-in-depth mirror of SettingsRepository.SAFE_SHELL_VALUE: catalogue keys get
        // interpolated into `settings put ...` the same way applied values do.
        val unsafe = Regex("[;|&`$'\"\\\\\n]")
        fun check(id: String, key: String) {
            assertTrue("$id: blank accessor key", key.isNotBlank())
            assertFalse("$id: unsafe accessor key '$key'", unsafe.containsMatchIn(key))
        }
        all.forEach { s ->
            when (val acc = s.accessor) {
                is SettingAccessor.SystemSetting -> check(s.id, acc.key)
                is SettingAccessor.SecureSetting -> check(s.id, acc.key)
                is SettingAccessor.GlobalSetting -> check(s.id, acc.key)
                is SettingAccessor.ShellCommand -> {
                    assertTrue("${s.id}: blank readCmd", acc.readCmd.isNotBlank())
                    assertTrue("${s.id}: blank writeCmd", acc.writeCmd.isNotBlank())
                    assertTrue(
                        "${s.id}: writeCmd has no {value} placeholder",
                        acc.writeCmd.contains("{value}"),
                    )
                }
            }
        }
    }

    @Test
    fun `values with a scale, Hz, or ms unit are numeric`() {
        all.filter { it.unit in setOf("×", "Hz", "ms") }.forEach { s ->
            assertNotNull(
                "${s.id}: androidDefaultValue '${s.androidDefaultValue}' isn't numeric",
                s.androidDefaultValue.toDoubleOrNull(),
            )
            assertNotNull(
                "${s.id}: iosDefaultValue '${s.iosDefaultValue}' isn't numeric",
                s.iosDefaultValue.toDoubleOrNull(),
            )
        }
    }

    @Test
    fun `unit-less on-off-shaped values are integers, not stray text`() {
        // Most unit-less entries are boolean toggles ("0"/"1"); a few (vibration intensity,
        // location_mode) are small enum ints. Either way they must parse as Int, catching a
        // typo like "true"/"on" slipping into a field the UI treats as a raw settings value.
        all.filter { it.unit.isEmpty() && it.accessor !is SettingAccessor.ShellCommand }
            .forEach { s ->
                assertNotNull(
                    "${s.id}: androidDefaultValue '${s.androidDefaultValue}' isn't an int",
                    s.androidDefaultValue.toIntOrNull(),
                )
                assertNotNull(
                    "${s.id}: iosDefaultValue '${s.iosDefaultValue}' isn't an int",
                    s.iosDefaultValue.toIntOrNull(),
                )
            }
    }

    @Test
    fun `every category is used by at least one setting`() {
        val unused = SettingCategory.values().toSet() - all.map { it.category }.toSet()
        assertTrue("Unused categories: $unused", unused.isEmpty())
    }

    @Test
    fun `catalogue size matches the README's claimed count`() {
        // README.md says "54 settings" — this is the tripwire if either drifts.
        assertEquals(54, all.size)
    }
}
