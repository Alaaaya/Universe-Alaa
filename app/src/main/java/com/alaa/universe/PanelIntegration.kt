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
    private val initialWall=wall()
    private val initialElapsed=elapsed()
    private var generation=0L
    private var configKey:String?=null
    private var loading=false
    private var wallDeadline=0L
    private var elapsedDeadline=0L
    private var lastWall=0L
    private var leaseSeen=false
    private var cacheRejected=false
    @Volatile var configuredLogin:ProviderLogin?=null; private set
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
            val fresh=client.isFreshDelivery()
            val remaining=client.leaseRemainingMillis() -
                if(!fresh && !leaseSeen)maxOf(0L,initialWall+(elapsed()-initialElapsed)-wall()) else 0L
            require(remaining>0)
            synchronized(lock) {
                if(closed)return
                if(!fresh && (cacheRejected || (!leaseSeen && wall()<initialWall) || (leaseSeen && !valid()))) {
                    deny("cache_expired");return
                }
                if(fresh || !leaseSeen) {
                    wallDeadline=wall()+remaining;elapsedDeadline=elapsed()+remaining;lastWall=wall()
                    leaseSeen=true;cacheRejected=false
                }
                val device=config.getString("device_code")
                val parsed=CategoryPolicy.parse(device,p)
                val oldPolicy=repository.categoryPolicy()
                val sameProvider=repository.login()==login
                val retained=parsed ?: oldPolicy?.takeIf {
                    sameProvider && repository.hasCompatiblePolicy(device,p.getString("id"),
                        p.optString("category_provider_generation").takeIf { v -> v.isNotBlank() })
                }
                // Unknown schema is not a revocation, nor permission to drop previous hides.
                if(retained==null) {
                    status="Category settings unavailable"
                    if(!sameProvider) { deny("invalid_config");status="Category settings unavailable" }
                    return
                }
                val key=p.toString()+retained.toJson().toString()
                if(configKey==key && ownsProvider && (loading || repository.login()!=null))return
                ++generation
                val ticket=generation
                if(!sameProvider)repository.clear()
                val policyChanged=repository.applyCategoryPolicy(retained)
                configuredLogin=login;configKey=key;ownsProvider=true;status="Connecting playlist";loading=true
                // Policy-only changes keep visible content and fetch category metadata, NOT newly unhidden streams.
                val load: () -> ProviderSnapshot = if(sameProvider && policyChanged && repository.cached()!=null && type=="xtream")
                    ({ repository.refreshCategories() }) else repository.prepareConnect(login, loadAllCategories=false)
                main.execute { synchronized(lock) { if(ticket==generation&&!closed) { stopped();changed(null,status) } } }
                io.execute {
                    val cached=runCatching{repository.cached()}.getOrNull()
                    if(cached!=null)main.execute { synchronized(lock) {
                        if(ticket==generation&&!closed&&valid())changed(cached,"Active")
                    } }
                    val result=runCatching{load()}
                    main.execute { synchronized(lock) {
                        if(ticket==generation && !closed && valid()) {
                            loading=false
                            status=if(result.isSuccess)"Active" else "Active · "+(result.exceptionOrNull()?.message ?: "provider unavailable")
                            changed(result.getOrNull()?:cached,status)
                        }
                    } }
                }
            }
        } catch(_:Exception) { deny("invalid_config") }
    }
    private fun valid():Boolean {
        val now=wall()
        val ok=ownsProvider && now>=lastWall && now<wallDeadline && elapsed()<elapsedDeadline
        if(ok)lastWall=now
        return ok
    }
    /** Main thread every second, and before any play/reopen. Never waits on panel networking. */
    fun tick():Boolean {
        synchronized(lock) {
            if(loading && repository.progress!="Ready")status=repository.progress
            if(ownsProvider && !valid()) { deny("cache_expired");return false }
            return ownsProvider
        }
    }
    private fun deny(reason:String) {
        synchronized(lock) {
            cacheRejected=true
            ++generation;configKey=null;loading=false;wallDeadline=0;elapsedDeadline=0
            val wasOwned=ownsProvider;ownsProvider=false;configuredLogin=null
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
