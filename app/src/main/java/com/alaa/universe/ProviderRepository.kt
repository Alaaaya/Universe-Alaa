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
data class MediaEntry(val id: String, val name: String, val categoryId: String, val kind: String, val icon: String = "", val extension: String = "mp4", val directUrl: String = "", val season: Int = 0, val plot: String = "", val categoryIds: Set<String> = setOf(categoryId)) {
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
    .put("kind", e.kind).put("icon", e.icon).put("extension", e.extension).put("directUrl", e.directUrl).put("season", e.season).put("plot", e.plot).put("categoryIds", JSONArray(e.categoryIds.toList()))
private fun entryFromJson(j: JSONObject) = MediaEntry(j.getString("id"), j.getString("name"), j.getString("categoryId"), j.getString("kind"),
    j.optString("icon"), j.optString("extension", "mp4"), j.optString("directUrl"), j.optInt("season"), j.optString("plot"), j.optJSONArray("categoryIds")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: setOf(j.getString("categoryId")))

/** Blocking API. Call on an IO worker, never the Android main thread. No network response is logged. */
class ProviderRepository internal constructor(private val vault: CredentialVault, private val testFetch: ((String) -> String)? = null) {
    constructor(context: Context) : this(CredentialVault(context.applicationContext))
    private val stateLock = Any()
    private var initialized = false
    private var manifest: JSONObject? = null
    private var activeLogin: ProviderLogin? = null
    private var activeSnapshot: ProviderSnapshot? = null
    private var generation = 0L
    private var policy: CategoryPolicy? = null
    // Payload-free ownership tombstones prevent refresh from resurrecting shared hidden items.
    private var memberships = linkedMapOf<String,Set<String>>()
    private fun memberKey(e:MediaEntry)=e.kind+":"+e.id
    private val connections = mutableSetOf<HttpURLConnection>()
    private fun invalidate() { ++generation; connections.toList().forEach { it.disconnect() }; connections.clear() }
    internal fun categoryPolicy(): CategoryPolicy? = synchronized(stateLock) { ensureLoaded(); policy }
    internal fun isVisible(entry: MediaEntry): Boolean = synchronized(stateLock) { ensureLoaded(); policy?.allows(entry.kind, entry.categoryIds+memberships[memberKey(entry)].orEmpty()) != false }
    private fun checkTicket(ticket: Long) = synchronized(stateLock) { check(ticket == generation) }
    private fun allowed(kind: String, ids: Collection<String>, ticket: Long): Boolean = synchronized(stateLock) {
        check(ticket == generation); policy?.allows(kind, ids) != false
    }
    /** Serial reconciliation precedes every cache read/publication and invalidates ALL stale jobs. */
    internal fun applyCategoryPolicy(next: CategoryPolicy): Boolean = safe { synchronized(stateLock) {
        ensureLoaded()
        if (policy == next) return@synchronized false
        val old = policy
        val sameScope = old?.scope == next.scope
        val snapshot = if (sameScope || old == null) cached() else null
        invalidate()
        // Remove episodes even when not present in the open screen; payload membership is inherited.
        vault.deletePrefix("episodes_")
        policy = next
        // Durable policy FIRST. A crash cannot restore a more permissive old catalogue.
        vault.write("category-policy", next.toJson().toString())
        if (!sameScope && (old != null || manifest != null)) {
            vault.delete("state"); vault.deletePrefix("catalog_")
            manifest = null; activeSnapshot = null; activeLogin = null
            memberships.clear();vault.delete("category-memberships")
        } else if (snapshot != null && activeLogin != null) {
            val clean = snapshot.copy(entries = snapshot.entries.filter { next.allows(it.kind, it.categoryIds) },
                categories = snapshot.categories.filter { next.allows(it.kind, listOf(it.id)) })
            persist(activeLogin!!, clean, generation)
        }
        true
    } }
    /** Malformed/unknown policy can retain ONLY a known policy for this exact identity/subscription. */
    internal fun hasCompatiblePolicy(device: String, playlist: String, provider: String?): Boolean = synchronized(stateLock) {
        ensureLoaded(); policy?.let { it.device == device && it.playlist == playlist && (provider == null || it.provider == provider) } == true
    }
    private fun ensureLoaded() {
        if (initialized) return
        policy = vault.read("category-policy")?.let { CategoryPolicy.restore(JSONObject(it)) }
        memberships = linkedMapOf()
        vault.read("category-memberships")?.let { raw ->
            val record=JSONObject(raw)
            val scope=record.optJSONArray("scope")
            val expected=policy?.scope.orEmpty()
            if(scope!=null && (0 until scope.length()).map{scope.getString(it)}==expected) {
                val rows=record.getJSONObject("rows")
                for(k in rows.keys()) { val a=rows.getJSONArray(k);memberships[k]=(0 until a.length()).map{a.getString(it)}.toSet() }
            } else vault.delete("category-memberships")
        }
        val state = vault.read("state")?.let { JSONObject(it) }
        val l = state?.getJSONObject("login")
        activeLogin = l?.let { ProviderLogin(it.getString("name"), it.getString("server"), it.optString("username"),
            it.optString("password"), it.optString("m3uUrl"), it.optBoolean("allowHttp")) }
        val previousPolicy=state?.optString("policyKey")?.takeIf { it.isNotBlank() }?.let { CategoryPolicy.restore(JSONObject(it)) }
        if (policy!=null && state!=null && (previousPolicy==null || policy!!.scope!=previousPolicy.scope)) {
            // Recover a crash after a scope-changing policy write but before old subscription removal.
            vault.delete("state");vault.deletePrefix("catalog_");vault.deletePrefix("episodes_")
            activeLogin=null;manifest=null;memberships.clear();vault.delete("category-memberships")
        } else manifest = state
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
        activeSnapshot = activeSnapshot?.let { snap -> policy?.let { p -> snap.copy(
            entries = snap.entries.filter { p.allows(it.kind, it.categoryIds) },
            categories = snap.categories.filter { p.allows(it.kind, listOf(it.id)) }) } ?: snap }
        // Recover an interrupted policy rewrite before exposing any old disk payload.
        if (policy != null && activeSnapshot != null && activeLogin != null && manifest?.optString("policyKey") != policy!!.toJson().toString()) {
            vault.deletePrefix("episodes_")
            persist(activeLogin!!, activeSnapshot!!, generation)
        }
        activeSnapshot
    } }
    fun connect(login: ProviderLogin): ProviderSnapshot = prepareConnect(login).invoke()
    internal fun prepareConnect(login: ProviderLogin, loadAllCategories: Boolean = true): () -> ProviderSnapshot = safe {
        validateLogin(login)
        val normalized = login.copy(name = login.name.trim().ifBlank {
            validatedUrl(login.m3uUrl.ifBlank { login.server }, login.allowHttp).host
        })
        val ticket = synchronized(stateLock) { ensureLoaded(); ++generation }
        val operation: () -> ProviderSnapshot = { safe { loadAndPublish(normalized, ticket, loadAllCategories) } }
        operation
    }
    private fun loadAndPublish(login: ProviderLogin, ticket: Long, loadAllCategories: Boolean = true): ProviderSnapshot {
        val result = if (login.m3uUrl.isNotBlank()) loadM3u(login, ticket) else loadXtream(login, ticket, loadAllCategories)
        persist(login, result, ticket)
        return result
    }
    private fun persist(login: ProviderLogin, result: ProviderSnapshot, ticket: Long) {
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
                .put("policyKey", policy?.toJson()?.toString() ?: "")
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
    }
    fun refresh(loadAllCategories: Boolean = true): ProviderSnapshot = safe {
        val request = synchronized(stateLock) {
            ensureLoaded()
            (activeLogin ?: error("Connect a provider first.")) to ++generation
        }
        loadAndPublish(request.first, request.second, loadAllCategories)
    }
    fun cachedEpisodes(series: MediaEntry): List<MediaEntry> {
        return synchronized(stateLock) {
            ensureLoaded()
            if (!isVisible(series)) return@synchronized emptyList()
            val l = activeLogin ?: return@synchronized emptyList()
            vault.read(episodeKey(l, series))?.let { objects(JSONArray(it)).map { j -> entryFromJson(j) }.filter { isVisible(it) } } ?: emptyList()
        }
    }
    /** Explicit network refresh; cachedEpisodes supplies immediate offline results. */
    fun episodes(series: MediaEntry): List<MediaEntry> = prepareEpisodes(series).invoke()
    internal fun prepareEpisodes(series: MediaEntry): () -> List<MediaEntry> = safe {
        require(series.kind == "series") { "Select a series." }
        val request = synchronized(stateLock) { ensureLoaded(); (activeLogin ?: error("Connect a provider first.")) to generation }
        val l = request.first
        check(allowed(series.kind, series.categoryIds, request.second) && isVisible(series))
        if (l.m3uUrl.isNotBlank()) throw UnsupportedOperationException("M3U playlists do not provide a series episode catalogue.")
        val operation: () -> List<MediaEntry> = { safe {
        checkTicket(request.second)
        val response = JSONObject(fetch(api(l, "get_series_info", "series_id" to series.id), l, request.second))
        val seasons = response.optJSONObject("episodes") ?: error("Provider returned no episode catalogue.")
        val episodes = mutableListOf<MediaEntry>()
        for (key in seasons.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }) {
            val rows = seasons.optJSONArray(key) ?: continue
            for (row in objects(rows)) {
                val info = row.optJSONObject("info")
                episodes += MediaEntry(row.get("id").toString(), row.optString("title", "Episode"), series.categoryId, "episode",
                    info?.optString("movie_image") ?: series.icon, extension(row.optString("container_extension", "mp4")),
                    season = row.optInt("season", key.toIntOrNull() ?: 0), plot = info?.optString("plot") ?: "", categoryIds = series.categoryIds)
            }
        }
        val serialized = JSONArray(episodes.map { entryJson(it) }).toString()
        synchronized(stateLock) {
            check(generation == request.second) { "Provider changed while loading." }
            vault.write(episodeKey(l, series), serialized)
        }
        episodes
        } }
        operation
    }
    fun streamUrl(entry: MediaEntry): String = safe {
        check(isVisible(entry))
        val l = login() ?: error("Connect a provider first.")
        if (entry.directUrl.isNotBlank()) return@safe validatedUrl(entry.directUrl, l.allowHttp).toASCIIString()
        check(l.m3uUrl.isBlank()) { "Playlist stream URL is unavailable." }
        val type = when (entry.kind) { "live" -> "live"; "movie" -> "movie"; "episode" -> "series"; else -> error("Select an episode first.") }
        val ext = if (entry.kind == "live") "ts" else extension(entry.extension)
        base(l) + "/$type/" + encode(l.username) + "/" + encode(l.password) + "/" + encode(entry.id) + "." + ext
    }
    fun clear() = synchronized(stateLock) {
        invalidate(); vault.clear(); memberships.clear(); policy = null; activeLogin = null; activeSnapshot = null; manifest = null; initialized = true
    }

    private fun loadXtream(l: ProviderLogin, ticket: Long, loadAllCategories: Boolean): ProviderSnapshot {
        val user = validateAuth(JSONObject(fetch(api(l), l, ticket)))
        val categories = mutableListOf<MediaCategory>()
        val entries = mutableListOf<MediaEntry>()
        for ((kind, catAction, streamAction) in actions) {
            val visible = mutableListOf<MediaCategory>()
            arrayRows(api(l, catAction), l, ticket) {
                val id = it.get("category_id").toString()
                require(Regex("[0-9]{1,64}").matches(id))
                // Immediately after get_*_categories, before scheduling any item work.
                if (allowed(kind, listOf(id), ticket)) visible += MediaCategory(id, it.optString("category_name", "Other"), kind)
            }
            categories += visible.distinctBy { it.id }
            if (loadAllCategories) for (category in visible.distinctBy { it.id }) entries += loadCategoryEntries(l, category, streamAction, ticket)
        }
        if (!loadAllCategories) {
            // Panel accounts can expose thousands of categories. Publish metadata and one
            // live category promptly; category-open already fetches the rest on demand.
            val previous = synchronized(stateLock) {
                checkTicket(ticket)
                if (activeLogin == l) cached() else null
            }
            val visible = categories.mapTo(HashSet()) { it.kind to it.id }
            entries += previous?.entries.orEmpty().filter {
                (it.kind to it.categoryId) in visible && allowed(it.kind, it.categoryIds, ticket)
            }
            val first = categories.firstOrNull { it.kind == "live" }
            if (first != null) {
                entries.removeAll { it.kind == first.kind && it.categoryId == first.id }
                entries += loadCategoryEntries(l, first, "get_live_streams", ticket)
            }
        }
        val expiry = user.optString("exp_date").toLongOrNull()?.takeIf { it > 0 }
        return ProviderSnapshot(l.name, expiry, mergeMemberships(entries,ticket).filter { allowed(it.kind,it.categoryIds,ticket) }, categories)
    }
    private fun loadCategoryEntries(l: ProviderLogin, category: MediaCategory, action: String, ticket: Long): List<MediaEntry> {
        require(category.id.isNotBlank())
        check(allowed(category.kind, listOf(category.id), ticket))
        val rows = mutableListOf<MediaEntry>()
        arrayRows(api(l, action, "category_id" to category.id), l, ticket) { row ->
            val membership = row.optString("category_id")
            // Providers may ignore category_id. Never accept missing/wrong membership or bulk fallback.
            if (membership != category.id) {
                synchronized(stateLock) { checkTicket(ticket); vault.write("fullcatalog-" + category.kind, "true") }
            } else {
                val ids = row.optJSONArray("category_ids")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: setOf(membership)
                if (membership in ids && allowed(category.kind, ids, ticket)) rows += MediaEntry(
                    row.get(if (category.kind == "series") "series_id" else "stream_id").toString(), row.optString("name", "Untitled"),
                    membership, category.kind, row.optString(if (category.kind == "series") "cover" else "stream_icon"),
                    extension(row.optString("container_extension", "mp4")), plot = row.optString("plot"), categoryIds = ids)
            }
        }
        return rows
    }
    /** Reconcile metadata without fetching newly unhidden content. Fetch it only on category open. */
    internal fun refreshCategories(): ProviderSnapshot = safe {
        val request = synchronized(stateLock) { ensureLoaded(); (activeLogin ?: error("No provider")) to generation }
        val (l,ticket) = request
        val categories = mutableListOf<MediaCategory>()
        for ((kind,catAction,_) in actions) arrayRows(api(l,catAction),l,ticket) {
            val id = it.get("category_id").toString(); require(Regex("[0-9]{1,64}").matches(id))
            if (allowed(kind,listOf(id),ticket)) categories += MediaCategory(id,it.optString("category_name","Other"),kind)
        }
        synchronized(stateLock) {
            checkTicket(ticket)
            val old = cached() ?: ProviderSnapshot(l.name,null,emptyList(),emptyList())
            val next = old.copy(categories=categories.distinctBy { it.kind to it.id })
            persist(l,next,ticket); next
        }
    }
    internal fun loadCategory(kind: String, id: String): ProviderSnapshot = prepareCategoryLoad(kind,id).invoke()
    /** Capture the identity/policy ticket at scheduling, NOT when a queued worker eventually runs. */
    internal fun prepareCategoryLoad(kind: String, id: String): () -> ProviderSnapshot = safe {
        val request = synchronized(stateLock) { ensureLoaded(); (activeLogin ?: error("No provider")) to generation }
        val (l,ticket) = request
        val category = synchronized(stateLock) { cached()?.categories?.firstOrNull { it.kind == kind && it.id == id } } ?: error("Unavailable category")
        check(allowed(kind,listOf(id),ticket))
        val operation: () -> ProviderSnapshot = { safe {
        checkTicket(ticket)
        val entries = loadCategoryEntries(l,category,actions.first { it.first == kind }.third,ticket)
        synchronized(stateLock) {
            checkTicket(ticket)
            val old = cached() ?: error("No catalogue")
            val known=old.entries.associateBy { it.kind to it.id }
            val owned=entries.map { it.copy(categoryIds=it.categoryIds+known[it.kind to it.id]?.categoryIds.orEmpty()) }
            val next = old.copy(entries=mergeMemberships(old.entries.filterNot { it.kind == kind && it.categoryId == id } + owned,ticket)
                .filter { allowed(it.kind,it.categoryIds,ticket) })
            persist(l,next,ticket); next
        }
        } }
        operation
    }
    private fun mergeMemberships(entries: List<MediaEntry>,ticket:Long): List<MediaEntry> = synchronized(stateLock) {
        checkTicket(ticket)
        val merged=linkedMapOf<Pair<String,String>,MediaEntry>()
        for(entry in entries) {
            val key=entry.kind to entry.id
            val previous=merged[key]
            val ids=entry.categoryIds+memberships[memberKey(entry)].orEmpty()+previous?.categoryIds.orEmpty()
            memberships[memberKey(entry)]=ids
            merged[key]=(previous?:entry).copy(categoryIds=ids)
        }
        val saved=JSONObject();memberships.forEach{(k,v)->saved.put(k,JSONArray(v.toList()))}
        vault.write("category-memberships",JSONObject().put("scope",JSONArray(policy?.scope.orEmpty())).put("rows",saved).toString())
        merged.values.toList()
    }
    internal fun fullCatalogOnly(kind: String): Boolean = synchronized(stateLock) { vault.read("fullcatalog-" + kind) == "true" }
    private val actions = listOf(Triple("live", "get_live_categories", "get_live_streams"), Triple("movie", "get_vod_categories", "get_vod_streams"), Triple("series", "get_series_categories", "get_series"))
    /** Streaming arrays retain only one provider object at a time, skipping nested unused fields. */
    private fun arrayRows(url: String, l: ProviderLogin, ticket: Long, consume: (JSONObject) -> Unit) {
        checkTicket(ticket)
        val uri = validatedUrl(url, l.allowHttp)
        val guarded: (JSONObject) -> Unit = { checkTicket(ticket); consume(it) }
        val fake = testFetch
        if (fake != null) {
            JsonReader(java.io.StringReader(fake(url))).use { readRows(it, guarded) }
            return
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            synchronized(stateLock) { checkTicket(ticket); connections.add(connection) }
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
            JsonReader(java.io.InputStreamReader(bounded, Charsets.UTF_8)).use { readRows(it, guarded) }
        } finally { synchronized(stateLock) { connections.remove(connection) }; connection.disconnect() }
    }
    /** Chunk restore does not materialize a JSONArray, even for an individual encrypted chunk. */
    private fun readCachedEntries(reader: JsonReader, consume: (MediaEntry) -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) {
            var id = ""; var name = ""; var category = ""; var kind = ""
            var icon = ""; var extension = "mp4"; var direct = ""; var season = 0; var plot = ""; var memberships: Set<String>? = null
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
                    "categoryIds" -> { val ids = mutableSetOf<String>(); reader.beginArray(); while(reader.hasNext())ids.add(reader.nextString()); reader.endArray(); memberships=ids }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            consume(MediaEntry(id, name, category, kind, icon, extension, direct, season, plot, memberships ?: setOf(category)))
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
                if (key == "category_ids") {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        val ids = JSONArray(); reader.beginArray()
                        while (reader.hasNext()) { require(ids.length() < 10000); ids.put(reader.nextString()) }
                        reader.endArray(); row.put(key,ids)
                    } else reader.skipValue()
                    continue
                }
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
    private fun fetch(url: String, l: ProviderLogin, ticket: Long): String {
        checkTicket(ticket)
        val uri = validatedUrl(url, l.allowHttp)
        testFetch?.let { return it(url).also { body -> checkTicket(ticket); check(body.toByteArray().size <= MAX_BYTES) } }
        val c = uri.toURL().openConnection() as HttpURLConnection
        try {
            synchronized(stateLock) { checkTicket(ticket); connections.add(c) }
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
        } finally { synchronized(stateLock) { connections.remove(c) }; c.disconnect() }
    }
    private fun loadM3u(l: ProviderLogin, ticket: Long): ProviderSnapshot {
        testFetch?.let { return parseM3u(it(l.m3uUrl), l) }
        var url = validatedUrl(l.m3uUrl, l.allowHttp)
        val deadline = System.nanoTime() + 120_000_000_000L
        repeat(6) {
            checkTicket(ticket)
            val c = url.toURL().openConnection() as HttpURLConnection
            synchronized(stateLock) { checkTicket(ticket); connections.add(c) }
            try {
                c.connectTimeout=20000; c.readTimeout=45000; c.instanceFollowRedirects=false
                c.setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, text/plain")
                c.setRequestProperty("User-Agent", "Universe-Alaa/0.4.9")
                val status=c.responseCode
                if(status in listOf(301,302,303,307,308)) {
                    val target=url.resolve(c.getHeaderField("Location") ?: throw ProviderFailure("Playlist redirect has no destination."))
                    // Never forward query credentials cross-origin or downgrade HTTPS.
                    if(url.scheme=="https" && target.scheme!="https") throw ProviderFailure("Playlist redirected to insecure HTTP.")
                    if((url.host!=target.host || url.port!=target.port) && (url.rawQuery!=null || target.rawQuery!=null))
                        throw ProviderFailure("Playlist redirect with private query credentials was blocked.")
                    url=validatedUrl(target.toASCIIString(),l.allowHttp)
                } else {
                    if(status !in 200..299) throw ProviderFailure("Playlist server returned HTTP $status.")
                    if(c.contentLengthLong>MAX_BYTES) throw ProviderFailure("Playlist exceeds the 32 MB limit.")
                    var count=0L
                    val bounded=object:java.io.FilterInputStream(c.inputStream) {
                        override fun read(b:ByteArray,off:Int,len:Int):Int {
                            checkTicket(ticket)
                            if(System.nanoTime()>deadline)throw ProviderFailure("Playlist download timed out.")
                            val n=super.read(b,off,len)
                            if(n>0)count+=n
                            if(count>MAX_BYTES)throw ProviderFailure("Playlist exceeds the 32 MB limit.")
                            return n
                        }
                    }
                    return bounded.bufferedReader(Charsets.UTF_8).use { parseM3uReader(it,l) }
                }
            } finally { synchronized(stateLock){connections.remove(c)};c.disconnect() }
        }
        throw ProviderFailure("Playlist redirected too many times.")
    }
    private class ProviderFailure(message:String):IllegalStateException(message)
    private fun episodeKey(l: ProviderLogin, e: MediaEntry): String {
        val bytes = (l.server + "\u0000" + l.username + "\u0000" + l.password + "\u0000" + l.m3uUrl + "\u0000" + e.id).toByteArray()
        return "episodes_" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private fun <T> safe(block: () -> T): T = try { block() } catch (_: UnsupportedOperationException) {
        throw UnsupportedOperationException("M3U playlists do not provide a series episode catalogue.")
    } catch (e: ProviderFailure) { throw e
    } catch (_: java.net.SocketTimeoutException) { throw ProviderFailure("Playlist server timed out. Try again.")
    } catch (_: java.net.UnknownHostException) { throw ProviderFailure("Server hostname could not be resolved. Check your connection and URL.")
    } catch (_: javax.net.ssl.SSLException) { throw ProviderFailure("The server TLS certificate could not be verified.")
    } catch (_: java.net.ConnectException) { throw ProviderFailure("Could not reach the server. Check your connection.")
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
        internal fun parseM3u(text: String, login: ProviderLogin): ProviderSnapshot =
            text.reader().buffered().use { parseM3uReader(it,login) }
        private fun parseM3uReader(reader: java.io.BufferedReader, login: ProviderLogin): ProviderSnapshot {
            val header=reader.readLine().orEmpty().trimStart('\uFEFF',' ')
            if(!header.startsWith("#EXTM3U")) throw ProviderFailure("The server did not return an M3U playlist.")
            val entries = mutableListOf<MediaEntry>()
            val groups = linkedMapOf<String, MediaCategory>()
            var title: String? = null
            var attrs = emptyMap<String, String>()
            for (raw in reader.lineSequence()) {
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
                    val uri = try { validatedUrl(line, login.allowHttp) } catch (_:IllegalArgumentException) {
                        // One unsupported or HTTP-only stream must not reject a mixed public list.
                        title=null;attrs=emptyMap();continue
                    }
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
            if(entries.isEmpty()) throw ProviderFailure("Playlist has no supported streams. If it contains HTTP-only channels, enable insecure HTTP.")
            return ProviderSnapshot(login.name, null, entries, groups.values.toList())
        }
    }
}
