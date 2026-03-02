package com.thejaustin.pearity.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
    var searchActive by remember { mutableStateOf(false) }
    var navRailVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refreshShizuku() }

    // Build category list with icons
    val categories = remember {
        listOf(
            "Animations" to Icons.Outlined.SlowMotionVideo,
            "Text & Font" to Icons.Outlined.Title,
            "Sound" to Icons.Outlined.VolumeUp,
            "Haptics" to Icons.Outlined.Vibration,
            "Display" to Icons.Outlined.BrightnessHigh,
            "Navigation" to Icons.Outlined.Swipe,
            "Accessibility" to Icons.Outlined.Accessibility,
            "Keyboard" to Icons.Outlined.Keyboard,
            "Lock Screen" to Icons.Outlined.Lock,
            "Samsung One UI" to Icons.Outlined.PhoneAndroid,
            "System" to Icons.Outlined.Tune,
        )
    }

    val visibleCategories = categories.filter { ui.settingsByCategory.containsKey(it.first) }

    fun scrollToCategory(categoryName: String) {
        // Calculate the target index based on list structure
        var targetIndex = 0
        // Count banners that appear before categories
        if (ui.smartSwitchBackupFound) targetIndex++
        if (!ui.shizukuAvailable || !ui.shizukuPermission) targetIndex++
        if (!ui.writeSettingsGranted) targetIndex++
        
        // Find the category index
        val categoryIndex = visibleCategories.indexOfFirst { it.first == categoryName }
        if (categoryIndex != -1) {
            targetIndex += categoryIndex
            listState.animateScrollToItem(targetIndex)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Navigation Rail (Adaptive) ────────────────────────────────────────
        AnimatedVisibility(
            visible = navRailVisible,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Spacer(Modifier.height(12.dp))
                visibleCategories.forEach { (name, icon) ->
                    NavigationRailItem(
                        selected = false,
                        onClick = { scrollToCategory(name) },
                        icon = { Icon(icon, contentDescription = name) },
                        label = { Text(name) }
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier
                .weight(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .widthIn(min = 400.dp),
            topBar   = {
                Column {
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

                    // ── Global Search Bar (M3 Expressive) ────────────────────────
                    SearchBar(
                        query = ui.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        onSearch = { searchActive = false },
                        active = searchActive,
                        onActiveChange = { searchActive = it },
                        placeholder = { Text("Search settings...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (searchActive) 0.dp else 16.dp)
                            .padding(bottom = if (searchActive) 0.dp else 8.dp)
                    ) {
                        // Results are shown in the main list below
                    }
                }
            },
        ) { padding ->

        Box(modifier = Modifier.fillMaxSize()) {
            if (ui.isLoading) {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            LazyColumn(
                state               = listState,
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                // ── Smart Switch Import banner ────────────────────────────────────
                if (ui.smartSwitchBackupFound) {
                    item(key = "smart_switch_banner") {
                        SmartSwitchBanner(
                            deviceInfo = ui.smartSwitchDeviceInfo,
                            appCount   = ui.smartSwitchApps.size,
                            onImport   = viewModel::importSmartSwitchData,
                        )
                    }
                }

                // ── Shizuku / connection banner ───────────────────────────────────
                if (!ui.shizukuAvailable || !ui.shizukuPermission) {
                    item(key = "shizuku_banner") {
                        ShizukuBanner(
                            available     = ui.shizukuAvailable,
                            hasPermission = ui.shizukuPermission,
                            onGrant       = viewModel::requestShizukuPermission,
                            onRefresh     = viewModel::refreshShizuku,
                        )
                    }
                }

                // ── WRITE_SETTINGS banner ─────────────────────────────────────────
                if (!ui.writeSettingsGranted) {
                    item(key = "write_settings_banner") {
                        // LocalContext must be inside a Composable (item) scope
                        val activity = LocalContext.current as Activity
                        WriteSettingsBanner(
                            onGrant   = { viewModel.requestWriteSettingsPermission(activity) },
                            onRefresh = viewModel::refreshShizuku,
                        )
                    }
                }

                // ── Settings grouped by category ──────────────────────────────────
                ui.settingsByCategory.forEach { (categoryName, settings) ->

                    item(key = "header_$categoryName") {
                        var expanded by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                settings.forEach { settingState ->
                                    SettingCard(
                                        state = settingState,
                                        onStateChange = { newState ->
                                            viewModel.applyState(settingState.setting.id, newState)
                                        },
                                        onSaveAsCustom = {
                                            viewModel.saveCurrentAsCustom(settingState.setting.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }

                // ── Empty state for search ────────────────────────────────────────
                if (ui.settingsByCategory.isEmpty() && ui.searchQuery.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching settings found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Bottom-left hamburger toggle for navigation rail ────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                FloatingActionButton(
                    onClick = { navRailVisible = !navRailVisible },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = if (navRailVisible) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.Menu,
                        contentDescription = if (navRailVisible) "Hide navigation" else "Show navigation",
                    )
                }
            }
        }
    }
}
}

// ── Banners ───────────────────────────────────────────────────────────────────

@Composable
private fun SmartSwitchBanner(
    deviceInfo: com.thejaustin.pearity.utils.SmartSwitchDeviceInfo?,
    appCount: Int,
    onImport: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape  = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "iPhone Backup Found",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text  = "Detected ${deviceInfo?.model ?: "iPhone"} with $appCount apps transferred. Import your setup?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onImport,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor   = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Match My Previous Setup")
            }
        }
    }
}

@Composable
private fun ShizukuBanner(
    available: Boolean,
    hasPermission: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
) {
    val (title, body) = when {
        !available     -> "Shizuku not running" to
                          "Open the Shizuku app and start the service, then tap Refresh."
        !hasPermission -> "Permission needed" to
                          "Pearity needs the Shizuku permission to write privileged settings."
        else           -> return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape  = MaterialTheme.shapes.extraLarge,
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
                    text       = title,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = MaterialTheme.colorScheme.onErrorContainer,
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

@Composable
private fun WriteSettingsBanner(
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape  = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Modify System Settings permission missing",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pearity needs this to change fonts, sounds, and other system-level values without Shizuku.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrant,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Grant Permission") }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}
