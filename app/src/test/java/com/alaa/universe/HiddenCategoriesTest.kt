package com.alaa.universe

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HiddenCategoriesTest {
    private val login=ProviderLogin("Fixture","https://provider.invalid","fixture-user","fixture-pass")
    private fun vault(name:String="hidden-test")=CredentialVault(org.robolectric.RuntimeEnvironment.getApplication(),{SecretKeySpec(ByteArray(16){4},"AES")},name)
    private fun playlist(hidden:String="""{"live":["7"],"vod":[],"series":["7"]}""", revision:Char='a')=JSONObject()
        .put("id","00000000-0000-0000-0000-000000000001").put("type","xtream")
        .put("category_settings_version",1).put("category_provider_generation","00000000-0000-0000-0000-000000000002")
        .put("category_config_revision","hc1_"+revision.toString().repeat(64)).put("hidden_category_ids",JSONObject(hidden))
    private fun response(url:String):String=when {
        url.contains("categories") -> """[{"category_id":"7","category_name":"Seven"},{"category_id":"007","category_name":"Leading zeros"}]"""
        url.contains("get_series_info") -> """{"episodes":{"1":[{"id":"ep","title":"Episode"}]}}"""
        url.contains("action=") -> {
            val cat=if(url.contains("category_id=007"))"007" else "7"
            """[{"stream_id":"$cat","series_id":"$cat","category_id":"$cat","name":"Item $cat"}]"""
        }
        else -> """{"user_info":{"auth":1,"status":"Active"}}"""
    }
    @Test fun excludesHiddenRequestsImmediatelyAndKeepsMediaAndExactStringScopes() {
        val v=vault().also{it.clear()};val requests=mutableListOf<String>()
        val repo=ProviderRepository(v){url->requests.add(url);response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("device-A",playlist())!!)
        val snapshot=repo.connect(login)
        assertEquals(setOf("live:007","movie:7","movie:007","series:007"),snapshot.entries.map{"${it.kind}:${it.categoryId}"}.toSet())
        assertEquals(4,requests.count{it.contains("category_id=")})
        assertFalse(requests.any{(it.contains("get_live_streams")||it.contains("action=get_series&"))&&it.endsWith("category_id=7")})
        assertFalse(requests.any{it.contains("action=")&&!it.contains("categories")&&!it.contains("category_id=")})
        assertFalse(snapshot.categories.any{it.id=="7"&&it.kind!="movie"})
    }

    @Test fun allHiddenMeansZeroStreamRequestsAndUnknownIdsAreHarmless() {
        val v=vault().also{it.clear()};val urls=mutableListOf<String>()
        val repo=ProviderRepository(v){url->urls.add(url);response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":["7","007","999"],"vod":["7","007"],"series":["7","007"]}"""))!!)
        assertTrue(repo.connect(login).entries.isEmpty())
        assertEquals(4,urls.size)
    }
    @Test fun policyPurgesEncryptedChunksEpisodesAndBlocksStalePlaybackOnRestart() {
        val v=vault().also{it.clear()}
        val repo=ProviderRepository(v,::response)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        val old=repo.connect(login)
        val series=old.entries.first{it.kind=="series"&&it.categoryId=="7"}
        assertEquals(1,repo.episodes(series).size)
        val oldChunks=JSONObject(v.read("state")!!).getJSONArray("chunks")
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist(revision='b'))!!)
        assertTrue(repo.cachedEpisodes(series).isEmpty())
        assertTrue(ProviderRepository(v).cached()!!.entries.none{it.categoryId=="7"&&it.kind!="movie"})
        for(i in 0 until oldChunks.length()) assertNull(v.read(oldChunks.getJSONObject(i).getString("file")))
        val state=JSONObject(v.read("state")!!)
        val payloads=state.getJSONArray("chunks")
        for(i in 0 until payloads.length()) {
            val payload=v.read(payloads.getJSONObject(i).getString("file"))!!
            val rows=org.json.JSONArray(payload)
            for(j in 0 until rows.length()) {
                val row=rows.getJSONObject(j)
                assertFalse(row.getString("categoryId")=="7" && row.getString("kind")!="movie")
            }
        }
        try { repo.streamUrl(series); fail("Hidden playback") } catch(_:IllegalStateException){}
        try { repo.episodes(series); fail("Hidden series detail") } catch(_:IllegalStateException){}
    }
    @Test fun unhideRefreshesMetadataOnlyThenFetchesRequestedCategoryLazily() {
        val v=vault().also{it.clear()};val urls=mutableListOf<String>()
        val repo=ProviderRepository(v){url->urls.add(url);response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!);repo.connect(login)
        urls.clear()
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}""",'b'))!!)
        assertTrue(urls.isEmpty())
        repo.refreshCategories()
        assertEquals(3,urls.size);assertTrue(urls.all{it.contains("categories")})
        assertFalse(repo.cached()!!.entries.any{it.kind=="live"&&it.categoryId=="7"})
        urls.clear();val loaded=repo.loadCategory("live","7")
        assertEquals(1,urls.size);assertTrue(urls.single().endsWith("category_id=7"))
        assertTrue(loaded.entries.any{it.kind=="live"&&it.categoryId=="7"})
    }
    @Test fun devicePlaylistAndProviderChangesDoNotReuseAnotherScopeCache() {
        val v=vault().also{it.clear()};val repo=ProviderRepository(v,::response)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!);repo.connect(login)
        assertFalse(repo.hasCompatiblePolicy("B",playlist().getString("id"),null))
        repo.applyCategoryPolicy(CategoryPolicy.parse("B",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        assertNull(repo.cached());assertNull(repo.login())
        assertTrue(repo.connect(login).entries.any{it.kind=="live"&&it.categoryId=="7"})
        val other=playlist().put("id","00000000-0000-0000-0000-000000000099")
        repo.applyCategoryPolicy(CategoryPolicy.parse("B",other)!!)
        assertNull(repo.cached())
    }
    @Test fun legacyMalformedAndM3uSemanticsNeverInventEmptyV1() {
        assertNotNull(CategoryPolicy.parse("A",JSONObject().put("id","p").put("type","xtream")))
        assertNull(CategoryPolicy.parse("A",playlist().put("category_settings_version",2)))
        assertNull(CategoryPolicy.parse("A",playlist().put("hidden_category_ids",JSONObject("""{"live":[7],"vod":[],"series":[]}"""))))
        assertNull(CategoryPolicy.parse("A",playlist().put("hidden_category_ids",JSONObject("""{"live":[],"vod":[]}"""))))
        assertNull(CategoryPolicy.parse("A",playlist().put("category_config_revision","bad")))
        assertFalse(CategoryPolicy.parse("A",playlist().put("type","m3u"))!!.hasHides)
    }
    @Test fun providerIgnoringCategoryFilterIsMarkedAndNeverPersistsHiddenOrUnknownMembership() {
        val v=vault().also{it.clear()}
        val repo=ProviderRepository(v){url->if(url.contains("categories"))response(url) else if(url.contains("action="))
            """[{"stream_id":"h","series_id":"h","category_id":"7","name":"HIDDEN"},{"stream_id":"v","series_id":"v","category_id":"007","name":"Visible"},{"stream_id":"u","series_id":"u","name":"UNKNOWN"}]""" else response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!)
        val snapshot=repo.connect(login)
        assertTrue(repo.fullCatalogOnly("live"))
        assertFalse(snapshot.entries.any{it.kind!="movie"&&it.name=="HIDDEN"})
        assertFalse(snapshot.entries.any{it.name=="UNKNOWN"})
        assertEquals(snapshot,ProviderRepository(v).cached())
    }
    @Test fun changedPolicyCancelsInFlightCategoryAndRejectsLatePublication() {
        val v=vault().also{it.clear()}
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        var block=false
        val repo=ProviderRepository(v){url->
            if(block&&url.contains("get_live_streams")){entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
            response(url)
        }
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        repo.connect(login);block=true
        val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val job=pool.submit<Boolean>{try{repo.refresh();false}catch(_:IllegalStateException){true}}
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS))
            repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist(revision='b'))!!)
            release.countDown();assertTrue(job.get(3,java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(repo.cached()!!.entries.any{it.kind=="live"&&it.categoryId=="7"})
        } finally { release.countDown();pool.shutdownNow() }
    }

    @Test fun failedCategoryRequestNeverFallsBackToFullcatalogAndKeepsFilteredCache() {
        val v=vault().also{it.clear()};val urls=mutableListOf<String>();var fail=false
        val repo=ProviderRepository(v){url->urls.add(url);if(fail&&url.contains("get_live_streams"))throw java.io.IOException();response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!);val original=repo.connect(login)
        urls.clear();fail=true
        try{repo.refresh();fail("Expected unavailable")}catch(_:IllegalStateException){}
        assertEquals(original,repo.cached())
        assertTrue(urls.filter{it.contains("get_live_streams")}.all{it.contains("category_id=007")})
    }
    @Test fun interruptedPolicyCommitIsReconciledBeforeCacheFirstExposure() {
        val v=vault().also{it.clear()};val repo=ProviderRepository(v,::response)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        repo.connect(login)
        val old=JSONObject(v.read("state")!!).getJSONArray("chunks").getJSONObject(0).getString("file")
        // Crash after durable policy commit but before catalogue rewrite.
        v.write("category-policy",CategoryPolicy.parse("A",playlist())!!.toJson().toString())
        val reopened=ProviderRepository(v)
        assertFalse(reopened.cached()!!.entries.any{it.kind=="live"&&it.categoryId=="7"})
        assertNull(v.read(old))
    }
    @Test fun sharedMembershipAndUnknownMembershipCannotBypassHides() {
        val v=vault().also{it.clear()}
        val repo=ProviderRepository(v){url->if(url.contains("categories"))response(url) else if(url.contains("action="))
            """[{"stream_id":"shared","series_id":"shared","category_id":"007","category_ids":["007","7"],"name":"Shared"}]""" else response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!)
        assertTrue(repo.connect(login).entries.none{it.kind!="movie"})
        assertFalse(repo.isVisible(MediaEntry("u","Unknown","","live")))
    }
    @Test fun hiddenDuringSeriesInfoFetchCannotRefillEpisodeCache() {
        val v=vault().also{it.clear()};val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val repo=ProviderRepository(v){url->if(url.contains("get_series_info")){entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))};response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        val series=repo.connect(login).entries.first{it.kind=="series"&&it.categoryId=="7"}
        val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
        try{
            val job=pool.submit<Boolean>{try{repo.episodes(series);false}catch(_:IllegalStateException){true}}
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS))
            repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!)
            release.countDown();assertTrue(job.get(3,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(ProviderRepository(v).cachedEpisodes(series).isEmpty())
        }finally{release.countDown();pool.shutdownNow()}
    }

    @Test fun duplicateRowsMergeKnownMembershipBeforeAnyLaterHide() {
        val v=vault().also{it.clear()}
        val repo=ProviderRepository(v){url->if(url.contains("action=")&&!url.contains("categories")&&!url.contains("get_series_info"))
            response(url).replace("\"stream_id\":\"007\"","\"stream_id\":\"7\"").replace("\"series_id\":\"007\"","\"series_id\":\"7\"") else response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        val shared=repo.connect(login).entries.first{it.kind=="live"}
        assertEquals(setOf("7","007"),shared.categoryIds)
        repo.loadCategory("live","7")
        assertEquals(setOf("7","007"),repo.cached()!!.entries.first{it.kind=="live"}.categoryIds)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":["007"],"vod":[],"series":[]}""",'b'))!!)
        assertTrue(repo.cached()!!.entries.none{it.kind=="live"})
        assertFalse(repo.isVisible(shared))
        assertTrue(repo.refresh().entries.none{it.kind=="live"})
        val reopened=ProviderRepository(v){url->if(url.contains("action=")&&!url.contains("categories"))response(url).replace("\"stream_id\":\"007\"","\"stream_id\":\"7\"") else response(url)}
        assertTrue(reopened.refresh().entries.none{it.kind=="live"})
    }
    @Test fun interruptedDifferentScopePolicyCannotAdoptPreviousCredentialsOrCatalog() {
        val v=vault().also{it.clear()};val repo=ProviderRepository(v,::response)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!);repo.connect(login)
        v.write("category-policy",CategoryPolicy.parse("B",playlist("""{"live":[],"vod":[],"series":[]}"""))!!.toJson().toString())
        val reopened=ProviderRepository(v)
        assertNull(reopened.login());assertNull(reopened.cached());assertNull(v.read("state"))
    }
    @Test fun queuedCategoryAndEpisodeWorkCannotAdoptNewPolicyGeneration() {
        val v=vault().also{it.clear()};val urls=mutableListOf<String>()
        val repo=ProviderRepository(v){url->urls.add(url);response(url)}
        val empty="""{"live":[],"vod":[],"series":[]}"""
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist(empty))!!)
        val series=repo.connect(login).entries.first{it.kind=="series"}
        val pending=repo.prepareCategoryLoad("live","7")
        val pendingEpisode=repo.prepareEpisodes(series)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!)
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist(empty,'b'))!!)
        urls.clear()
        try{pending();fail("Stale queued category")}catch(_:IllegalStateException){}
        try{pendingEpisode();fail("Stale queued episode")}catch(_:IllegalStateException){}
        assertTrue(urls.isEmpty())
    }

    @Test fun interruptedScopeCleanupCannotAdoptOldMembershipTombstonesWithoutState() {
        val v=vault().also{it.clear()}
        val repo=ProviderRepository(v){url->if(url.contains("action=")&&!url.contains("categories"))response(url).replace("\"stream_id\":\"007\"","\"stream_id\":\"7\"") else response(url)}
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}"""))!!)
        repo.connect(login)
        assertNotNull(v.read("category-memberships"))
        v.write("category-policy",CategoryPolicy.parse("B",playlist("""{"live":["007"],"vod":[],"series":[]}"""))!!.toJson().toString())
        v.delete("state")
        val reopened=ProviderRepository(v,::response)
        assertTrue(reopened.connect(login).entries.any{it.kind=="live"&&it.categoryId=="7"})
    }
    @Test fun boundedPanelStartupNeverFetchesHiddenAndLazyUnhideDoesNotLeak() {
        val v=vault().also { it.clear() };val requests=mutableListOf<String>()
        val repo=ProviderRepository(v) { url -> requests+=url;response(url) }
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist())!!)
        val snapshot=repo.prepareConnect(login,loadAllCategories=false)()
        assertEquals(listOf("live:007"),snapshot.entries.map { it.kind+":"+it.categoryId })
        assertEquals(5,requests.size)
        assertFalse(requests.any { it.contains("category_id=7") })
        repo.loadCategory("movie","7")
        requests.clear()
        repo.applyCategoryPolicy(CategoryPolicy.parse("A",playlist("""{"live":[],"vod":[],"series":[]}""",'b'))!!)
        val metadata=repo.refreshCategories()
        assertEquals(3,requests.size)
        assertFalse(metadata.entries.any { it.kind=="live" && it.categoryId=="7" })
        requests.clear()
        val loaded=repo.loadCategory("live","7")
        assertEquals(1,requests.size)
        assertTrue(loaded.entries.any { it.kind=="live" && it.categoryId=="7" })
        repo.clear()
    }

}
