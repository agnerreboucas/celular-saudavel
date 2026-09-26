package br.com.celularsaudavel.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import br.com.celularsaudavel.model.DAY_MS
import br.com.celularsaudavel.model.InstalledApp
import java.io.File

class AppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun loadApps(): List<InstalledApp> {
        val usage = hasUsageAccess()
        val lastUsedMap = if (usage) lastUsedTimes() else emptyMap()
        val ssm = context.getSystemService(StorageStatsManager::class.java)

        return pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .mapNotNull { ai ->
                try {
                    val pi = pm.getPackageInfo(ai.packageName, 0)
                    var size = 0L
                    var estimate = true
                    if (usage && ssm != null) {
                        try {
                            val st = ssm.queryStatsForPackage(
                                StorageManager.UUID_DEFAULT, ai.packageName, Process.myUserHandle()
                            )
                            size = st.appBytes + st.dataBytes + st.cacheBytes
                            estimate = false
                        } catch (_: Exception) {
                        }
                    }
                    if (estimate) {
                        size = File(ai.sourceDir).length() +
                            (ai.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)
                    }
                    InstalledApp(
                        packageName = ai.packageName,
                        name = ai.loadLabel(pm).toString(),
                        sizeBytes = size,
                        sizeIsEstimate = estimate,
                        installTime = pi.firstInstallTime,
                        lastUsed = lastUsedMap[ai.packageName]
                    )
                } catch (_: Exception) {
                    null
                }
            }
    }

    private fun lastUsedTimes(): Map<String, Long> = try {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(now - 365 * DAY_MS, now)
            .mapValues { it.value.lastTimeUsed }
            .filterValues { it > 0 }
    } catch (_: Exception) {
        emptyMap()
    }

    @Suppress("DEPRECATION")
    fun isInstalled(packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
