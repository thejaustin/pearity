package com.thejaustin.pearity.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thejaustin.pearity.data.model.SettingState
import com.thejaustin.pearity.ui.components.SettingCard
import com.thejaustin.pearity.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Refresh Shizuku state each time the screen is visible
    LaunchedEffect(Unit) { viewModel.refreshShizuku() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Pearity",
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text  = "iOS parity for One UI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->

        if (ui.isLoading) {
            Box(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Shizuku / connection banner ───────────────────────────────────
            if (!ui.shizukuAvailable || !ui.shizukuPermission) {
                item(key = "shizuku_banner") {
                    ShizukuBanner(
                        available      = ui.shizukuAvailable,
                        hasPermission  = ui.shizukuPermission,
                        onGrant        = viewModel::requestShizukuPermission,
                        onRefresh      = viewModel::refreshShizuku,
                    )
                }
            }

            if (!ui.writeSettingsGranted) {
                val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
                item(key = "write_settings_banner") {
                    WriteSettingsBanner(
                        onGrant   = { viewModel.requestWriteSettingsPermission(activity) },
                        onRefresh = viewModel::refreshShizuku,
                    )
                }
            }

            // ── Settings grouped by category ──────────────────────────────────
            ui.settingsByCategory.forEach { (categoryName, settings) ->

                item(key = "header_$categoryName") {
                    Text(
                        text       = categoryName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    
                    @Composable
                    private fun WriteSettingsBanner(
                        onGrant: () -> Unit,
                        onRefresh: () -> Unit,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Modify System Settings permission missing",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Pearity needs this to change fonts, sounds, and other system-level values without Shizuku.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onGrant,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text("Grant Permission")
                                    }
                                    TextButton(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Refresh")
                                    }
                                }
                            }
                        }
                    }
                                    items(settings, key = { it.setting.id }) { settingState ->
                    SettingCard(
                        state         = settingState,
                        onStateChange = { newState ->
                            viewModel.applyState(settingState.setting.id, newState)
                        },
                        onSaveAsCustom = {
                            viewModel.saveCurrentAsCustom(settingState.setting.id)
                        },
                    )
                }
            }

            // Bottom padding so last card clears nav bar
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Shizuku status banner ─────────────────────────────────────────────────────

@Composable
private fun ShizukuBanner(
    available: Boolean,
    hasPermission: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
) {
    val (title, body) = when {
        !available    -> "Shizuku not running" to
                         "Open the Shizuku app and start the service, then tap Refresh."
        !hasPermission -> "Permission needed" to
                         "Pearity needs the Shizuku permission to write privileged settings."
        else          -> return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Warning,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text      = title,
                    style     = MaterialTheme.typography.titleSmall,
                    color     = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (!available) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
            } else {
                TextButton(onClick = onGrant)   { Text("Grant") }
            }
        }
    }
}
