package com.alaa.universe

import com.alaa.universe.panel.PanelClient
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.Executor

/**
 * Owns only the dedicated panel repository. Manual provider storage is never touched.
 * All UI callbacks are dispatched through main; repository invalidation is synchronous,
 * so an in-flight catalogue cannot republish credentials after a denial.
 */
internal class PanelIntegration(
    store: PanelClient.Store, transport: PanelClient.Transport,
    private val repository: ProviderRepository,
    private val io: Executor, private val main: Executor,
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val changed: (ProviderSnapshot?, String) -> Unit,
    private val stopped: () -> Unit
) {
    private val lock=Any()
    private var generation=0L
    private var configKey:String?=null
    private var wallDeadline=0L
    private var elapsedDeadline=0L
    private var lastWall=0L
    @Volatile var ownsProvider=false; private set
    @Volatile var code=""; private set
    @Volatile var status="Waiting for activation"; private set
    private var closed=false
    private val client=PanelClient(store,transport, { wall() },SecureRandom(),object:PanelClient.Listener {
        override fun onConfig(config:JSONObject) = accept(config)
        override fun onStopped(reason:String) = deny(reason)
    })
    fun open() { io.execute {
        if(!closed) {
            runCatching { code=client.displayCode() }
            client.open()
        }
    } }
    private fun accept(config:JSONObject) {
        try {
            val p=config.getJSONArray("playlists").getJSONObject(0)
            fun value(k:String)=if(p.isNull(k)) "" else p.optString(k)
            val type=value("type")
            require(type=="xtream" || type=="m3u")
            val login=ProviderLogin(value("name").ifBlank{"Subscription"},value("server"),
                value("username"),value("password"),if(type=="m3u")value("url") else "",
                // Admin-provisioned HTTP is explicit, unlike an untrusted redirect.
                (if(type=="m3u")value("url") else value("server")).startsWith("http://",true))
            ProviderRepository.validateLogin(login)
            val remaining=client.leaseRemainingMillis()
            require(remaining>0)
            synchronized(lock) {
                if(closed)return
                wallDeadline=wall()+remaining;elapsedDeadline=elapsed()+remaining;lastWall=wall()
                val key=p.toString()
                if(configKey==key && ownsProvider)return
                ++generation
                val ticket=generation
                if(repository.login()!=login)repository.clear()
                configKey=key;ownsProvider=true;status="Active"
                // Reserve generation now, not after a possibly slow worker queue.
                val load=repository.prepareConnect(login)
                main.execute { synchronized(lock) { if(ticket==generation&&!closed) { stopped();changed(null,status) } } }
                io.execute {
                    val cached=runCatching{repository.cached()}.getOrNull()
                    if(cached!=null)main.execute { synchronized(lock) {
                        if(ticket==generation&&!closed&&valid())changed(cached,"Active")
                    } }
                    val result=runCatching{load()}
                    main.execute { synchronized(lock) {
                        if(ticket==generation && !closed && valid()) {
                            changed(result.getOrNull()?:cached,if(result.isSuccess)"Active" else "Active · provider unavailable")
                        }
                    } }
                }
            }
        } catch(_:Exception) { deny("invalid_config") }
    }
    private fun valid():Boolean = ownsProvider && wall()>=lastWall && wall()<wallDeadline && elapsed()<elapsedDeadline
    /** Main thread every second, and before any play/reopen. Never waits on panel networking. */
    fun tick():Boolean {
        synchronized(lock) {
            if(ownsProvider && !valid()) { deny("cache_expired");return false }
            return ownsProvider
        }
    }
    private fun deny(reason:String) {
        synchronized(lock) {
            ++generation;configKey=null;wallDeadline=0;elapsedDeadline=0
            val wasOwned=ownsProvider;ownsProvider=false
            status=when(reason){"pending"->"Waiting for activation";"expired","cache_expired"->"Subscription expired";else->"Subscription unavailable"}
            // Also removes panel remnants on startup, even when no lease was restored.
            runCatching{repository.clear()}
            val ticket=generation
            main.execute { synchronized(lock) {
                if(ticket==generation&&!closed) {
                    if(wasOwned)stopped()
                    changed(null,status)
                }
            } }
        }
    }
    fun close() { synchronized(lock) { closed=true;++generation } }
}
