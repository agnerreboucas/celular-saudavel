package br.com.celularsaudavel.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.celularsaudavel.data.AppsRepository
import br.com.celularsaudavel.data.HistoryStore
import br.com.celularsaudavel.data.MediaRepository
import br.com.celularsaudavel.model.DuplicateGroup
import br.com.celularsaudavel.model.HealthScore
import br.com.celularsaudavel.model.HistoryEntry
import br.com.celularsaudavel.model.HistoryType
import br.com.celularsaudavel.model.InstalledApp
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

data class UiState(
    val storage: StorageInfo? = null,
    val mediaAccess: MediaAccess = MediaAccess.NONE,
    val usageAccess: Boolean = false,

    val scanning: Boolean = false,
    val scanStep: String = "",
    val scanProgress: Float? = null,
    val scanned: Boolean = false,
    val media: MediaSummary? = null,
    val largeVideos: List<MediaFile> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),

    val appsLoading: Boolean = false,
    val appsLoaded: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),

    val history: List<HistoryEntry> = emptyList(),
) {
    val duplicateBytes: Long get() = duplicates.sumOf { it.wastedBytes }
    val duplicateCount: Int get() = duplicates.sumOf { it.copies.size }
    val largeVideoBytes: Long get() = largeVideos.sumOf { it.sizeBytes }
    val unusedApps: List<InstalledApp> get() = if (usageAccess) apps.filter { it.isUnused() } else emptyList()
    val unusedAppsBytes: Long get() = unusedApps.sumOf { it.sizeBytes }
    val appsBytes: Long? get() = if (usageAccess && appsLoaded) apps.sumOf { it.sizeBytes } else null

    /** Espaço que dá para revisar: cópias duplicadas + vídeos grandes + apps sem uso. */
    val reviewableBytes: Long get() = duplicateBytes + largeVideoBytes + unusedAppsBytes

    val health: HealthScore?
        get() = if (!scanned) null else computeHealth(
            storage,
            reclaimableBytes = duplicateBytes + largeVideoBytes,
            unusedApps = if (usageAccess && appsLoaded) unusedApps.size else null
        )

    /** Lixeira não conta: o espaço só é liberado quando ela é esvaziada. */
    val totalFreed: Long get() = history.filter { it.type != HistoryType.VIDEOS_TRASHED }.sumOf { it.bytes }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val media = MediaRepository(app)
    private val appsRepo = AppsRepository(app)
    private val historyStore = HistoryStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshBasics()
    }

    /** Leve: roda ao abrir e sempre que o app volta para a frente. */
    fun refreshBasics() {
        viewModelScope.launch(Dispatchers.IO) {
            val storage = media.storageInfo()
            val access = media.mediaAccess()
            val usage = appsRepo.hasUsageAccess()
            val history = historyStore.load()
            val before = _state.value
            _state.update {
                it.copy(storage = storage, mediaAccess = access, usageAccess = usage, history = history)
            }
            // Acesso de uso acabou de ser liberado: recarrega apps com dados reais.
            if (usage != before.usageAccess && before.appsLoaded) loadApps()
        }
    }

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scanning = true, scanStep = "Lendo armazenamento…", scanProgress = null) }
            val storage = media.storageInfo()
            val access = media.mediaAccess()

            _state.update { it.copy(scanStep = "Contando fotos e vídeos…") }
            val images = media.images()
            val videos = media.videos()
            val summary = media.summary(images, videos)
            val large = media.largeVideos(videos)

            _state.update { it.copy(scanStep = "Procurando duplicadas…", scanProgress = 0f) }
            val dups = media.findDuplicates(images + videos) { done, total ->
                _state.update {
                    it.copy(
                        scanStep = "Comparando arquivos parecidos ($done de $total)…",
                        scanProgress = if (total > 0) done.toFloat() / total else null
                    )
                }
            }

            _state.update { it.copy(scanStep = "Revisando aplicativos…", scanProgress = null) }
            val usage = appsRepo.hasUsageAccess()
            val apps = runCatching { appsRepo.loadApps() }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    storage = storage,
                    mediaAccess = access,
                    usageAccess = usage,
                    media = summary,
                    largeVideos = large,
                    duplicates = dups,
                    apps = apps,
                    appsLoaded = true,
                    scanned = true,
                    scanning = false,
                    scanStep = "",
                    scanProgress = null
                )
            }
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
                storage = media.storageInfo()
            )
        }
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
}
