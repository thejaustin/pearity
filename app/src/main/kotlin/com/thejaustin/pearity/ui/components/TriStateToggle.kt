package com.thejaustin.pearity.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.thejaustin.pearity.data.model.SettingState

/**
 * Three-position segmented control that represents the three setting states:
 *
 *  [Android default]  ←→  [Custom (yours)]  ←→  [iOS parity]
 *
 * Uses M3 SingleChoiceSegmentedButtonRow with spring-physics selection
 * per Material 3 Expressive motion guidelines.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TriStateToggle(
    state: SettingState,
    onStateChange: (SettingState) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val options = listOf(
        SettingState.ANDROID_DEFAULT to "Android",
        SettingState.CUSTOM          to "Custom",
        SettingState.IOS             to "iOS",
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (optionState, label) ->
            SegmentedButton(
                selected = state == optionState,
                onClick  = { if (enabled) onStateChange(optionState) },
                shape    = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled  = enabled,
                label    = {
                    Text(
                        text       = label,
                        fontWeight = if (state == optionState) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                icon = { SegmentedButtonDefaults.ActiveIcon() },
            )
        }
    }
}
