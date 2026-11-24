package com.alaa.universe

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Small payload-free viewing checkpoint; stream URLs and provider credentials are never persisted. */
internal data class ResumePoint(
    val id: String,
    val kind: String,
    val name: String,
    val categoryId: String,
    val season: Int,
    val positionMs: Long,
    val durationMs: Long
)

internal class PlaybackProgressStore(context: Context, scope: String) {
    private val prefs = context.applicationContext.getSharedPreferences("playback-progress-v1", Context.MODE_PRIVATE)
    private val key = "resume_" + MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun read(): ResumePoint? = runCatching {
        val j = prefs.getString(key, null)?.let(::JSONObject) ?: return null
        ResumePoint(j.getString("id"), j.getString("kind"), j.getString("name"), j.getString("categoryId"),
            j.optInt("season"), j.getLong("positionMs"), j.getLong("durationMs"))
    }.getOrNull()?.takeIf { it.kind in setOf("movie", "episode") && it.positionMs > 0 && it.durationMs > 0 }

    fun save(entry: MediaEntry, positionMs: Long, durationMs: Long) {
        if (entry.kind !in setOf("movie", "episode") || entry.id.isBlank() || durationMs <= 0 || positionMs <= 0) return
        if (positionMs >= durationMs - 15_000L) { clear(entry); return }
        val j = JSONObject().put("id", entry.id).put("kind", entry.kind).put("name", entry.name)
            .put("categoryId", entry.categoryId).put("season", entry.season)
            .put("positionMs", positionMs).put("durationMs", durationMs)
        prefs.edit().putString(key, j.toString()).apply()
    }

    fun clear(entry: MediaEntry? = null) {
        val current = read()
        if (entry == null || current?.id == entry.id && current.kind == entry.kind) prefs.edit().remove(key).apply()
    }

    companion object {
        fun formatTime(ms: Long): String {
            val total = (ms.coerceAtLeast(0) / 1000).toInt()
            return if (total >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
            else String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
        }
    }
}

/** Bounded search over the already-loaded catalogue; no network calls and no extra entry views. */
internal object MediaQuickSearch {
    private val SEARCHABLE_KINDS = setOf("live", "movie", "series")
    fun search(entries: List<MediaEntry>, query: String, limit: Int = 60): List<MediaEntry> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty() || limit <= 0) return emptyList()
        // Keep the original stable prefix-first ordering without sorting every match.
        // A broad query can match all 10k+ channels; sorting those only to return 60
        // made each debounced search O(N log N) with avoidable pair allocations.
        val prefixes = ArrayList<MediaEntry>(limit)
        val contains = ArrayList<MediaEntry>(limit)
        for (entry in entries) {
            if (entry.kind !in SEARCHABLE_KINDS) continue
            val name = entry.name.trim().lowercase(Locale.ROOT)
            if (!name.contains(q)) continue
            if (name.startsWith(q)) {
                if (prefixes.size < limit) prefixes += entry
            } else if (contains.size < limit) contains += entry
        }
        if (prefixes.size >= limit) return prefixes
        return buildList(prefixes.size + minOf(contains.size, limit - prefixes.size)) {
            addAll(prefixes)
            addAll(contains.take(limit - prefixes.size))
        }
    }
}

internal object MediaSort {
    fun sort(values: List<MediaEntry>, kind: String, mode: String): List<MediaEntry> {
        if (kind == "live" || mode == "default") return values
        val indexed = values.withIndex()
        return when (mode) {
            "year" -> indexed.sortedWith(compareBy<IndexedValue<MediaEntry>> { it.value.releaseYear == null }
                .thenByDescending { it.value.releaseYear }.thenBy { it.index }).map { it.value }
            "newest" -> indexed.sortedWith(compareBy<IndexedValue<MediaEntry>> { it.value.addedTimestamp == null }
                .thenByDescending { it.value.addedTimestamp }.thenBy { it.index }).map { it.value }
            "popular" -> indexed.sortedWith(compareBy<IndexedValue<MediaEntry>> { it.value.viewCount == null }
                .thenByDescending { it.value.viewCount }.thenBy { it.index }).map { it.value }
            else -> values
        }
    }
}
