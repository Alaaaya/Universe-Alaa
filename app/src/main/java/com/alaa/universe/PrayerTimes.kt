package com.alaa.universe
import android.content.Context
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.text.SimpleDateFormat
import java.util.*

/** Mirrors launcher confData extraction without evaluating any remote JavaScript. */
object PrayerTimes {
 private val zone=TimeZone.getTimeZone("Europe/Berlin")
 fun date(now:Date=Date())=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{timeZone=zone}.format(now)
 fun extract(html:String):JSONObject {
  val match=Regex("\\bconfData\\s*=\\s*").find(html)?:error("No configuration")
  val start=html.indexOf('{',match.range.last+1);require(start>=0)
  var depth=0;var quoted=false;var escaped=false
  for(i in start until html.length){val c=html[i]
   if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
   else if(c=='"')quoted=true else if(c=='{')depth++ else if(c=='}'&&--depth==0)return JSONObject(html.substring(start,i+1))
  };error("Incomplete configuration")
 }
 fun times(c:JSONObject,key:String,fetched:String):List<String>? {
  val arr=if(key==fetched)c.optJSONArray("times") else null
  val parts=key.split("-")
  val row=c.optJSONArray("calendar")?.optJSONObject(parts[1].toInt()-1)?.optJSONArray(parts[2].toInt().toString())
  val values=if(arr!=null && arr.length()==5)(0..4).map{arr.optString(it)} else if(row!=null&&row.length()>=6)listOf(0,2,3,4,5).map{row.optString(it)} else return null
  return values.takeIf{it.all{v->Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(v)}}
 }
 data class Snapshot(val values:List<String>,val next:Int,val stale:Boolean)
 fun snapshot(context:Context,now:Date=Date()):Snapshot? {
  val prefs=context.getSharedPreferences("prayer-cache",0)
  return try{
   val c=JSONObject(prefs.getString("config",null)?:return null);val fetched=prefs.getString("date","")!!
   val current=times(c,date(now),fetched);val values=current?:times(c,fetched,fetched)?:return null
   val time=SimpleDateFormat("HH:mm",Locale.US).apply{timeZone=zone}.format(now)
   val next=if(current==null)-1 else values.indexOfFirst{it>time}.let{if(it<0)0 else it}
   Snapshot(values,next,current==null)
  }catch(e:Exception){null}
 }
 fun refresh(context:Context,done:()->Unit) {
  Thread{
   try{
    val conn=URL("https://mawaqit.net/de/m/arrahma-mulheim").openConnection() as HttpsURLConnection
    conn.connectTimeout=8000;conn.readTimeout=10000
    try{
     require(conn.responseCode==200)
     val html=conn.inputStream.bufferedReader().use{it.readText().also{v->require(v.length<=500000)}}
     val config=extract(html);require(times(config,date(),date())!=null)
     context.getSharedPreferences("prayer-cache",0).edit().putString("config",config.toString()).putString("date",date()).apply()
    }finally{conn.disconnect()}
   }catch(_:Exception){}finally{done()}
  }.apply{isDaemon=true;name="prayer-refresh"}.start()
 }
}
