package com.thejaustin.pearity

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thejaustin.pearity.ui.screens.HomeScreen
import com.thejaustin.pearity.ui.screens.SettingsScreen
import com.thejaustin.pearity.ui.theme.PearityTheme
import com.thejaustin.pearity.viewmodel.MainViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(this)

        setContent {
            PearityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PearityApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this)
    }

    /** Called after user responds to the Shizuku permission dialog */
    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        // Same ViewModelStore as viewModel() in the composables, so this refreshes
        // the banner and per-setting supported flags immediately after the grant.
        ViewModelProvider(this)[MainViewModel::class.java].refreshShizuku()
    }
}

// ── In-process navigation (no NavController overhead for 2 screens) ───────────

@Composable
private fun PearityApp() {
    val vm: MainViewModel = viewModel()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // With enableOnBackInvokedCallback + manual (non-NavController) navigation, system back
    // would otherwise fall through to the default handler and finish the Activity from Settings.
    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(
            viewModel = vm,
            onBack    = { showSettings = false },
        )
    } else {
        HomeScreen(
            viewModel          = vm,
            onNavigateToSettings = { showSettings = true },
        )
    }
}
