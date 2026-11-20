package com.alaa.universe
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alaa.universe.panel.PanelClient
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.*
@RunWith(AndroidJUnit4::class)
class PanelDeviceTest {
 @Test fun panelStartupRealNetworkKeystoreAndResponsiveMain() {
  val inst=InstrumentationRegistry.getInstrumentation()
  val repo=ProviderRepository(inst.targetContext)
  val store=object:PanelClient.Store {
   var data:String?=null
   override fun read()=data
   override fun write(s:String){data=s}
  }
  val transport=PanelClient.Transport{fn,req->
   if(fn=="register_device_v2")JSONObject().put("status","registered")
   else JSONObject().put("status","active").put("device_code",req.getString("p_device_code"))
    .put("app_type","universe").put("expires_at",JSONObject.NULL)
    .put("playlists",JSONArray().put(JSONObject().put("id","public-regression").put("name","Public panel")
     .put("type","m3u").put("url","https://iptv-org.github.io/iptv/index.m3u")))
  }
  val worker=Executors.newSingleThreadExecutor()
  val done=CountDownLatch(1)
  val main=android.os.Handler(android.os.Looper.getMainLooper())
  val panel=PanelIntegration(store,transport,repo,worker,Executor{main.post(it)},changed={s,_->if(s!=null)done.countDown()},stopped={})
  try {
   worker.execute{panel.open()}
   var beats=0
   val until=System.nanoTime()+90_000_000_000L
   while(done.count>0 && System.nanoTime()<until) {
    val heartbeat=CountDownLatch(1)
    main.post{heartbeat.countDown()}
    assertTrue("Main thread blocked",heartbeat.await(2,TimeUnit.SECONDS));beats++
    done.await(100,TimeUnit.MILLISECONDS)
   }
   assertEquals("Panel never completed "+panel.status,0,done.count)
   assertEquals("Active",panel.status)
   assertTrue(repo.cached()!!.entries.size>8000)
   android.util.Log.i("UniverseQA","panel Active channels="+repo.cached()!!.entries.size+" mainHeartbeats="+beats)
  } finally {worker.shutdownNow()}
 }
}
