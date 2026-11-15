package com.alaa.universe

import android.util.JsonReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.Executor
import javax.crypto.spec.SecretKeySpec

/** Opt in with UNIVERSE_PRIVATE_PROBE_DIR. Never prints provider URLs, credentials or titles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RealProviderLoadingTest {
    private fun root(): File {
        val path = System.getenv("UNIVERSE_PRIVATE_PROBE_DIR")
        assumeTrue(path != null)
        return File(path!!)
    }
    private fun repo() = ProviderRepository(CredentialVault(
        org.robolectric.RuntimeEnvironment.getApplication(),
        { SecretKeySpec(ByteArray(16) { 8 }, "AES") }, "private-probe"))
    private fun api(p: JSONObject, action: String, https: Boolean = false): String {
        fun encode(v: String) = URLEncoder.encode(v,"UTF-8")
        val base = p.getString("server").trimEnd('/').let { if (https) it.replaceFirst("http://","https://") else it }
        return base + "/player_api.php?username=" + encode(p.getString("username")) +
            "&password=" + encode(p.getString("password")) + if(action.isEmpty()) "" else "&action=" + action
    }
    @Test fun actualConfigIsAcceptedBeforeCatalogueButRefreshHasNoSavedLogin() {
        val config = JSONObject(File(root(),"config.json").readText())
        var state: String? = null
        val store = object : com.alaa.universe.panel.PanelClient.Store {
            override fun read() = state
            override fun write(value: String) { state = value }
        }
        val io = ArrayDeque<Runnable>()
        val repository = repo().also { it.clear() }
        val integration = PanelIntegration(store, { fn, request ->
            if(fn == "register_device_v2") JSONObject().put("status","registered")
            else JSONObject(config.toString()).put("device_code",request.getString("p_device_code"))
        }, repository, Executor { io.addLast(it) }, Executor { it.run() },
            changed={ _, _ -> }, stopped={})
        integration.open(); io.removeFirst().run()
        assertTrue(integration.ownsProvider)
        assertEquals("Connecting playlist",integration.status)
        assertNull(repository.login()); assertNull(repository.cached())
        assertEquals(1,io.size)
        try { repository.refresh(); fail("No catalogue committed yet") } catch(_: IllegalStateException) {}
        println("PRIVATE_PROBE config accepted; pending initial catalogue has no login; refresh rejected")
        integration.close(); repository.clear()
    }
    @Test fun actualHttpAndHttpsAuthCategoriesAndLargeStreamArray() {
        val root = root()
        val p = JSONObject(File(root,"playlist.json").readText())
        val repository = repo()
        for(https in listOf(false,true)) {
            val started = System.nanoTime()
            var count = 0
            var numericMembership = 0
            var categories = 0
            for(action in listOf("", "get_live_categories", "get_live_streams")) {
                val c = java.net.URL(api(p,action,https)).openConnection() as HttpURLConnection
                c.connectTimeout=15000;c.readTimeout=25000;c.instanceFollowRedirects=false
                try {
                    assertEquals(200,c.responseCode)
                    c.inputStream.bufferedReader().use { input ->
                        if(action.isEmpty()) {
                            val user=ProviderRepository.validateAuth(JSONObject(input.readText()))
                            assertEquals("1",user.optString("auth"))
                        } else JsonReader(input).use { reader ->
                            repository.readRows(reader) { row ->
                                if(action.endsWith("categories")) categories++ else {
                                    count++
                                    assertTrue(row.getString("stream_id").isNotBlank())
                                    val ids=row.getJSONArray("category_ids")
                                    if(ids.length()>0 && ids.get(0) is String) numericMembership++
                                }
                            }
                        }
                    }
                } finally { c.disconnect() }
            }
            assertTrue(categories>800); assertTrue(count>50000); assertEquals(count,numericMembership)
            println("PRIVATE_PROBE https=$https categories=$categories streams=$count numeric_memberships_normalized=$numericMembership elapsed_ms=" +
                (System.nanoTime()-started)/1000000)
        }
        repository.clear()
    }
    @Test fun actualPanelConnectPublishesWithoutFetchingEveryCategory() {
        val p = JSONObject(File(root(),"playlist.json").readText())
        val repository = repo().also { it.clear() }
        val login = ProviderLogin(p.getString("name"),p.getString("server"),
            p.getString("username"),p.getString("password"),allowHttp=true)
        val start=System.nanoTime()
        val snapshot=repository.prepareConnect(login,loadAllCategories=false)()
        assertTrue(snapshot.categories.size>1700)
        assertTrue(snapshot.entries.isNotEmpty())
        assertTrue(snapshot.entries.all { it.kind=="live" })
        assertNotNull(repository.login())
        assertEquals(snapshot,ProviderRepository(repositoryVaultForReopen()).cached())
        println("PRIVATE_PROBE bounded panel categories="+snapshot.categories.size+
            " initial_live_entries="+snapshot.entries.size+" elapsed_ms="+(System.nanoTime()-start)/1000000)
        repository.clear()
    }
    private fun repositoryVaultForReopen() = CredentialVault(
        org.robolectric.RuntimeEnvironment.getApplication(),
        { SecretKeySpec(ByteArray(16) { 8 }, "AES") }, "private-probe")

}
