package com.thejaustin.pearity.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileSettingEntry(
    val state: SettingState,
    val customValue: String? = null,
)

@Serializable
data class PearityProfile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: Map<String, ProfileSettingEntry> = emptyMap(),
)
