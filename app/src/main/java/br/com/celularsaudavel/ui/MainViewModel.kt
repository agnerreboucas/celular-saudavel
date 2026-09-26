package br.com.celularsaudavel.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.celularsaudavel.data.AppsRepository
import br.com.celularsaudavel.data.BackupDb
import br.com.celularsaudavel.data.BackupRow
import br.com.celularsaudavel.data.BackupState
import br.com.celularsaudavel.data.BackupSummary
import br.com.celularsaudavel.data.BackupWorker
import br.com.celularsaudavel.data.DriveAccount
import br.com.celularsaudavel.data.DriveRepository
import br.com.celularsaudavel.data.FolderRepository
import br.com.celularsaudavel.data.HealthMonitorWorker
import br.com.celularsaudavel.data.HealthNotifier
import br.com.celularsaudavel.data.HistoryStore
import br.com.celularsaudavel.data.MonitorPrefs
import br.com.celularsaudavel.data.MediaRepository
import br.com.celularsaudavel.model.CategoryStat
import br.com.celularsaudavel.model.DuplicateGroup
import br.com.celularsaudavel.model.FolderCategory
import br.com.celularsaudavel.model.HealthScore
import br.com.celularsaudavel.model.HistoryEntry
import br.com.celularsaudavel.model.HistoryType
import br.com.celularsaudavel.model.InstalledApp
import br.com.celularsaudavel.model.LocalFile
import br.com.celularsaudavel.model.MediaAccess
import br.com.celularsaudavel.model.MediaFile
import br.com.celularsaudavel.model.MediaSummary
import br.com.celularsaudavel.model.StorageInfo
import br.com.celularsaudavel.model.computeHealth
import br.com.celularsaudavel.model.isUnused
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Grupos do backup. */
object BackupCat {
    const val CAMERA = "camera"
    const val PHOTOS = "photos"
    const val VIDEO = "video"
}

data class DriveUi(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val account: DriveAccount? = null,
    val error: String? = null,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val summary: BackupSummary = BackupSummary(),
    /** O que ainda não tem backup, por grupo */
    val toProtect: Map<String, CategoryStat> = emptyMap(),
)

data class MonitorUi(
    val enabled: Boolean = true,
    val storageAlerts: Boolean = true,
    val weeklyCheckup: Boolean = true,
    val backupReminder: Boolean = true,
    val canNotify: Boolean = false,
)

data class UiState(
    val storage: StorageInfo? = null,
    val mediaAccess: MediaAccess = MediaAccess.NONE,
    val usageAccess: Boolean = false,
    val folderAccess: Boolean = false,

    val scanning: Boolean = false,
    val scanStep: String = "",
    val scanProgress: Float? = null,
    val scanned: Boolean = false,
    val media: MediaSummary? = null,
    val largeVideos: List<MediaFile> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val sequences: List<DuplicateGroup> = emptyList(),
    val screenshots: List<MediaFile> = emptyList(),
    val trash: List<MediaFile> = emptyList(),

    val foldersLoading: Boolean = false,
    val folders: List<FolderCategory> = emptyList(),
    val bigFolders: List<Pair<String, Long>> = emptyList(),

    val appsLoading: Boolean = false,
    val appsLoaded: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),

    val drive: DriveUi = DriveUi(),
    val monitor: MonitorUi = MonitorUi(),
    val history: List<HistoryEntry> = emptyList(),
) {
    val duplicateBytes: Long get() = duplicates.sumOf { it.wastedBytes }
    val duplicateCount: Int get() = duplicates.sumOf { it.copies.size }
    val sequenceCount: Int get() = sequences.sumOf { it.files.size }
    val sequenceBytes: Long get() = sequences.sumOf { it.wastedBytes }
    val screenshotBytes: Long get() = screenshots.sumOf { it.sizeBytes }
    val largeVideoBytes: Long get() = largeVideos.sumOf { it.sizeBytes }
    val trashBytes: Long get() = trash.sumOf { it.sizeBytes }
    val unusedApps: List<InstalledApp> get() = if (usageAccess) apps.filter { it.removable && it.isUnused() }.sortedByDescending { it.sizeBytes } else emptyList()
    val unusedAppsBytes: Long get() = unusedApps.sumOf { it.sizeBytes }
    val cacheBytes: Long get() = apps.sumOf { it.cacheBytes ?: 0L }
    val appsWithCache: Int get() = apps.count { (it.cacheBytes ?: 0L) > 10_000_000L }
    val appsBytes: Long? get() = if (usageAccess && appsLoaded) apps.sumOf { it.sizeBytes } else null
    val safeFolderBytes: Long get() = folders.filter { it.safe }.sumOf { it.bytes }
    val whatsappBytes: Long get() = folders.filter { it.group == "WhatsApp" }.sumOf { it.bytes }

    /** Espaço que dá para revisar agora. */
    val reviewableBytes: Long
        get() = duplicateBytes + largeVideoBytes + unusedAppsBytes + trashBytes + safeFolderBytes + cacheBytes

    val health: HealthScore?
        get() = if (!scanned) null else computeHealth(
            storage,
            reclaimableBytes = duplicateBytes + largeVideoBytes + trashBytes + safeFolderBytes,
            unusedApps = if (usageAccess && appsLoaded) unusedApps.size else null
        )

    /** Lixeira não conta: o espaço só é liberado quando ela é esvaziada. */
    val totalFreed: Long
        get() = history.filter {
            it.type != HistoryType.VIDEOS_TRASHED && it.type != HistoryType.SEQUENCE_TRASHED && it.type != HistoryType.SCREENSHOTS_TRASHED
        }.sumOf { it.bytes }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val media = MediaRepository(app)
    private val appsRepo = AppsRepository(app)
    private val folderRepo = FolderRepository(app)
    private val historyStore = HistoryStore(app)
    private val drive = DriveRepository(app)
    private val backupDb = BackupDb(app)
    private val monitorPrefs = MonitorPrefs(app)

    private var images: List<MediaFile> = emptyList()
    private var videos: List<MediaFile> = emptyList()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (monitorPrefs.enabled) HealthMonitorWorker.schedule(app)
        refreshBasics()
        _state.update { it.copy(drive = it.drive.copy(connected = drive.connected)) }
        viewModelScope.launch {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(BackupWorker.NAME).collect { infos ->
                val info = infos.firstOrNull()
                val running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
                val done = info?.progress?.getInt(BackupWorker.KEY_DONE, 0) ?: 0
                val total = info?.progress?.getInt(BackupWorker.KEY_TOTAL, 0) ?: 0
                val err = if (info?.state == WorkInfo.State.FAILED) info.outputData.getString(BackupWorker.KEY_ERROR) else null
                _state.update {
                    it.copy(drive = it.drive.copy(running = running, done = done, total = total, error = err ?: it.drive.error))
                }
                refreshBackupSummary()
            }
        }
    }

    /** Leve: roda ao abrir e sempre que o app volta para a frente. */
    fun refreshBasics() {
        viewModelScope.launch(Dispatchers.IO) {
            val storage = media.storageInfo()
            val access = media.mediaAccess()
            val usage = appsRepo.hasUsageAccess()
            val folders = folderRepo.hasAllFilesAccess()
            val history = historyStore.load()
            val before = _state.value
            _state.update {
                it.copy(
                    storage = storage, mediaAccess = access, usageAccess = usage, folderAccess = folders, history = history,
                    monitor = monitorUi()
                )
            }
            if (usage != before.usageAccess && before.appsLoaded) loadApps()
            if (folders && !before.folderAccess && before.scanned) loadFolders()
        }
    }

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scanning = true, scanStep = "Lendo armazenamento…", scanProgress = null) }
            val storage = media.storageInfo()
            val access = media.mediaAccess()

            _state.update { it.copy(scanStep = "Contando fotos e vídeos…") }
            images = media.images()
            videos = media.videos()
            val summary = media.summary(images, videos)
            val large = media.largeVideos(videos)
            val trash = media.trashed()
            val shots = media.screenshots(images)
            val seqs = media.sequences(images)

            _state.update { it.copy(scanStep = "Procurando duplicadas…", scanProgress = 0f) }
            val dups = media.findDuplicates(images + videos) { done, total ->
                _state.update {
                    it.copy(
                        scanStep = "Comparando arquivos parecidos ($done de $total)…",
                        scanProgress = if (total > 0) done.toFloat() / total else null
                    )
                }
            }

            _state.update { it.copy(scanStep = "Olhando pastas do WhatsApp, Downloads e temporários…", scanProgress = null) }
            val folderAccess = folderRepo.hasAllFilesAccess()
            val folders = runCatching { folderRepo.scan() }.getOrDefault(emptyList())

            _state.update { it.copy(scanStep = "Revisando aplicativos…") }
            val usage = appsRepo.hasUsageAccess()
            val apps = runCatching { appsRepo.loadApps() }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    storage = storage,
                    mediaAccess = access,
                    usageAccess = usage,
                    folderAccess = folderAccess,
                    media = summary,
                    largeVideos = large,
                    duplicates = dups,
                    sequences = seqs,
                    screenshots = shots,
                    trash = trash,
                    folders = folders,
                    apps = apps,
                    appsLoaded = true,
                    scanned = true,
                    scanning = false,
                    scanStep = "",
                    scanProgress = null
                )
            }
            refreshBackupSummary()
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(appsLoading = true) }
            val usage = appsRepo.hasUsageAccess()
            val apps = runCatching { appsRepo.loadApps() }.getOrDefault(emptyList())
            _state.update { it.copy(apps = apps, appsLoaded = true, appsLoading = false, usageAccess = usage) }
        }
    }

    fun loadFolders(withBiggest: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(foldersLoading = true) }
            val access = folderRepo.hasAllFilesAccess()
            val folders = runCatching { folderRepo.scan() }.getOrDefault(emptyList())
            val big = if (withBiggest) runCatching { folderRepo.biggestFolders() }.getOrDefault(emptyList()) else _state.value.bigFolders
            _state.update { it.copy(folders = folders, bigFolders = big, folderAccess = access, foldersLoading = false) }
        }
    }

    fun deleteFolderFiles(files: List<LocalFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = folderRepo.delete(files)
            if (deleted.isNotEmpty()) {
                val history = historyStore.add(
                    HistoryEntry(System.currentTimeMillis(), HistoryType.FILES_DELETED, deleted.size, deleted.sumOf { it.sizeBytes })
                )
                val gone = deleted.map { it.path }.toSet()
                _state.update { s ->
                    s.copy(
                        history = history,
                        folders = s.folders.map { c -> c.copy(files = c.files.filterNot { it.path in gone }) }.filter { it.files.isNotEmpty() },
                        storage = media.storageInfo()
                    )
                }
            }
        }
    }

    fun loadTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            val t = media.trashed()
            _state.update { it.copy(trash = t, storage = media.storageInfo()) }
        }
    }

    fun restoreRequest(uris: List<Uri>) = media.restoreRequest(uris)

    fun onTrashRestored() = loadTrash()

    fun deleteRequest(uris: List<Uri>) = media.deleteRequest(uris)
    fun trashRequest(uris: List<Uri>) = media.trashRequest(uris)

    /** Chamado depois que o usuário confirmou na janela do sistema. */
    fun onMediaRemoved(type: HistoryType, removed: List<MediaFile>) {
        val uris = removed.map { it.uri }.toSet()
        val bytes = removed.sumOf { it.sizeBytes }
        val history = historyStore.add(
            HistoryEntry(System.currentTimeMillis(), type, removed.size, bytes)
        )
        _state.update { s ->
            s.copy(
                history = history,
                largeVideos = s.largeVideos.filterNot { it.uri in uris },
                duplicates = s.duplicates
                    .map { g -> DuplicateGroup(g.files.filterNot { it.uri in uris }) }
                    .filter { it.files.size > 1 },
                sequences = s.sequences
                    .map { g -> DuplicateGroup(g.files.filterNot { it.uri in uris }) }
                    .filter { it.files.size > 1 },
                screenshots = s.screenshots.filterNot { it.uri in uris },
                trash = s.trash.filterNot { it.uri in uris },
                storage = media.storageInfo()
            )
        }
        images = images.filterNot { it.uri in uris }
        videos = videos.filterNot { it.uri in uris }
        if (type == HistoryType.VIDEOS_TRASHED || type == HistoryType.SEQUENCE_TRASHED || type == HistoryType.SCREENSHOTS_TRASHED) loadTrash()
    }

    fun onUninstallFinished(app: InstalledApp) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!appsRepo.isInstalled(app.packageName)) {
                val history = historyStore.add(
                    HistoryEntry(
                        System.currentTimeMillis(), HistoryType.APP_UNINSTALLED, 1,
                        app.sizeBytes, detail = app.name
                    )
                )
                _state.update { s ->
                    s.copy(
                        history = history,
                        apps = s.apps.filterNot { it.packageName == app.packageName },
                        storage = media.storageInfo()
                    )
                }
            }
        }
    }

    // ---------------- Acompanhamento ----------------

    private fun monitorUi() = MonitorUi(
        enabled = monitorPrefs.enabled,
        storageAlerts = monitorPrefs.storageAlerts,
        weeklyCheckup = monitorPrefs.weeklyCheckup,
        backupReminder = monitorPrefs.backupReminder,
        canNotify = HealthNotifier.canNotify(getApplication())
    )

    fun setMonitor(enabled: Boolean? = null, storage: Boolean? = null, weekly: Boolean? = null, backup: Boolean? = null) {
        enabled?.let {
            monitorPrefs.enabled = it
            if (it) HealthMonitorWorker.schedule(getApplication()) else HealthMonitorWorker.cancel(getApplication())
        }
        storage?.let { monitorPrefs.storageAlerts = it }
        weekly?.let { monitorPrefs.weeklyCheckup = it }
        backup?.let { monitorPrefs.backupReminder = it }
        _state.update { it.copy(monitor = monitorUi()) }
    }

    fun refreshMonitor() = _state.update { it.copy(monitor = monitorUi()) }

    fun testNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            HealthMonitorWorker.runChecks(getApplication(), force = true)
        }
    }

    // ---------------- Google Drive ----------------

    fun onDriveConnecting() {
        _state.update { it.copy(drive = it.drive.copy(connecting = true, error = null)) }
    }

    fun onDriveAuthorized(token: String?) {
        drive.setToken(token)
        _state.update { it.copy(drive = it.drive.copy(connected = true, connecting = false, error = null)) }
        loadDriveAccount()
    }

    fun onDriveError(message: String) {
        _state.update { it.copy(drive = it.drive.copy(connecting = false, error = message)) }
    }

    fun loadDriveAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val acc = drive.account()
                _state.update { it.copy(drive = it.drive.copy(account = acc, connected = true, error = null)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(drive = it.drive.copy(error = "Não consegui falar com o Drive: ${e.message ?: "erro desconhecido"}"))
                }
            }
            refreshBackupSummary()
        }
    }

    fun disconnectDrive() {
        BackupWorker.stop(getApplication())
        drive.disconnect()
        _state.update { it.copy(drive = DriveUi()) }
    }

    private fun categoryOf(f: MediaFile): String = when {
        f.isVideo -> BackupCat.VIDEO
        f.folder.contains("camera", ignoreCase = true) -> BackupCat.CAMERA
        else -> BackupCat.PHOTOS
    }

    fun refreshBackupSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = backupDb.summary()
            val all = images + videos
            val states = if (all.isEmpty()) emptyMap() else backupDb.stateOf(all.map { it.uri.toString() })
            val notProtected = all.filter {
                val st = states[it.uri.toString()]
                st != BackupState.VERIFIED && st != BackupState.DELETED_LOCAL
            }
            val toProtect = notProtected.groupBy { categoryOf(it) }
                .mapValues { (_, l) -> CategoryStat(l.size, l.sumOf { it.sizeBytes }) }
            _state.update { it.copy(drive = it.drive.copy(summary = summary, toProtect = toProtect)) }
        }
    }

    fun startBackup(categories: Set<String>, wifiOnly: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = (images + videos).filter { categoryOf(it) in categories }
            backupDb.enqueue(files) { categoryOf(it) }
            _state.update { it.copy(drive = it.drive.copy(error = null)) }
            BackupWorker.start(getApplication(), wifiOnly)
            refreshBackupSummary()
        }
    }

    fun stopBackup() = BackupWorker.stop(getApplication())

    fun retryFailed(wifiOnly: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            backupDb.retryFailed()
            BackupWorker.start(getApplication(), wifiOnly)
            refreshBackupSummary()
        }
    }

    /** Só arquivos no estado VERIFIED. Até 1.000 por vez (limite prático da janela do Android). */
    fun freeRequest(): Pair<IntentSender?, List<BackupRow>> {
        val rows = backupDb.verified(1000)
        if (rows.isEmpty()) return null to emptyList()
        val sender = try {
            media.deleteRequest(rows.map { Uri.parse(it.uri) })
        } catch (_: Exception) {
            null
        }
        return sender to rows
    }

    fun onFreed(rows: List<BackupRow>) {
        viewModelScope.launch(Dispatchers.IO) {
            backupDb.markDeleted(rows.map { it.uri })
            val history = historyStore.add(
                HistoryEntry(System.currentTimeMillis(), HistoryType.BACKUP_FREED, rows.size, rows.sumOf { it.size })
            )
            val gone = rows.map { it.uri }.toSet()
            images = images.filterNot { it.uri.toString() in gone }
            videos = videos.filterNot { it.uri.toString() in gone }
            _state.update { it.copy(history = history, storage = media.storageInfo()) }
            refreshBackupSummary()
        }
    }
}
