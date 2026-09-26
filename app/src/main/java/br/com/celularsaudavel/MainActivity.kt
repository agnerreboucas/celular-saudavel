package br.com.celularsaudavel

import android.Manifest
import android.app.Activity
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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import br.com.celularsaudavel.data.BackupRow
import br.com.celularsaudavel.data.DriveRepository
import br.com.celularsaudavel.ui.screens.FolderCategoryScreen
import br.com.celularsaudavel.ui.screens.FoldersScreen
import com.google.android.gms.auth.api.identity.Identity
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
import br.com.celularsaudavel.model.InstalledApp
import br.com.celularsaudavel.ui.screens.SORT_CACHE
import br.com.celularsaudavel.ui.screens.SORT_SIZE
import br.com.celularsaudavel.ui.screens.SORT_UNUSED
import br.com.celularsaudavel.ui.CS
import br.com.celularsaudavel.ui.CelularSaudavelTheme
import br.com.celularsaudavel.ui.DeletingDialog
import br.com.celularsaudavel.ui.WinDialog
import br.com.celularsaudavel.ui.MainViewModel
import br.com.celularsaudavel.ui.screens.AppsScreen
import br.com.celularsaudavel.ui.screens.BackupScreen
import br.com.celularsaudavel.ui.screens.CleanScreen
import br.com.celularsaudavel.ui.screens.DuplicatesScreen
import br.com.celularsaudavel.ui.screens.HistoryScreen
import br.com.celularsaudavel.ui.screens.HomeScreen
import br.com.celularsaudavel.ui.screens.LargeVideosScreen
import br.com.celularsaudavel.ui.screens.MonitorScreen
import br.com.celularsaudavel.ui.screens.PremiumScreen
import br.com.celularsaudavel.ui.screens.RecoverScreen
import br.com.celularsaudavel.ui.screens.MoreScreen
import br.com.celularsaudavel.ui.screens.PermissionsScreen
import br.com.celularsaudavel.ui.screens.PrivacyScreen
import br.com.celularsaudavel.ui.screens.TrashScreen

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
    var appsSort by rememberSaveable { mutableIntStateOf(SORT_SIZE) }
    val openApps: (Int) -> Unit = { sort -> appsSort = sort; go(TAB_APPS, null) }

    var pendingUninstall by remember { mutableStateOf<InstalledApp?>(null) }
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingUninstall?.let(vm::onUninstallFinished)
        pendingUninstall = null
    }
    val openAppDetails: (InstalledApp) -> Unit = { app ->
        context.safeStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
        )
    }
    val uninstall: (InstalledApp) -> Unit = { app ->
        try {
            pendingUninstall = app
            uninstallLauncher.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        } catch (_: Exception) {
            pendingUninstall = null
            openAppDetails(app)
        }
    }

    // ---------- Pastas (acesso a todos os arquivos) ----------
    val legacyFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshBasics()
        vm.loadFolders(withBiggest = true)
    }
    val requestFolders: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            } catch (_: Exception) {
                context.safeStart(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyFilesLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }

    // ---------- Google Drive ----------
    fun driveError(e: Exception?): String =
        "O Google não autorizou a conexão${e?.message?.let { " ($it)" } ?: ""}. Confira no Google Cloud: API do Drive ativada, " +
            "cliente Android com o pacote br.com.celularsaudavel e o SHA-1 certo, e sua conta como usuário de teste."
    val driveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            try {
                val res = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
                vm.onDriveAuthorized(res.accessToken)
            } catch (e: Exception) {
                vm.onDriveError(driveError(e))
            }
        } else {
            vm.onDriveError("A conexão foi cancelada.")
        }
    }
    val connectDrive: () -> Unit = {
        vm.onDriveConnecting()
        try {
            Identity.getAuthorizationClient(context).authorize(DriveRepository.authRequest())
                .addOnSuccessListener { res ->
                    if (res.hasResolution()) {
                        val pi = res.pendingIntent
                        if (pi != null) driveLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        else vm.onDriveError(driveError(null))
                    } else {
                        vm.onDriveAuthorized(res.accessToken)
                    }
                }
                .addOnFailureListener { e -> vm.onDriveError(driveError(e)) }
        } catch (e: Exception) {
            vm.onDriveError(driveError(e))
        }
    }
    var pendingStart by remember { mutableStateOf<Pair<Set<String>, Boolean>?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStart?.let { (cats, wifi) -> vm.startBackup(cats, wifi) }
        pendingStart = null
    }
    val startBackup: (Set<String>, Boolean) -> Unit = { cats, wifi ->
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = cats to wifi
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startBackup(cats, wifi)
        }
    }
    var pendingFree by remember { mutableStateOf<List<BackupRow>>(emptyList()) }
    val freeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.onFreed(pendingFree)
        pendingFree = emptyList()
    }
    val freeSpace: () -> Unit = {
        val (sender, rows) = vm.freeRequest()
        if (sender != null) {
            pendingFree = rows
            freeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            vm.onDriveError("Não consegui abrir a confirmação do Android (é preciso Android 11 ou mais novo).")
        }
    }

    val monitorNotifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openAppSettings()
        vm.refreshMonitor()
    }

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

    // Falha anterior: mostra e deixa enviar os detalhes
    var crashText by remember {
        mutableStateOf(
            try {
                val f = CelularSaudavelApp.crashFile(context.applicationContext as android.app.Application)
                if (f.exists()) f.readText() else null
            } catch (_: Exception) {
                null
            }
        )
    }
    crashText?.let { text ->
        val clear = {
            try {
                CelularSaudavelApp.crashFile(context.applicationContext as android.app.Application).delete()
            } catch (_: Exception) {
            }
            crashText = null
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clear() },
            title = { Text("O app fechou por um erro") },
            text = {
                Text(
                    "Da última vez o Celular Saudável fechou sozinho. Envie os detalhes para corrigirmos: " +
                        "eles contêm só o modelo do celular, a versão do Android e onde o erro aconteceu."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    context.safeStart(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Falha no Celular Saudável")
                                .putExtra(Intent.EXTRA_TEXT, text),
                            "Enviar detalhes do erro"
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    clear()
                }) { Text("Enviar detalhes") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { clear() }) { Text("Agora não") } }
        )
    }

    var preview by remember { mutableStateOf<br.com.celularsaudavel.ui.PreviewTarget?>(null) }
    preview?.let { t -> br.com.celularsaudavel.ui.PreviewDialog(t, onClose = { preview = null }) }
    val openPreview: (br.com.celularsaudavel.ui.PreviewTarget) -> Unit = { preview = it }

    state.deleting?.let { (done, total) -> DeletingDialog(done, total) }
    state.win?.let { w ->
        WinDialog(
            bytes = w.bytes, count = w.count, what = w.what, toTrash = w.toTrash,
            sessionSaved = maxOf(
                (state.sessionStartUsed ?: 0L) - (state.storage?.usedBytes ?: 0L),
                state.sessionFreed
            ),
            avgPhotoBytes = state.avgPhotoBytes,
            onOpenTrash = { go(TAB_CLEAN, "trash") },
            onDismiss = vm::dismissWin
        )
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
                sub == "seq" -> DuplicatesScreen(
                    state, onBack = { sub = null },
                    makeRequest = vm::trashRequest,
                    onRemoved = { vm.onMediaRemoved(HistoryType.SEQUENCE_TRASHED, it) },
                    groups = state.sequences,
                    title = "Fotos em sequência",
                    subtitle = "Fotos da câmera tiradas com poucos segundos de diferença. Toque nas que não quer; fique com a melhor de cada grupo.",
                    preselect = false,
                    toTrash = true
                )
                sub == "shots" -> LargeVideosScreen(
                    state, onBack = { sub = null },
                    makeRequest = vm::trashRequest,
                    onRemoved = { vm.onMediaRemoved(HistoryType.SCREENSHOTS_TRASHED, it) },
                    videos = state.screenshots,
                    title = "Capturas de tela",
                    subtitle = "Prints guardados, dos mais novos para os mais antigos.",
                    noun = "prints",
                    onPreview = openPreview
                )
                sub == "videos" -> LargeVideosScreen(
                    state, onBack = { sub = null },
                    makeRequest = vm::trashRequest,
                    onRemoved = { vm.onMediaRemoved(HistoryType.VIDEOS_TRASHED, it) },
                    onPreview = openPreview
                )
                sub == "trash" -> TrashScreen(
                    state, onBack = { sub = null },
                    onLoad = vm::loadTrash,
                    deleteRequest = vm::deleteRequest,
                    restoreRequest = vm::restoreRequest,
                    onDeleted = { vm.onMediaRemoved(HistoryType.TRASH_DELETED, it) },
                    onRestored = vm::onTrashRestored,
                    onPreview = openPreview
                )
                sub == "folders" -> FoldersScreen(
                    state, onBack = { sub = null },
                    onLoad = { vm.loadFolders(withBiggest = true) },
                    onOpenCategory = { id -> sub = "folder:$id" },
                    onRequestFolders = requestFolders
                )
                sub?.startsWith("folder:") == true -> FolderCategoryScreen(
                    state, categoryId = sub!!.removePrefix("folder:"),
                    onBack = { sub = "folders" },
                    onDelete = { files, mode -> vm.deleteFolderFiles(files, mode) },
                    onPreview = openPreview,
                    onOpenRecover = { sub = "recover" }
                )
                sub == "recover" -> RecoverScreen(
                    state, onBack = { sub = null },
                    onLoad = vm::loadRecycle,
                    onRestore = vm::restoreRecycle,
                    onDeleteForever = vm::deleteRecycleForever,
                    onSetRetention = vm::setRetention,
                    onOpenSystemTrash = { sub = "trash" },
                    onPreview = openPreview
                )
                sub == "history" -> HistoryScreen(state, onBack = { sub = null })
                sub == "perms" -> PermissionsScreen(
                    state, onBack = { sub = null },
                    onRequestMedia = requestMedia,
                    onOpenAppSettings = openAppSettings,
                    onOpenUsageSettings = openUsage,
                    onRequestFolders = requestFolders
                )
                sub == "privacy" -> PrivacyScreen(onBack = { sub = null })
                sub == "monitor" -> MonitorScreen(
                    state, onBack = { sub = null },
                    onSet = { e, st, w, b -> vm.setMonitor(e, st, w, b) },
                    onAllowNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) monitorNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openAppSettings()
                    },
                    onTest = vm::testNotifications,
                    onOpenPremium = { sub = "premium" },
                    onSetDaily = { e, h -> vm.setDaily(e, h) },
                    onTestDaily = vm::testDaily
                )
                sub == "premium" -> PremiumScreen(
                    state, onBack = { sub = null },
                    onBuy = { plan -> (context as? Activity)?.let { vm.billing.buy(it, plan) } },
                    onRestore = vm.billing::restore,
                    onManage = {
                        context.safeStart(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/account/subscriptions?sku=premium&package=${context.packageName}")
                            )
                        )
                    }
                )

                tab == TAB_HOME -> HomeScreen(
                    state,
                    onScan = scan,
                    onOpenClean = { go(TAB_CLEAN, null) },
                    onOpenDuplicates = { go(TAB_CLEAN, "dups") },
                    onOpenVideos = { go(TAB_CLEAN, "videos") },
                    onOpenTrash = { go(TAB_CLEAN, "trash") },
                    onOpenFolders = { go(TAB_CLEAN, "folders") },
                    onRequestFolders = requestFolders,
                    onOpenApps = { openApps(SORT_SIZE) },
                    onOpenUnused = { openApps(SORT_UNUSED) },
                    onOpenCache = { openApps(SORT_CACHE) },
                    onOpenUsageSettings = openUsage,
                    onUninstall = uninstall,
                    onOpenAppSettings = openAppDetails,
                    onOpenBackup = { go(TAB_BACKUP, null) },
                    onOpenPermissions = { go(TAB_MORE, "perms") },
                    onAllowNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) monitorNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openAppSettings()
                    },
                )
                tab == TAB_CLEAN -> CleanScreen(
                    state,
                    onScan = scan,
                    onOpen = { id -> sub = id },
                    onOpenApps = { openApps(SORT_UNUSED) },
                    onOpenCache = { openApps(SORT_CACHE) },
                    onRequestFolders = requestFolders,
                )
                tab == TAB_BACKUP -> BackupScreen(
                    state,
                    onScan = scan,
                    onConnect = connectDrive,
                    onDisconnect = vm::disconnectDrive,
                    onStart = startBackup,
                    onStop = vm::stopBackup,
                    onRetry = vm::retryFailed,
                    onFree = freeSpace,
                    onRefresh = vm::loadDriveAccount,
                    onOpenPremium = { sub = "premium" },
                )
                tab == TAB_APPS -> AppsScreen(
                    state,
                    initialSort = appsSort,
                    onOpenUsageSettings = openUsage,
                    onUninstall = uninstall,
                    onOpenAppSettings = openAppDetails
                )
                else -> MoreScreen(
                    state,
                    versionName = versionName,
                    onOpenHistory = { sub = "history" },
                    onOpenPermissions = { sub = "perms" },
                    onOpenPrivacy = { sub = "privacy" },
                    onOpenMonitor = { sub = "monitor" },
                    onOpenPremium = { sub = "premium" },
                    onOpenRecover = { sub = "recover" },
                )
            }
        }
    }
}
