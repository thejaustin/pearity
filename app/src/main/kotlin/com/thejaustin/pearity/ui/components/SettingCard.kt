package com.thejaustin.pearity.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.thejaustin.pearity.data.model.SettingState
import com.thejaustin.pearity.viewmodel.SettingUiState

@Composable
fun SettingCard(
    state: SettingUiState,
    onStateChange: (SettingState) -> Unit,
    onSaveAsCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M3 Expressive: spring-physics card elevation animation
    val elevation by animateDpAsState(
        targetValue = if (state.isApplying) 6.dp else 1.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_elevation",
    )

    Card(
        modifier  = modifier
            .fillMaxWidth()
            .alpha(if (state.supported) 1f else 0.5f),
        shape     = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = state.setting.title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = state.setting.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Shizuku badge
                if (state.setting.requiresShizuku) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            if (state.supported) "Privileged" else "Locked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            // ── Value comparison chips ────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ValueChip(
                    label    = "Android",
                    value    = state.setting.androidDefaultValue + state.setting.unit,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                ValueChip(
                    label    = "iOS",
                    value    = state.setting.iosDefaultValue + state.setting.unit,
                    modifier = Modifier.weight(1f),
                )
                
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = (state.currentValue ?: "—") + state.setting.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Progress / toggle ─────────────────────────────────────────────
            if (state.isApplying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                TriStateToggle(
                    state         = state.state,
                    onStateChange = onStateChange,
                    enabled       = state.supported,
                    modifier      = Modifier.height(32.dp) // Compact toggle
                )
            }

            // ── Error / Info message ──────────────────────────────────────────
            AnimatedVisibility(
                visible = state.error != null || !state.supported,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                val message = when {
                    state.error != null -> state.error
                    !state.supported -> "This setting requires Root or Shizuku permission to modify."
                    else -> null
                }
                message?.let {
                    Text(
                        text  = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ── Save current as custom ────────────────────────────────────────
            AnimatedVisibility(
                visible = state.supported &&
                          state.state != SettingState.CUSTOM &&
                          state.currentValue != null &&
                          state.currentValue != state.customValue,
            ) {
                TextButton(
                    onClick      = onSaveAsCustom,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Save current as Custom", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ValueChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier  = modifier,
        shape     = MaterialTheme.shapes.medium,
        color     = containerColor,
    ) {
        Column(
            modifier              = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
            Text(
                text      = value,
                style     = MaterialTheme.typography.labelMedium,
                color     = contentColor,
                fontWeight = FontWeight.Medium,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
        }
    }
}
