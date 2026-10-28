package com.alaa.universe

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

data class ProviderLogin(val name: String, val server: String, val username: String = "", val password: String = "", val m3uUrl: String = "", val allowHttp: Boolean = false) {
    override fun toString() = "ProviderLogin(redacted)"
}
data class MediaEntry(val id: String, val name: String, val categoryId: String, val kind: String, val icon: String = "", val extension: String = "mp4", val directUrl: String = "", val season: Int = 0, val plot: String = "") {
    override fun toString() = "MediaEntry(redacted)"
}
data class MediaCategory(val id: String, val name: String, val kind: String)
data class ProviderSnapshot(val name: String, val expires: Long?, val entries: List<MediaEntry>, val categories: List<MediaCategory>, val updated: Long = System.currentTimeMillis()) {
    /** Expiry is Unix seconds, as supplied by Xtream. Null means unknown/unlimited, not expired. */
    fun daysLeft(nowMillis: Long = System.currentTimeMillis()): Long? =
        expires?.let { ((it - nowMillis / 1000).coerceAtLeast(0) + 86399) / 86400 }
    fun toJson(): JSONObject = JSONObject().put("name", name).put("expires", expires ?: JSONObject.NULL)
        .put("updated", updated).put("entries", JSONArray(entries.map { entryJson(it) }))
        .put("categories", JSONArray(categories.map { JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind) }))
    companion object {
        fun fromJson(j: JSONObject) = ProviderSnapshot(j.getString("name"), if (j.isNull("expires")) null else j.getLong("expires"),
            objects(j.getJSONArray("entries")).map { entryFromJson(it) },
            objects(j.getJSONArray("categories")).map { MediaCategory(it.getString("id"), it.getString("name"), it.getString("kind")) }, j.getLong("updated"))
    }
}
private fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }
private fun entryJson(e: MediaEntry) = JSONObject().put("id", e.id).put("name", e.name).put("categoryId", e.categoryId)
    .put("kind", e.kind).put("icon", e.icon).put("extension", e.extension).put("directUrl", e.directUrl).put("season", e.season).put("plot", e.plot)
private fun entryFromJson(j: JSONObject) = MediaEntry(j.getString("id"), j.getString("name"), j.getString("categoryId"), j.getString("kind"),
    j.optString("icon"), j.optString("extension", "mp4"), j.optString("directUrl"), j.optInt("season"), j.optString("plot"))

/** Blocking API. Call on an IO worker, never the Android main thread. No network response is logged. */
class ProviderRepository internal constructor(private val vault: CredentialVault, private val testFetch: ((String) -> String)? = null) {
    constructor(context: Context) : this(CredentialVault(context.applicationContext))
    private val stateLock = Any()
    private var initialized = false
    private var manifest: JSONObject? = null
    private var activeLogin: ProviderLogin? = null
    private var activeSnapshot: ProviderSnapshot? = null
    private var generation = 0L
    private fun ensureLoaded() {
        if (initialized) return
        val state = vault.read("state")?.let { JSONObject(it) }
        val l = state?.getJSONObject("login")
        activeLogin = l?.let { ProviderLogin(it.getString("name"), it.getString("server"), it.optString("username"),
            it.optString("password"), it.optString("m3uUrl"), it.optBoolean("allowHttp")) }
        manifest = state
        // Login must not deserialize the catalogue. The snapshot is loaded lazily by cached().
        initialized = true
    }
    fun login(): ProviderLogin? = safe { synchronized(stateLock) { ensureLoaded(); activeLogin } }
    fun cached(): ProviderSnapshot? = safe { synchronized(stateLock) {
        ensureLoaded()
        if (activeSnapshot == null) manifest?.let { state ->
            activeSnapshot = if (state.has("snapshot")) ProviderSnapshot.fromJson(state.getJSONObject("snapshot")) else {
                val entries = ArrayList<MediaEntry>()
                for (chunk in objects(state.getJSONArray("chunks"))) {
                    val plaintext = vault.read(chunk.getString("file")) ?: error("Missing catalogue chunk.")
                    JsonReader(java.io.StringReader(plaintext)).use { reader ->
                        readCachedEntries(reader) { entries.add(it) }
                    }
                }
                ProviderSnapshot(state.getString("name"), if (state.isNull("expires")) null else state.getLong("expires"), entries,
                    objects(state.getJSONArray("categories")).map { MediaCategory(it.getString("id"), it.getString("name"), it.getString("kind")) }, state.getLong("updated"))
            }
        }
        activeSnapshot
    } }
    fun connect(login: ProviderLogin): ProviderSnapshot = safe {
        validateLogin(login)
        val normalized = login.copy(name = login.name.trim().ifBlank {
            validatedUrl(login.m3uUrl.ifBlank { login.server }, login.allowHttp).host
        })
        val ticket = synchronized(stateLock) { ensureLoaded(); ++generation }
        loadAndPublish(normalized, ticket)
    }
    private fun loadAndPublish(login: ProviderLogin, ticket: Long): ProviderSnapshot {
        val result = if (login.m3uUrl.isNotBlank()) parseM3u(fetch(login.m3uUrl, login), login) else loadXtream(login)
        val prefix = "catalog_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val chunks = JSONArray()
        val staged = mutableListOf<String>()
        try {
            // Serialize one bounded chunk at a time, never build a JSON tree for the entire account.
            var chunk = JSONArray()
            var bytes = 2
            fun flush() {
                if (chunk.length() == 0) return
                val file = prefix + "_" + staged.size
                synchronized(stateLock) {
                    check(ticket == generation)
                    vault.write(file, chunk.toString())
                }
                staged += file
                chunks.put(JSONObject().put("file", file))
                chunk = JSONArray(); bytes = 2
            }
            for (entry in result.entries) {
                val row = entryJson(entry)
                val size = row.toString().toByteArray(Charsets.UTF_8).size + 1
                require(size < 2 * 1024 * 1024 - 64)
                if (bytes + size > 2 * 1024 * 1024 - 64) flush()
                chunk.put(row); bytes += size
            }
            flush()
            val state = JSONObject().put("login", JSONObject().put("name", login.name).put("server", login.server)
                .put("username", login.username).put("password", login.password).put("m3uUrl", login.m3uUrl).put("allowHttp", login.allowHttp))
                .put("name", result.name).put("expires", result.expires ?: JSONObject.NULL).put("updated", result.updated)
                .put("chunks", chunks).put("categories", JSONArray(result.categories.map {
                    JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                }))
            synchronized(stateLock) {
                check(ticket == generation) { "Provider changed while loading." }
                val old = manifest?.optJSONArray("chunks")
                vault.write("state", state.toString()) // Sole atomic commit point.
                manifest = state; activeLogin = login; activeSnapshot = result; initialized = true
                old?.let { objects(it).forEach { row -> runCatching { vault.delete(row.getString("file")) } } }
            }
        } catch (e: Exception) {
            staged.forEach { vault.delete(it) }
            throw e
        }
        return result
    }
    fun refresh(): ProviderSnapshot = safe {
        val request = synchronized(stateLock) {
            ensureLoaded()
            (activeLogin ?: error("Connect a provider first.")) to ++generation
        }
        loadAndPublish(request.first, request.second)
    }
    fun cachedEpisodes(series: MediaEntry): List<MediaEntry> {
        val l = login() ?: return emptyList()
        return vault.read(episodeKey(l, series))?.let { objects(JSONArray(it)).map { j -> entryFromJson(j) } } ?: emptyList()
    }
    /** Explicit network refresh; cachedEpisodes supplies immediate offline results. */
    fun episodes(series: MediaEntry): List<MediaEntry> = safe {
        require(series.kind == "series") { "Select a series." }
        val request = synchronized(stateLock) { ensureLoaded(); (activeLogin ?: error("Connect a provider first.")) to generation }
        val l = request.first
        if (l.m3uUrl.isNotBlank()) throw UnsupportedOperationException("M3U playlists do not provide a series episode catalogue.")
        val response = JSONObject(fetch(api(l, "get_series_info", "series_id" to series.id), l))
        val seasons = response.optJSONObject("episodes") ?: error("Provider returned no episode catalogue.")
        val episodes = mutableListOf<MediaEntry>()
        for (key in seasons.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }) {
            val rows = seasons.optJSONArray(key) ?: continue
            for (row in objects(rows)) {
                val info = row.optJSONObject("info")
                episodes += MediaEntry(row.get("id").toString(), row.optString("title", "Episode"), series.categoryId, "episode",
                    info?.optString("movie_image") ?: series.icon, extension(row.optString("container_extension", "mp4")),
                    season = row.optInt("season", key.toIntOrNull() ?: 0), plot = info?.optString("plot") ?: "")
            }
        }
        val serialized = JSONArray(episodes.map { entryJson(it) }).toString()
        synchronized(stateLock) {
            check(generation == request.second) { "Provider changed while loading." }
            vault.write(episodeKey(l, series), serialized)
        }
        episodes
    }
    fun streamUrl(entry: MediaEntry): String = safe {
        val l = login() ?: error("Connect a provider first.")
        if (entry.directUrl.isNotBlank()) return@safe validatedUrl(entry.directUrl, l.allowHttp).toASCIIString()
        check(l.m3uUrl.isBlank()) { "Playlist stream URL is unavailable." }
        val type = when (entry.kind) { "live" -> "live"; "movie" -> "movie"; "episode" -> "series"; else -> error("Select an episode first.") }
        val ext = if (entry.kind == "live") "ts" else extension(entry.extension)
        base(l) + "/$type/" + encode(l.username) + "/" + encode(l.password) + "/" + encode(entry.id) + "." + ext
    }
    fun clear() = synchronized(stateLock) {
        ++generation; vault.clear(); activeLogin = null; activeSnapshot = null; manifest = null; initialized = true
    }

    private fun loadXtream(l: ProviderLogin): ProviderSnapshot {
        val user = validateAuth(JSONObject(fetch(api(l), l)))
        val categories = mutableListOf<MediaCategory>()
        val entries = mutableListOf<MediaEntry>()
        for ((kind, catAction, streamAction) in listOf(Triple("live", "get_live_categories", "get_live_streams"), Triple("movie", "get_vod_categories", "get_vod_streams"), Triple("series", "get_series_categories", "get_series"))) {
            arrayRows(api(l, catAction), l) {
                categories += MediaCategory(it.get("category_id").toString(), it.optString("category_name", "Other"), kind)
            }
            arrayRows(api(l, streamAction), l) {
                entries += MediaEntry(it.get(if (kind == "series") "series_id" else "stream_id").toString(), it.optString("name", "Untitled"),
                    it.optString("category_id"), kind, it.optString(if (kind == "series") "cover" else "stream_icon"),
                    extension(it.optString("container_extension", "mp4")), plot = it.optString("plot"))
            }
        }
        val expiry = user.optString("exp_date").toLongOrNull()?.takeIf { it > 0 }
        return ProviderSnapshot(l.name, expiry, entries, categories)
    }
    /** Streaming arrays retain only one provider object at a time, skipping nested unused fields. */
    private fun arrayRows(url: String, l: ProviderLogin, consume: (JSONObject) -> Unit) {
        val uri = validatedUrl(url, l.allowHttp)
        val fake = testFetch
        if (fake != null) {
            JsonReader(java.io.StringReader(fake(url))).use { readRows(it, consume) }
            return
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000; connection.readTimeout = 25000
            connection.instanceFollowRedirects = false
            check(connection.responseCode in 200..299)
            check(connection.contentLengthLong <= ARRAY_MAX_BYTES)
            val deadline = System.nanoTime() + 300_000_000_000L
            val bounded = object : java.io.FilterInputStream(connection.inputStream) {
                var count = 0L
                private fun tally(n: Int): Int {
                    if (n > 0) count += n
                    check(count <= ARRAY_MAX_BYTES && System.nanoTime() < deadline)
                    return n
                }
                override fun read(): Int { val n = super.read(); tally(if (n < 0) 0 else 1); return n }
                override fun read(b: ByteArray, off: Int, len: Int): Int = tally(`in`.read(b, off, len))
            }
            JsonReader(java.io.InputStreamReader(bounded, Charsets.UTF_8)).use { readRows(it, consume) }
        } finally { connection.disconnect() }
    }
    /** Chunk restore does not materialize a JSONArray, even for an individual encrypted chunk. */
    private fun readCachedEntries(reader: JsonReader, consume: (MediaEntry) -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) {
            var id = ""; var name = ""; var category = ""; var kind = ""
            var icon = ""; var extension = "mp4"; var direct = ""; var season = 0; var plot = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextString()
                    "name" -> name = reader.nextString()
                    "categoryId" -> category = reader.nextString()
                    "kind" -> kind = reader.nextString()
                    "icon" -> icon = reader.nextString()
                    "extension" -> extension = reader.nextString()
                    "directUrl" -> direct = reader.nextString()
                    "season" -> season = reader.nextInt()
                    "plot" -> plot = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            consume(MediaEntry(id, name, category, kind, icon, extension, direct, season, plot))
        }
        reader.endArray()
        check(reader.peek() == JsonToken.END_DOCUMENT)
    }
    internal fun readRows(reader: JsonReader, consume: (JSONObject) -> Unit) {
        val wanted = setOf("category_id", "category_name", "series_id", "stream_id", "name", "cover", "stream_icon", "container_extension", "plot")
        reader.beginArray()
        var count = 0
        while (reader.hasNext()) {
            check(++count <= 1000000)
            val row = JSONObject()
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key !in wanted) { reader.skipValue(); continue }
                when (reader.peek()) {
                    JsonToken.STRING, JsonToken.NUMBER -> {
                        val value = reader.nextString()
                        require(value.length <= 1024 * 1024)
                        row.put(key, value)
                    }
                    JsonToken.BOOLEAN -> row.put(key, reader.nextBoolean().toString())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            consume(row)
        }
        reader.endArray()
        check(reader.peek() == JsonToken.END_DOCUMENT)
    }
    private fun api(l: ProviderLogin, action: String = "", extra: Pair<String, String>? = null): String =
        base(l) + "/player_api.php?username=" + encode(l.username) + "&password=" + encode(l.password) +
            (if (action.isEmpty()) "" else "&action=" + encode(action)) + (extra?.let { "&" + it.first + "=" + encode(it.second) } ?: "")
    private fun base(l: ProviderLogin): String = validatedUrl(l.server, l.allowHttp).toASCIIString().trimEnd('/')
    private fun fetch(url: String, l: ProviderLogin): String {
        val uri = validatedUrl(url, l.allowHttp)
        testFetch?.let { return it(url).also { body -> check(body.toByteArray().size <= MAX_BYTES) } }
        val c = uri.toURL().openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 25000
            c.instanceFollowRedirects = false // Never forward provider credentials via redirects.
            c.setRequestProperty("Accept", "application/json, application/x-mpegURL, text/plain")
            check(c.responseCode in 200..299) { "Provider request failed." }
            check(c.contentLengthLong <= MAX_BYTES) { "Provider response is too large." }
            val deadline = System.nanoTime() + 90_000_000_000L
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    check(System.nanoTime() < deadline) { "Provider request timed out." }
                    val n = input.read(buffer); if (n < 0) break
                    check(out.size().toLong() + n <= MAX_BYTES) { "Provider response is too large." }
                    out.write(buffer, 0, n)
                }
            }
            return out.toString("UTF-8")
        } finally { c.disconnect() }
    }
    private fun episodeKey(l: ProviderLogin, e: MediaEntry): String {
        val bytes = (l.server + "\u0000" + l.username + "\u0000" + l.password + "\u0000" + l.m3uUrl + "\u0000" + e.id).toByteArray()
        return "episodes_" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private fun <T> safe(block: () -> T): T = try { block() } catch (_: UnsupportedOperationException) {
        throw UnsupportedOperationException("M3U playlists do not provide a series episode catalogue.")
    } catch (_: Exception) {
        // No raw parser/network exceptions, causes or URL-bearing messages reach UI/crash reporters.
        throw IllegalStateException("Provider operation failed. Check connection, credentials and HTTP permission.")
    }
    companion object {
        private const val ARRAY_MAX_BYTES = 256L * 1024 * 1024
        private const val MAX_BYTES = 32L * 1024 * 1024
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        private fun extension(value: String): String = value.takeIf { Regex("[A-Za-z0-9]{1,10}").matches(it) } ?: "mp4"
        internal fun validatedUrl(url: String, allowHttp: Boolean): URI {
            val u = try { URI(url) } catch (_: Exception) { throw IllegalArgumentException("Invalid provider URL.") }
            require((u.scheme.equals("https", true) || allowHttp && u.scheme.equals("http", true)) &&
                !u.host.isNullOrEmpty() && u.rawUserInfo == null && u.rawFragment == null && u.port in -1..65535) { "Invalid provider URL or HTTP permission required." }
            return u
        }
        internal fun validateLogin(l: ProviderLogin) {
            if (l.m3uUrl.isNotBlank()) validatedUrl(l.m3uUrl, l.allowHttp)
            else {
                val u = validatedUrl(l.server, l.allowHttp)
                require(u.rawQuery == null) { "Server URL must not contain a query." }
                require(l.username.isNotBlank() && l.password.isNotBlank()) { "Credentials required." }
            }
        }
        internal fun validateAuth(j: JSONObject): JSONObject {
            val user = j.optJSONObject("user_info")
            check(user != null && user.optString("auth") == "1" && user.optString("status").equals("Active", true)) { "Provider authentication rejected." }
            return user
        }
        internal fun parseM3u(text: String, login: ProviderLogin): ProviderSnapshot {
            require(text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) { "Not an M3U playlist." }
            val entries = mutableListOf<MediaEntry>()
            val groups = linkedMapOf<String, MediaCategory>()
            var title: String? = null
            var attrs = emptyMap<String, String>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF:", true)) {
                    // Find the title separator outside quoted attributes, including commas in group names.
                    var quote: Char? = null
                    var separator = -1
                    for (i in line.indices) {
                        val ch = line[i]
                        if (quote != null) { if (ch == quote) quote = null }
                        else if (ch == '"' || ch == '\'') quote = ch
                        else if (ch == ',') { separator = i; break }
                    }
                    require(separator >= 0) { "Malformed M3U entry." }
                    attrs = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""").findAll(line.substring(0, separator))
                        .associate { it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull { v -> v.isNotEmpty() }.orEmpty() }
                    title = line.substring(separator + 1).trim()
                } else if (line.startsWith("#EXTGRP:", true)) {
                    attrs = attrs + ("group-title" to line.substringAfter(':').trim())
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    // Preserve the full URL, including query tokens; never attempt filename reconstruction.
                    val uri = validatedUrl(line, login.allowHttp)
                    val group = attrs["group-title"].orEmpty().ifBlank { "Other" }
                    val kind = if (Regex("(?i).*\\.(mp4|mkv|avi|mov|m4v|webm)$").matches(uri.path.orEmpty())) "movie" else "live"
                    val category = "$kind:$group"
                    groups[category] = MediaCategory(category, group, kind)
                    val id = MessageDigest.getInstance("SHA-256").digest(line.toByteArray()).joinToString("") { "%02x".format(it) }
                    entries += MediaEntry(id, title?.ifBlank { attrs["tvg-name"].orEmpty().ifBlank { "Untitled" } } ?: "Untitled",
                        category, kind, attrs["tvg-logo"].orEmpty(), directUrl = line)
                    title = null; attrs = emptyMap()
                }
            }
            require(entries.isNotEmpty()) { "Playlist has no supported streams." }
            return ProviderSnapshot(login.name, null, entries, groups.values.toList())
        }
    }
}
