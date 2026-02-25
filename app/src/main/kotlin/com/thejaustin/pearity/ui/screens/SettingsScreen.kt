package com.thejaustin.pearity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thejaustin.pearity.viewmodel.ConnectionMode
import com.thejaustin.pearity.viewmodel.MainViewModel
import com.thejaustin.pearity.utils.CrashHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        crashLog = CrashHandler.getCrashLog(context)
    }

    if (showCrashDialog && crashLog != null) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("Latest Crash Report") },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Text(
                        text = crashLog!!,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    CrashHandler.clearCrashLog(context)
                    crashLog = null
                    showCrashDialog = false
                }) {
                    Text("Clear Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title         = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Connection mode ───────────────────────────────────────────────
            item {
                Text(
                    "Connection Mode",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ConnectionModeCard(
                    current  = ui.connectionMode,
                    onChange = viewModel::setConnectionMode,
                )
            }

            // ── Shizuku status ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Shizuku Status",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ShizukuStatusCard(
                    available     = ui.shizukuAvailable,
                    hasPermission = ui.shizukuPermission,
                    onRefresh     = viewModel::refreshShizuku,
                    onGrant       = viewModel::requestShizukuPermission,
                )
            }

            // ── Diagnostics ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Diagnostics",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    ListItem(
                        headlineContent = { Text("Crash Logs", style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = { Text(if (crashLog != null) "Recent crash detected" else "No recent crashes", style = MaterialTheme.typography.labelSmall) },
                        trailingContent = {
                            if (crashLog != null) {
                                IconButton(onClick = { showCrashDialog = true }) {
                                    Icon(Icons.Outlined.Info, contentDescription = "View Log")
                                }
                            }
                        }
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "About",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pearity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Bring iOS-default settings to your Samsung device. " +
                            "Each toggle has three states: the Android default, your own custom value, " +
                            "and the iOS default. Swipe freely between them at any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "github.com/thejaustin/pearity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// ── Connection mode card ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionModeCard(
    current: ConnectionMode,
    onChange: (ConnectionMode) -> Unit,
) {
    val options = listOf(
        ConnectionMode.AUTO     to ("Auto"    to Icons.Outlined.AutoAwesome),
        ConnectionMode.ROOT     to ("Root"    to Icons.Outlined.AdminPanelSettings),
        ConnectionMode.SHIZUKU  to ("Shizuku" to Icons.Outlined.Hub),
        ConnectionMode.ADB_RISH to ("ADB"     to Icons.Outlined.Cable),
    )

    Card(shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text(
                "How Pearity applies privileged settings:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (mode, data) ->
                    val (label, icon) = data
                    SegmentedButton(
                        selected = current == mode,
                        onClick  = { onChange(mode) },
                        shape    = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon     = { Icon(icon, null, Modifier.size(16.dp)) },
                        label    = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            val modeDesc = when (current) {
                ConnectionMode.AUTO ->
                    "Automatically chooses the best available method (Root > Shizuku > ADB)."
                ConnectionMode.ROOT ->
                    "Uses 'su' for root access. Fast and doesn't require Shizuku."
                ConnectionMode.SHIZUKU  ->
                    "Uses the Shizuku API. Shizuku must be running and permission granted."
                ConnectionMode.ADB_RISH ->
                    "Uses the rish ADB shell. Useful when Shizuku API fails."
            }
            Text(modeDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Shizuku status card ───────────────────────────────────────────────────────

@Composable
private fun ShizukuStatusCard(
    available: Boolean,
    hasPermission: Boolean,
    onRefresh: () -> Unit,
    onGrant: () -> Unit,
) {
    Card(shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRow("Shizuku running",    available)
            StatusRow("Permission granted", hasPermission)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refresh") }
                if (available && !hasPermission) {
                    Button(onClick = onGrant, modifier = Modifier.weight(1f)) { Text("Grant") }
                }
            }
        }
    }
}

@Composable
private fun RootStatusCard(
    available: Boolean,
    onRefresh: () -> Unit,
) {
    Card(shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRow("Root (su) available", available)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refresh") }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = if (ok) "✓" else "✗",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
