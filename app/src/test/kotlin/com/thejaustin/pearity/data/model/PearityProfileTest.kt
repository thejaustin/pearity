package com.thejaustin.pearity.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PearityProfileTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun `profile serialization and deserialization preserves all fields`() {
        val original = PearityProfile(
            version = 1,
            exportedAt = 123456789L,
            settings = mapOf(
                "window_animation_scale" to ProfileSettingEntry(state = SettingState.IOS, customValue = "1.0"),
                "touch_sounds" to ProfileSettingEntry(state = SettingState.ANDROID_DEFAULT, customValue = "1"),
                "font_scale" to ProfileSettingEntry(state = SettingState.CUSTOM, customValue = "1.15"),
            )
        )

        val encoded = json.encodeToString(PearityProfile.serializer(), original)
        assertNotNull(encoded)

        val decoded = json.decodeFromString(PearityProfile.serializer(), encoded)
        assertEquals(original.version, decoded.version)
        assertEquals(original.exportedAt, decoded.exportedAt)
        assertEquals(3, decoded.settings.size)
        assertEquals(SettingState.IOS, decoded.settings["window_animation_scale"]?.state)
        assertEquals("1.0", decoded.settings["window_animation_scale"]?.customValue)
        assertEquals(SettingState.CUSTOM, decoded.settings["font_scale"]?.state)
        assertEquals("1.15", decoded.settings["font_scale"]?.customValue)
    }
}
