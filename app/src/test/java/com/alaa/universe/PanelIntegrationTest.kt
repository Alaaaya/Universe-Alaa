package com.alaa.universe

import android.content.Context
import com.alaa.universe.panel.PanelClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PanelIntegrationTest {
    private class Memory : PanelClient.Store { var value: String? = null
        override fun read() = value
        override fun write(s: String) { value = s } }
    private lateinit var context: Context
    private lateinit var store: Memory
    private lateinit var repo: ProviderRepository
    private var wall = 1_000_000_000L
    private var tick = 5_000L
    private var status = "pending"
    private var expires: Any = JSONObject.NULL
    private var password = "fixture-password"
    private var calls = 0
    private var offline=false
    private var categorySettings:JSONObject?=null
    private var providerCalls=0
    private val queue = ArrayDeque<Runnable>()
    private var queued = false
    private var stoppedCount = 0
    private val published = mutableListOf<String?>()
    private val direct = Executor { it.run() }
    private val deferred = Executor { if (queued) queue.addLast(it) else it.run() }
    private fun vault() = CredentialVault(context) { SecretKeySpec(ByteArray(16) { 7 }, "AES") }
    private val wire = PanelClient.Transport { fn, req ->
        calls++
        if(offline)throw java.io.IOException()
        if (fn == "register_device_v2") return@Transport JSONObject().put("status", "registered")
        val r = JSONObject().put("status", status)
        if (status == "active") r.put("device_code", req.getString("p_device_code")).put("app_type", "universe").put("expires_at", expires)
            .put("playlists", JSONArray().put(JSONObject().put("id", "p1").put("name", "Sub").put("type", "xtream").put("url", JSONObject.NULL)
                .put("epg_url", JSONObject.NULL).put("server", "https://provider.invalid").put("username", "fixture-user").put("password", password)))
        if(status=="active")categorySettings?.let { settings ->
            val p=r.getJSONArray("playlists").getJSONObject(0)
            settings.keys().forEach { key -> p.put(key,settings.get(key)) }
        }
        r
    }
    @Before fun setup() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        store = Memory()
        val fetch: (String) -> String = { url -> providerCalls++; if (url.contains("action=")) "[]" else """{"user_info":{"auth":1,"status":"Active","exp_date":"4102444800"}}""" }
        repo = ProviderRepository(vault(), fetch)
    }
    private fun integration(): PanelIntegration = PanelIntegration(store, wire, repo, deferred, direct, { wall }, { tick },
        { s, _ -> published.add(s?.name) }, { stoppedCount++ })
    @Test fun exactOwnerPanelM3uRowLoadsRealHttpsCatalogue() {
        val row=JSONObject(javaClass.getResource("/owner-public-m3u.json")!!.readText())
        val liveRepo=ProviderRepository(vault())
        val transport=PanelClient.Transport { fn,req ->
            if(fn=="register_device_v2") JSONObject().put("status","registered")
            else JSONObject().put("status","active").put("device_code",req.getString("p_device_code"))
                .put("app_type","universe").put("expires_at",JSONObject.NULL)
                .put("playlists",JSONArray().put(row))
        }
        val integration=PanelIntegration(store,transport,liveRepo,direct,direct,{wall},{tick},{s,_ -> published.add(s?.name)},{})
        integration.open()
        assertEquals("Active",integration.status)
        assertEquals(row.getString("url"),integration.configuredLogin!!.m3uUrl)
        assertTrue(liveRepo.cached()!!.entries.size>1000)
        java.io.File("/home/kit/universe-Alaa/artifacts/panel-live-049.txt").writeText("Read-only exact owner playlist row; simulated authenticated panel transport; real HTTPS catalogue; Active; channels="+liveRepo.cached()!!.entries.size)
    }
    @Test fun pendingShowsCodeOnlyAndNoProvider() {
        val p = integration(); p.open()
        assertTrue(p.code.matches(Regex("[A-F0-9]{12}")))
        assertEquals("Waiting for activation", p.status)
        assertNull(repo.login()); assertFalse(p.ownsProvider)
        assertFalse(p.status.contains(JSONObject(store.value!!).getString("token")))
    }
    @Test fun activeConnectsCatalogAndDenialRemovesIt() {
        status = "active"; val p = integration(); p.open()
        assertTrue(p.ownsProvider); assertEquals("Sub", repo.cached()?.name); assertEquals("fixture-user", repo.login()?.username)
        status = "inactive"; wall += 61_000; tick += 61_000; p.open()
        assertFalse(p.ownsProvider); assertNull(repo.login()); assertNull(repo.cached()); assertTrue(stoppedCount >= 1)
        assertNull(JSONObject(store.value!!).optJSONObject("cache"))
    }
    @Test fun changedConfigReplacesAndRemovalClears() {
        status = "active"; val p = integration(); p.open()
        password = "second-password"; wall += 61_000; tick += 61_000; p.open()
        assertEquals("second-password", repo.login()?.password)
        status = "unauthorized"; wall += 61_000; tick += 61_000; p.open()
        assertNull(repo.login())
    }
    @Test fun staleAsyncLoadCannotRepublishAfterDenial() {
        status = "active"; queued = true; val p = integration(); p.open(); queue.removeFirst().run() // registration/config
        assertEquals(1, queue.size) // catalogue load is waiting
        status = "inactive"; wall += 61_000; tick += 61_000; queue.addLast(Runnable { })
        queued = false; p.open()
        published.clear(); while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNull(repo.login()); assertNull(repo.cached()); assertFalse(published.any { it != null })
    }
    @Test fun offlineLeaseAllowsRestartWithin24hAndNotAfter() {
        status = "active"; integration().open(); repo.clear()
        val failing = PanelClient.Transport { _, _ -> throw java.io.IOException() }
        wall += 3_600_000; tick += 3_600_000
        val offline = PanelIntegration(store, failing, repo, direct, direct, { wall }, { tick }, { _, _ -> }, { stoppedCount++ })
        offline.open(); assertTrue(offline.ownsProvider)
        wall += 24 * 3_600_000L; tick += 24 * 3_600_000L; offline.tick()
        assertFalse(offline.ownsProvider); assertNull(repo.login())
        val later = PanelIntegration(store, failing, repo, direct, direct, { wall }, { tick }, { _, _ -> }, { stoppedCount++ })
        later.open(); assertFalse(later.ownsProvider)
    }
    @Test fun expiryEnforcedDuringRunningPlaybackAndClockRollbackDoesNotExtend() {
        expires = "1970-01-12T13:46:50+00:00" // epoch 1_000_010s, ten seconds after wall
        status = "active"; val p = integration(); p.open(); assertTrue(p.tick())
        tick += 11_000; // wall clock changed backwards must not extend monotonic lease
        wall -= 100_000
        assertFalse("tick", p.tick())
        assertTrue("stopped", stoppedCount >= 1)
        assertNull("login", repo.login())
    }

    @Test fun cachedReopenAfterMonotonicDeadlineCannotRenewAndFreshCanRecover() {
        status="active"; val p=integration();p.open();offline=true
        tick+=86_400_001L
        wall+=1_000 // cache still looks young to the wall clock
        p.open()
        assertFalse(p.ownsProvider);assertNull(repo.login())
        p.open();assertFalse(p.tick())
        offline=false;wall+=3_600_001;tick+=3_600_001;p.open()
        assertTrue(p.tick());assertNotNull(repo.login())
    }
    @Test fun rollbackObservedOnCachedReopenCannotRenew() {
        status="active";val p=integration();p.open();offline=true
        wall+=60_000;tick+=60_000;assertTrue(p.tick())
        wall-=10_000;tick+=1_000
        p.open()
        assertFalse(p.tick());assertNull(repo.login())
        wall+=20_000;p.open();assertFalse(p.ownsProvider)
    }
    @Test fun eachTickAdvancesWallHighWaterMark() {
        status="active";val p=integration();p.open()
        wall+=10_000;tick+=10_000;assertTrue(p.tick())
        wall-=1;tick+=1;assertFalse(p.tick());assertNull(repo.login())
    }

    @Test fun firstCachedAcceptRejectsRollbackSinceConstruction() {
        status="active";integration().open()
        val delayed=integration();offline=true
        wall-=1_000;tick+=1_000;delayed.open()
        assertFalse(delayed.ownsProvider);assertNull(repo.login())
    }
    @Test fun firstCachedAcceptClampsLeaseToElapsedSinceConstruction() {
        status="active";integration().open()
        val delayed=integration();offline=true
        tick+=86_400_001L // wall stayed frozen while instance waited to open
        delayed.open()
        assertFalse(delayed.ownsProvider);assertNull(repo.login())
    }
    @Test fun manualProviderDataIsNotWipedByPanelDenialInSeparateVault() {
        val manual = ProviderRepository(CredentialVault(context, { SecretKeySpec(ByteArray(16) { 9 }, "AES") }, "manual-test"), { url ->
            if (url.contains("action=")) "[]" else """{"user_info":{"auth":1,"status":"Active"}}""" })
        manual.connect(ProviderLogin("Manual", "https://manual.invalid", "u", "p"))
        status = "active"; val p = integration(); p.open()
        status = "inactive"; wall += 61_000; tick += 61_000; p.open()
        assertEquals("Manual", manual.cached()?.name)
    }

    private fun policy(hidden:Boolean,version:Int=1)=JSONObject().put("category_settings_version",version)
        .put("category_provider_generation","00000000-0000-0000-0000-000000000001")
        .put("category_config_revision","hc1_"+(if(hidden)"a" else "b").repeat(64))
        .put("hidden_category_ids",JSONObject().put("live",JSONArray(if(hidden)listOf("7") else emptyList<String>()))
            .put("vod",JSONArray()).put("series",JSONArray()))
    @Test fun malformedAndUnknownSchemaRetainPriorPolicyIncludingAcrossRestart() {
        status="active";categorySettings=policy(true)
        val p=integration();p.open()
        assertEquals(setOf("7"),repo.categoryPolicy()!!.hidden["live"])
        categorySettings=policy(false,2);wall+=61_000;tick+=61_000;p.open()
        assertEquals(setOf("7"),repo.categoryPolicy()!!.hidden["live"])
        p.close()
        repo=ProviderRepository(vault()){url->if(url.contains("action="))"[]" else """{"user_info":{"auth":1,"status":"Active"}}"""}
        offline=true;wall+=61_000;tick+=61_000
        integration().open()
        assertEquals(setOf("7"),repo.categoryPolicy()!!.hidden["live"])
    }
    @Test fun unsupportedPolicyWithNoPriorValidStateDefersEveryProviderRequest() {
        status="active";categorySettings=policy(true,8)
        val p=integration();p.open()
        assertEquals(0,providerCalls);assertNull(repo.login());assertNull(repo.cached());assertFalse(p.ownsProvider)
        categorySettings=policy(true);wall+=61_000;tick+=61_000;p.open()
        assertTrue(p.ownsProvider);assertNotNull(repo.cached())
    }
    @Test fun offlineDoesNotClearKnownHidesAndExplicitEmptyDoesClearThem() {
        status="active";categorySettings=policy(true);val p=integration();p.open()
        offline=true;wall+=61_000;tick+=61_000;p.open()
        assertEquals(setOf("7"),repo.categoryPolicy()!!.hidden["live"])
        offline=false;categorySettings=policy(false);wall+=3_600_000;tick+=3_600_000;p.open()
        assertEquals(emptySet<String>(),repo.categoryPolicy()!!.hidden["live"])
    }
    @Test fun initialProviderFailureRetriesSameConfigOnNextOpen() {
        var failures=true
        repo=ProviderRepository(vault()) { url ->
            if(failures)throw java.io.IOException()
            if(url.contains("action="))"[]" else """{"user_info":{"auth":1,"status":"Active"}}"""
        }
        status="active"
        val p=integration();p.open()
        assertTrue(p.ownsProvider);assertNull(repo.login())
        assertTrue(p.status.startsWith("Active · "))
        failures=false;wall+=61_000;tick+=61_000;p.open()
        assertNotNull(repo.login());assertNotNull(repo.cached());assertEquals("Active",p.status)
    }

    @Test fun repeatedConfigWhileConnectingDoesNotQueueDuplicateCatalogue() {
        status="active";queued=true
        val p=integration();p.open();queue.removeFirst().run()
        assertEquals("Connecting playlist",p.status)
        val initial=queue.removeFirst()
        wall+=61_000;tick+=61_000
        p.open();queue.removeFirst().run()
        assertTrue(queue.isEmpty())
        initial.run()
        assertNotNull(repo.cached());assertEquals("Active",p.status)
    }

}
