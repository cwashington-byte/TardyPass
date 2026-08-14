package com.iar.tardypass.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iar.tardypass.ui.auth.AuthViewModel
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as TardyPassApp).container

    private val vmFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(
                container.students, container.passes, container.settings,
                container.printer, container.auth, container.sync
            ) as T
            LogsViewModel::class.java -> LogsViewModel(container.passes, container.sync) as T
            SettingsViewModel::class.java -> SettingsViewModel(container.settings, container.printer) as T
            ImportViewModel::class.java ->
                ImportViewModel(container.students, container.pendingSharedCsv) as T
            AuthViewModel::class.java -> AuthViewModel(container.auth, container.sync) as T
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        setContent { TardyPassTheme { AppNav() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** Reads shared CSV (file stream or plain text) into the app-scoped holder. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                runCatching {
                    if (uri != null) contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    else intent.getStringExtra(Intent.EXTRA_TEXT)
                }.getOrNull()
            }
            if (!text.isNullOrBlank()) container.pendingSharedCsv.value = text
        }
    }

    @Composable
    private fun AppNav() {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val pendingCsv by container.pendingSharedCsv.collectAsState()

        // A shared CSV always lands on the import screen, whether the share
        // cold-started the app or arrived while it was already open.
        LaunchedEffect(pendingCsv) {
            if (pendingCsv != null && currentRoute != "import") {
                navController.navigate("import") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                }
            }
        }

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
                composable("home") { HomeScreen(viewModel(factory = vmFactory)) }
                composable("logs") { LogsScreen(viewModel(factory = vmFactory)) }
                composable("import") { ImportScreen(viewModel(factory = vmFactory)) }
                composable("settings") {
                    SettingsScreen(
                        vm = viewModel(factory = vmFactory),
                        authVm = viewModel(factory = vmFactory)
                    )
                }
            }
        }
    }
}
