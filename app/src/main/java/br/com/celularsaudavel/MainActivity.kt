package br.com.celularsaudavel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.celularsaudavel.model.HistoryType
import br.com.celularsaudavel.ui.CS
import br.com.celularsaudavel.ui.CelularSaudavelTheme
import br.com.celularsaudavel.ui.MainViewModel
import br.com.celularsaudavel.ui.screens.AppsScreen
import br.com.celularsaudavel.ui.screens.BackupScreen
import br.com.celularsaudavel.ui.screens.CleanScreen
import br.com.celularsaudavel.ui.screens.DuplicatesScreen
import br.com.celularsaudavel.ui.screens.HistoryScreen
import br.com.celularsaudavel.ui.screens.HomeScreen
import br.com.celularsaudavel.ui.screens.LargeVideosScreen
import br.com.celularsaudavel.ui.screens.MoreScreen
import br.com.celularsaudavel.ui.screens.PermissionsScreen
import br.com.celularsaudavel.ui.screens.PrivacyScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CelularSaudavelTheme { AppRoot() }
        }
    }
}

private const val TAB_HOME = 0
private const val TAB_CLEAN = 1
private const val TAB_BACKUP = 2
private const val TAB_APPS = 3
private const val TAB_MORE = 4

private val tabs = listOf(
    "🏠" to "Início",
    "🧹" to "Limpar",
    "☁️" to "Backup",
    "📱" to "Apps",
    "•••" to "Mais",
)

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshBasics() }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshBasics()
        vm.scan()
    }
    val requestMedia: () -> Unit = { permLauncher.launch(mediaPermissions()) }
    val scan: () -> Unit = {
        if (state.mediaAccess == br.com.celularsaudavel.model.MediaAccess.NONE) requestMedia() else vm.scan()
    }
    val openUsage: () -> Unit = { context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    val openAppSettings: () -> Unit = {
        context.safeStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    }
    val go: (Int, String?) -> Unit = { t, s -> tab = t; sub = s }

    LaunchedEffect(tab) {
        if (tab == TAB_APPS && !vm.state.value.appsLoaded && !vm.state.value.appsLoading) vm.loadApps()
    }

    BackHandler(enabled = sub != null || tab != TAB_HOME) {
        if (sub != null) sub = null else tab = TAB_HOME
    }

    val versionName = remember(context) {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    Scaffold(
        containerColor = CS.Bg,
        bottomBar = {
            NavigationBar(containerColor = CS.Surface) {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { go(i, null) },
                        icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = CS.GreenSoft)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                sub == "dups" -> DuplicatesScreen(
                    state, onBack = { sub = null },
                    makeRequest = vm::deleteRequest,
                    onRemoved = { vm.onMediaRemoved(HistoryType.DUPLICATES_REMOVED, it) }
                )
                sub == "videos" -> LargeVideosScreen(
                    state, onBack = { sub = null },
                    makeRequest = vm::trashRequest,
                    onRemoved = { vm.onMediaRemoved(HistoryType.VIDEOS_TRASHED, it) }
                )
                sub == "history" -> HistoryScreen(state, onBack = { sub = null })
                sub == "perms" -> PermissionsScreen(
                    state, onBack = { sub = null },
                    onRequestMedia = requestMedia,
                    onOpenAppSettings = openAppSettings,
                    onOpenUsageSettings = openUsage
                )
                sub == "privacy" -> PrivacyScreen(onBack = { sub = null })

                tab == TAB_HOME -> HomeScreen(
                    state,
                    onScan = scan,
                    onOpenClean = { go(TAB_CLEAN, null) },
                    onOpenDuplicates = { go(TAB_CLEAN, "dups") },
                    onOpenVideos = { go(TAB_CLEAN, "videos") },
                    onOpenApps = { go(TAB_APPS, null) },
                    onOpenBackup = { go(TAB_BACKUP, null) },
                    onOpenPermissions = { go(TAB_MORE, "perms") },
                )
                tab == TAB_CLEAN -> CleanScreen(
                    state,
                    onScan = scan,
                    onOpenDuplicates = { sub = "dups" },
                    onOpenVideos = { sub = "videos" },
                    onOpenApps = { go(TAB_APPS, null) },
                )
                tab == TAB_BACKUP -> BackupScreen(state, onScan = scan)
                tab == TAB_APPS -> AppsScreen(
                    state,
                    onOpenUsageSettings = openUsage,
                    onUninstallFinished = vm::onUninstallFinished
                )
                else -> MoreScreen(
                    state,
                    versionName = versionName,
                    onOpenHistory = { sub = "history" },
                    onOpenPermissions = { sub = "perms" },
                    onOpenPrivacy = { sub = "privacy" },
                )
            }
        }
    }
}
