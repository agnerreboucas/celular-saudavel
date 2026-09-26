package br.com.celularsaudavel.data

import android.content.Context
import br.com.celularsaudavel.model.HistoryEntry
import br.com.celularsaudavel.model.HistoryType
import org.json.JSONArray
import org.json.JSONObject

/** Histórico local simples. Nada sai do aparelho. */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = runCatching { HistoryType.valueOf(o.getString("type")) }.getOrNull()
                    ?: return@mapNotNull null
                HistoryEntry(
                    timestamp = o.getLong("ts"),
                    type = type,
                    count = o.optInt("count"),
                    bytes = o.optLong("bytes"),
                    detail = o.optString("detail")
                )
            }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(entry: HistoryEntry): List<HistoryEntry> {
        val list = (listOf(entry) + load()).take(200)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("ts", it.timestamp)
                    .put("type", it.type.name)
                    .put("count", it.count)
                    .put("bytes", it.bytes)
                    .put("detail", it.detail)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return list
    }

    private companion object {
        const val KEY = "entries"
    }
}
