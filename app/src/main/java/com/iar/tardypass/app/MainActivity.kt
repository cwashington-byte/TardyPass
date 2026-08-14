package com.iar.tardypass.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iar.tardypass.ui.home.HomeScreen
import com.iar.tardypass.ui.home.HomeViewModel
import com.iar.tardypass.ui.import_.ImportScreen
import com.iar.tardypass.ui.import_.ImportViewModel
import com.iar.tardypass.ui.logs.LogsScreen
import com.iar.tardypass.ui.logs.LogsViewModel
import com.iar.tardypass.ui.settings.SettingsScreen
import com.iar.tardypass.ui.settings.SettingsViewModel
import com.iar.tardypass.ui.theme.TardyPassTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as TardyPassApp).container

    private val vmFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            HomeViewModel::class.java ->
                HomeViewModel(container.students, container.passes, container.settings, container.printer) as T
            LogsViewModel::class.java -> LogsViewModel(container.passes) as T
            SettingsViewModel::class.java -> SettingsViewModel(container.settings, container.printer) as T
            ImportViewModel::class.java -> ImportViewModel(container.students) as T
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }

    // CSV text arriving via ACTION_SEND before the UI was ready
    private var pendingSharedCsv: String? = null
    private var importVm: ImportViewModel? = null
    private var navigateToImport: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)

        setContent {
            TardyPassTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
        deliverPendingCsv()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        MainScope().launch {
            val text = withContext(Dispatchers.IO) {
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                when {
                    uri != null -> contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    else -> intent.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
            if (!text.isNullOrBlank()) {
                pendingSharedCsv = text
                deliverPendingCsv()
            }
        }
    }

    private fun deliverPendingCsv() {
        val csv = pendingSharedCsv ?: return
        val vm = importVm ?: return
        pendingSharedCsv = null
        navigateToImport?.invoke()
        vm.preview(csv)
    }

    @Composable
    private fun AppNav() {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        val tabs = listOf(
            Triple("home", "Pass", Icons.Filled.Print),
            Triple("logs", "Logs", Icons.AutoMirrored.Filled.List),
            Triple("import", "Import", Icons.Filled.Upload),
            Triple("settings", "Settings", Icons.Filled.Settings)
        )

        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") {
                    HomeScreen(viewModel(factory = vmFactory))
                }
                composable("logs") {
                    LogsScreen(viewModel(factory = vmFactory))
                }
                composable("import") {
                    val vm: ImportViewModel = viewModel(factory = vmFactory)
                    importVm = vm
                    ImportScreen(vm)
                }
                composable("settings") {
                    SettingsScreen(viewModel(factory = vmFactory))
                }
            }

            // Wire share-intent delivery once nav exists
            navigateToImport = {
                navController.navigate("import") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                }
            }
            // A share that launched the app cold is delivered once the import VM exists.
            if (pendingSharedCsv != null && importVm == null) {
                navigateToImport?.invoke()
            } else {
                deliverPendingCsv()
            }
        }
    }
}
