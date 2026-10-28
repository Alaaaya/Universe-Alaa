package com.alaa.universe

import org.json.JSONArray
import org.json.JSONObject

/** Exact string IDs, separate media namespaces, securely paired device + playlist + provider epoch. */
internal data class CategoryPolicy(val device: String, val playlist: String, val provider: String,
    val revision: String, val hidden: Map<String, Set<String>>) {
    val scope get() = listOf(device, playlist, provider)
    val hasHides get() = hidden.values.any { it.isNotEmpty() }
    fun allows(kind: String, ids: Collection<String>): Boolean {
        val type = when(kind) { "movie" -> "vod"; "episode" -> "series"; else -> kind }
        return (!hasHides || ids.isNotEmpty() && ids.none { it.isBlank() }) && ids.none { it in hidden[type].orEmpty() }
    }
    fun toJson() = JSONObject().put("device", device).put("playlist", playlist).put("provider", provider)
        .put("revision", revision).put("hidden", JSONObject().also { j -> hidden.forEach { (k,v) -> j.put(k, JSONArray(v.toList())) } })
    companion object {
        private val fields = listOf("category_settings_version", "category_provider_generation", "category_config_revision", "hidden_category_ids")
        private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private fun ids(j: JSONObject): Map<String, Set<String>> = listOf("live", "vod", "series").associateWith { kind ->
            val a = j.getJSONArray(kind); require(a.length() <= 10000)
            (0 until a.length()).map { i ->
                val id = a.get(i); require(id is String && Regex("[0-9]{1,64}").matches(id)); id
            }.toSet()
        }
        /** null means unsupported/malformed, NEVER authoritative empty. M3U ignores this extension. */
        fun parse(device: String, p: JSONObject): CategoryPolicy? = runCatching {
            val playlist = p.getString("id"); require(device.isNotBlank() && playlist.isNotBlank())
            if (p.getString("type") == "m3u" || fields.none { p.has(it) })
                CategoryPolicy(device, playlist, "legacy", "legacy-empty", mapOf("live" to emptySet(), "vod" to emptySet(), "series" to emptySet()))
            else {
                require(p.get("category_settings_version") is Number && p.getInt("category_settings_version") == 1 && p.getDouble("category_settings_version") == 1.0)
                val provider = p.getString("category_provider_generation"); require(uuid.matches(provider))
                val revision = p.getString("category_config_revision"); require(Regex("hc1_[0-9a-f]{64}").matches(revision))
                CategoryPolicy(device, playlist, provider, revision, ids(p.getJSONObject("hidden_category_ids")))
            }
        }.getOrNull()
        fun restore(j: JSONObject) = CategoryPolicy(j.getString("device"), j.getString("playlist"), j.getString("provider"),
            j.getString("revision"), ids(j.getJSONObject("hidden")))
    }
}
