package com.alaa.universe

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProviderRepositoryTest {
    @Test fun livePublicHttpsM3uStreamsAndPersistsWithoutCredentials() {
        val repo=ProviderRepository(fixtureVault())
        val result=repo.connect(ProviderLogin("Public live test","",m3uUrl="https://iptv-org.github.io/iptv/index.m3u"))
        assertTrue("Expected thousands of supported HTTPS channels",result.entries.size>1000)
        assertTrue(result.entries.all { it.directUrl.startsWith("https://") })
        assertEquals(result.entries.size,repo.cached()!!.entries.size)
        java.io.File("/home/kit/universe-Alaa/artifacts/live-public-049.txt").writeText("Real HTTPS connect and encrypted cache roundtrip succeeded; supported HTTPS entries="+result.entries.size)
    }
    @Test fun mixedM3uSkipsUnsupportedInsteadOfRejectingWholeList() {
        val text="#EXTM3U\n#EXTINF:-1,HTTP\nhttp://example.test/live\n#EXTINF:-1,Bad\nudp://224.0.0.1:1234\n#EXTINF:-1,HTTPS\nhttps://example.test/live"
        val clean=ProviderRepository.parseM3u(text,ProviderLogin("Mixed","",m3uUrl="https://example.test/list"))
        assertEquals(listOf("HTTPS"),clean.entries.map{it.name})
        val permitted=ProviderRepository.parseM3u(text,ProviderLogin("Mixed","",m3uUrl="https://example.test/list",allowHttp=true))
        assertEquals(2,permitted.entries.size)
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    @Test fun expiryAndRoundTrip() {
        val s = ProviderSnapshot("Fixture", 200000L, listOf(MediaEntry("7", "A", "2", "movie")), listOf(MediaCategory("2", "Movies", "movie")), 123L)
        assertEquals(1L, s.daysLeft(199999000))
        assertEquals(0L, s.daysLeft(200001000))
        assertEquals(s, ProviderSnapshot.fromJson(s.toJson()))
        assertNull(s.copy(expires = null).daysLeft())
    }
    @Test fun authRejectsInvalidAndExpired() {
        for (json in listOf("{}", """{"user_info":{"auth":0,"status":"Active"}}""", """{"user_info":{"auth":1,"status":"Expired"}}""")) {
            rejects { ProviderRepository.validateAuth(JSONObject(json)) }
        }
        assertEquals("Active", ProviderRepository.validateAuth(JSONObject("""{"user_info":{"auth":1,"status":"Active"}}""")).getString("status"))
    }
    @Test fun rejectsUnsafeUrlsAndRequiresHttpOptIn() {
        for (url in listOf("file:///tmp/a", "https://user:pass@example.test/a", "https://example.test/a#token", "http://example.test/a", "javascript:alert(1)")) {
            rejects { ProviderRepository.validatedUrl(url, false) }
        }
        assertEquals("http", ProviderRepository.validatedUrl("http://example.test:8080/a", true).scheme)
        rejects { ProviderRepository.validateLogin(ProviderLogin("x", "https://example.test?bad=1", "u", "p")) }
        assertFalse(ProviderLogin("x", "https://example.test", "private-user", "private-pass").toString().contains("private"))
    }
    @Test fun quotedM3uPreservesCommasGroupsAndQuery() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="A" tvg-logo="https://example.test/logo.png" group-title="News, world",Channel, One
            https://example.test/live.ts?token=fake&x=a%2Fb
            #EXTINF:-1 group-title='Cinema' tvg-name="Film",Film name
            https://example.test/a.mp4?token=fake
        """.trimIndent()
        val s = ProviderRepository.parseM3u(text, ProviderLogin("Fixture", "", m3uUrl = "https://example.test/list"))
        assertEquals(2, s.entries.size)
        assertEquals("Channel, One", s.entries[0].name)
        assertEquals("News, world", s.categories[0].name)
        assertEquals("https://example.test/live.ts?token=fake&x=a%2Fb", s.entries[0].directUrl)
        assertEquals("movie", s.entries[1].kind)
        assertNull(s.expires)
    }
    @Test fun m3uNeverFakesSeriesAndRejectsUnsafeStreams() {
        val l = ProviderLogin("Fixture", "", m3uUrl = "https://example.test/list")
        val s = ProviderRepository.parseM3u("#EXTM3U\n#EXTINF:-1 group-title=\"Series\",Episode 1\nhttps://example.test/episode.mkv", l)
        assertTrue(s.entries.none { it.kind == "series" || it.kind == "episode" })
        rejects { ProviderRepository.parseM3u("#EXTM3U\n#EXTINF:-1,X\nfile:///secret", l) }
        rejects { ProviderRepository.parseM3u("<html>not a playlist</html>", l) }
        rejects { ProviderRepository.parseM3u("#EXTM3U", l) }
    }

    private fun fixtureVault(): CredentialVault {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return CredentialVault(context) { key }
    }
    @Test fun encryptedNetworkFixtureRoundTripAndTampering() {
        val vault = fixtureVault().also { it.clear() }
        var requests = 0
        val repo = ProviderRepository(vault) { url ->
            requests++
            when {
                url.contains("action=get_series_info") -> """{"episodes":{"2":[{"id":"21","title":"Second","season":2}],"1":[{"id":"11","title":"First","season":1}]}}"""
                url.contains("categories") -> """[{"category_id":"7","category_name":"Fixture"}]"""
                url.contains("get_series") -> """[{"series_id":"9","name":"Series","category_id":"7"}]"""
                url.contains("action=") -> """[{"stream_id":"8","name":"Channel","category_id":"7"}]"""
                else -> """{"user_info":{"auth":1,"status":"Active","exp_date":"200000"}}"""
            }
        }
        val snapshot = repo.connect(ProviderLogin("", "https://example.test", "fixture-user", "fixture-password"))
        assertEquals("example.test", snapshot.name)
        assertEquals(7, requests)
        val disk = java.io.File(org.robolectric.RuntimeEnvironment.getApplication().noBackupFilesDir, "provider-v1/state")
        assertFalse(disk.readBytes().toString(Charsets.ISO_8859_1).contains("fixture-password"))
        val reopened = ProviderRepository(vault)
        assertEquals(snapshot, reopened.cached())
        assertEquals("fixture-user", reopened.login()!!.username)
        repeat(100) { assertTrue(repo.streamUrl(snapshot.entries.first()).contains("/live/")) }
        assertEquals(7, requests)
        repo.refresh()
        assertEquals(14, requests)
        val series = snapshot.entries.first { it.kind == "series" }
        assertEquals(listOf(1, 2), repo.episodes(series).map { it.season })
        assertEquals(2, reopened.cachedEpisodes(series).size)
        val encrypted = disk.readBytes()
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        disk.writeBytes(encrypted)
        rejects { ProviderRepository(vault).cached() }
        repo.clear()
        assertNull(repo.login())
    }
    @Test fun refreshDoesNotBlockZappingAndClearCancelsPublication() {
        val vault = fixtureVault().also { it.clear() }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        var block = false
        val repo = ProviderRepository(vault) {
            if (block) { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            "#EXTM3U\n#EXTINF:-1,Fixture\nhttps://example.test/stream?token=fake"
        }
        val entry = repo.connect(ProviderLogin("Chosen Name", "", m3uUrl = "https://example.test/list")).entries.first()
        block = true
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val refresh = executor.submit<Boolean> { try { repo.refresh(); false } catch (_: IllegalStateException) { true } }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val zap = executor.submit<String> { repo.streamUrl(entry) }
            assertEquals(entry.directUrl, zap.get(1, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("Chosen Name", repo.cached()!!.name)
            assertFalse(entry.toString().contains("token"))
            repo.clear()
            release.countDown()
            assertTrue(refresh.get(3, java.util.concurrent.TimeUnit.SECONDS))
            assertNull(repo.cached())
            assertNull(ProviderRepository(vault).login())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun vaultUsesRandomNonceAndAuthenticatesFileIdentity() {
        val vault = fixtureVault().also { it.clear() }
        val directory = java.io.File(org.robolectric.RuntimeEnvironment.getApplication().noBackupFilesDir, "provider-v1")
        vault.write("sample", "private fixture")
        val first = java.io.File(directory, "sample").readBytes()
        assertEquals("private fixture", vault.read("sample"))
        vault.write("sample", "private fixture")
        val second = java.io.File(directory, "sample").readBytes()
        assertFalse(first.contentEquals(second))
        java.io.File(directory, "swapped").writeBytes(second)
        rejects { vault.read("swapped") }
        vault.clear()
    }
    @Test fun rejectedConnectionKeepsPreviouslySavedAccount() {
        val vault = fixtureVault().also { it.clear() }
        ProviderRepository(vault) { "#EXTM3U\n#EXTINF:-1,Fixture\nhttps://example.test/live" }
            .connect(ProviderLogin("My List", "", m3uUrl = "https://example.test/list"))
        val rejected = ProviderRepository(vault) { """{"user_info":{"auth":0,"status":"Disabled"}}""" }
        rejects { rejected.connect(ProviderLogin("Rejected", "https://example.test", "fake", "fake")) }
        assertEquals("My List", rejected.login()!!.name)
        assertEquals("My List", rejected.cached()!!.name)
    }

    @Test fun streamedLargeCatalogueUsesEncryptedChunksAndLazyLogin() {
        val vault = fixtureVault().also { it.clear() }
        val catalogue = org.json.JSONArray()
        repeat(18000) { i ->
            catalogue.put(JSONObject().put("stream_id", i).put("name", "Fixture " + "x".repeat(220))
                .put("category_id", "7").put("unused_nested", JSONObject().put("ignored", "value")))
        }
        val body = catalogue.toString()
        val repo = ProviderRepository(vault) { url ->
            when {
                url.contains("get_live_streams") -> body
                url.contains("get_live_categories") -> """[{"category_id":"7","category_name":"Large fixture"}]"""
                url.contains("action=") -> "[]"
                else -> """{"user_info":{"auth":1,"status":"Active"}}"""
            }
        }
        val snapshot = repo.connect(ProviderLogin("Large Fixture", "https://example.test", "fake", "fake"))
        assertEquals(18000, snapshot.entries.size)
        val state = JSONObject(vault.read("state")!!)
        assertFalse(state.has("snapshot"))
        assertTrue(state.getJSONArray("chunks").length() > 1)
        val chunkNames = state.getJSONArray("chunks")
        val directory = java.io.File(org.robolectric.RuntimeEnvironment.getApplication().noBackupFilesDir, "provider-v1")
        for (i in 0 until chunkNames.length()) {
            val filename = chunkNames.getJSONObject(i).getString("file")
            assertTrue(java.io.File(directory, filename).length() <= 2L * 1024 * 1024)
        }
        assertEquals(snapshot, ProviderRepository(vault).cached())
        val firstChunk = chunkNames.getJSONObject(0).getString("file")
        val reopened = ProviderRepository(vault)
        // A broken catalogue must not make credential reads or zapping deserialize the catalogue.
        vault.delete(firstChunk)
        assertEquals("fake", reopened.login()!!.username)
        assertTrue(reopened.streamUrl(snapshot.entries.first()).contains("/live/"))
        rejects { reopened.cached() }
        repo.clear()
    }

    @Test fun streamsActualAccountScaleWithoutBuildingWholeJsonResponse() {
        val vault = fixtureVault().also { it.clear() }
        val repository = ProviderRepository(vault)
        val total = 290851
        // Generated incrementally, exceeds the real 88,782,114-byte response with no giant String.
        val row = """{"stream_id":"8","name":"Fixture","category_id":"7","unused":{"padding":"PLACEHOLDER"}}"""
            .replace("PLACEHOLDER", "x".repeat(280))
        var nextRow = 0
        var pending = "["
        var offset = 0
        var emitted = 0L
        val source = object : java.io.Reader() {
            override fun read(buffer: CharArray, start: Int, length: Int): Int {
                if (length == 0) return 0
                if (offset == pending.length) {
                    if (nextRow < total) {
                        pending = (if (nextRow++ == 0) "" else ",") + row
                    } else if (nextRow++ == total) pending = "]" else return -1
                    offset = 0
                }
                val n = minOf(length, pending.length - offset)
                pending.toCharArray(buffer, start, offset, offset + n)
                offset += n; emitted += n
                return n
            }
            override fun close() {}
        }
        var parsed = 0
        android.util.JsonReader(source).use { reader ->
            repository.readRows(reader) { objectRow ->
                parsed++
                assertFalse(objectRow.has("unused"))
            }
        }
        assertEquals(total, parsed)
        assertTrue(emitted > 88782114L)
        vault.clear()
    }
    @Test fun panelStartupBoundsThousandsOfCategoriesAndRetainsLazyCacheOnRefresh() {
        val vault = fixtureVault().also { it.clear() }
        val requests = mutableListOf<String>()
        val categories = (1..900).joinToString(",", "[", "]") {
            """{"category_id":"$it","category_name":"Fixture"}"""
        }
        val repo = ProviderRepository(vault) { url ->
            requests += url
            when {
                url.contains("categories") -> categories
                url.contains("action=") -> {
                    val id = url.substringAfterLast("category_id=")
                    """[{"stream_id":$id,"series_id":$id,"category_id":"$id","category_ids":[$id],"name":"Fixture"}]"""
                }
                else -> """{"user_info":{"auth":1,"status":"Active"}}"""
            }
        }
        val login = ProviderLogin("Panel","https://example.test","u","p")
        val snapshot = repo.prepareConnect(login,loadAllCategories=false)()
        assertEquals(2700,snapshot.categories.size)
        assertEquals(5,requests.size) // auth, three metadata requests, first live category
        assertEquals(1,snapshot.entries.size)
        assertEquals(setOf("1"),snapshot.entries.single().categoryIds)
        repo.loadCategory("movie","2")
        repo.loadCategory("series","3")
        requests.clear()
        val refreshed=repo.refresh(loadAllCategories=false)
        assertEquals(5,requests.size)
        assertEquals(setOf("live","movie","series"),refreshed.entries.map { it.kind }.toSet())
        assertEquals(refreshed,ProviderRepository(vault).cached())
        repo.clear()
    }

}
