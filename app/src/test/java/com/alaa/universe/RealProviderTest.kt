package com.alaa.universe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.KeyEvent
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.crypto.KeyGenerator

/** Opt-in private integration evidence. No provider material exists in source or APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealProviderTest {
 @Test fun realAccountEncryptedReopenAndNativeScreens(){
  val input=File("/home/kit/tmp/ua/private/login.json")
  assumeTrue(input.exists())
  val j=JSONObject(input.readText())
  val login=ProviderLogin(j.getString("name"),j.getString("server"),j.getString("username"),j.getString("password"),allowHttp=j.optBoolean("allowHttp"))
  val context=RuntimeEnvironment.getApplication()
  val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
  val vault=CredentialVault(context){key};vault.clear()
  val repo=ProviderRepository(vault)
  val started=System.nanoTime()
  var snapshot=repo.connect(login)
  val connectMs=(System.nanoTime()-started)/1000000
  assertTrue(snapshot.entries.count{it.kind=="live"}>50000)
  assertTrue(snapshot.entries.count{it.kind=="movie"}>180000)
  assertTrue(snapshot.entries.count{it.kind=="series"}>49000)
  val series=snapshot.entries.first{it.kind=="series"&&it.id=="49784"}
  val episodes=repo.episodes(series)
  assertTrue(episodes.isNotEmpty())
  val reopened=ProviderRepository(vault)
  val readStart=System.nanoTime()
  val cached=reopened.cached()!!
  val reopenMs=(System.nanoTime()-readStart)/1000000
  assertEquals(snapshot.entries.size,cached.entries.size)
  assertEquals(snapshot.entries.last(),cached.entries.last())
  assertEquals(snapshot.name,reopened.login()!!.name)
  assertEquals(episodes,reopened.cachedEpisodes(series))
  for(e in listOf(snapshot.entries.first{it.kind=="live"},snapshot.entries.first{it.kind=="movie"},episodes.first()))assertTrue(reopened.streamUrl(e).startsWith("https://"))
  val encryptedFiles=File(context.noBackupFilesDir,"provider-v1").listFiles()!!
  assertTrue(encryptedFiles.count{it.name.startsWith("catalog_")}>10)
  encryptedFiles.forEach{f->val bytes=f.readBytes().toString(Charsets.ISO_8859_1);assertFalse(bytes.contains(login.password));assertFalse(bytes.contains(login.username))}
  val counts=JSONObject().put("live",snapshot.entries.count{it.kind=="live"}).put("movies",snapshot.entries.count{it.kind=="movie"}).put("series",snapshot.entries.count{it.kind=="series"})
  File("/home/kit/tmp/ua/REAL-INTEGRATION.json").writeText(JSONObject().put("counts",counts).put("connect_ms",connectMs).put("encrypted_reopen_ms",reopenMs).put("encrypted_chunks",encryptedFiles.size).put("episodes",episodes.size).put("seasons",episodes.map{it.season}.distinct().size).put("credentials_in_apk",false).put("hardware_playback_verified",false).toString(2))
  // Release duplicate full snapshot before native UI evidence.
  snapshot=cached
  val controller=Robolectric.buildActivity(HomeActivity::class.java)
  val a=controller.create().get();a.screen.provider.testMode=true;controller.start().resume().visible()
  val provider=a.screen.provider
  ProviderUi::class.java.getDeclaredField("snapshot").apply{isAccessible=true}.set(provider,snapshot)
  a.screen.fixedClock="10:15";a.screen.home()
  fun layout(){a.screen.measure(View.MeasureSpec.makeMeasureSpec(1920,1073741824),View.MeasureSpec.makeMeasureSpec(1080,1073741824));a.screen.layout(0,0,1920,1080);shadowOf(Looper.getMainLooper()).idle()}
  fun shot(name:String){layout();val b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);a.screen.draw(Canvas(b));File("/home/kit/tmp/ua/real-shots").mkdirs();File("/home/kit/tmp/ua/real-shots/$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  fun tap(label:String){layout();val stage=a.screen.getChildAt(0) as android.view.ViewGroup;val v=(0 until stage.childCount).map{stage.getChildAt(it)}.filterIsInstance<TextView>().first{it.contentDescription==label};assertTrue(v.requestFocus());v.performClick();layout()}
  shot("home-red");assertEquals("red",a.screen.theme)
  a.screen.homeTheme="prayer";a.screen.home();shot("home-prayer")
  a.screen.homeTheme="classic";a.screen.categories();shot("categories")
  // Render only a small safe selection of real provider titles, not arbitrary adult metadata.
  val safeTitles=listOf("Toy Story","Interstellar","Finding Nemo","Zootopia","Ratatouille","Moana","Frozen","The Lion King")
  val movies=snapshot.entries.filter{it.kind=="movie"&&safeTitles.any{t->Regex("""^(?:[A-Z]{2,5} - )?"""+Regex.escape(t)+"""(?: \(\d{4}\))?$""",RegexOption.IGNORE_CASE).matches(it.name)}}.distinctBy{it.name.substringAfter(" - ")}.take(10)
  assertTrue("Expected exact safe movie titles",movies.isNotEmpty())
  val safeLive=snapshot.entries.filter{it.kind=="live"&&it.name.contains("ZDF",true)}.take(30)
  val safeSeries=listOf(series)
  val safe=snapshot.copy(entries=safeLive+movies+safeSeries)
  ProviderUi::class.java.getDeclaredField("snapshot").apply{isAccessible=true}.set(provider,safe)
  a.screen.live();shot("live-list")
  a.screen.catalogue(false);layout()
  repeat(5){a.dispatchKeyEvent(KeyEvent(0,22));a.dispatchKeyEvent(KeyEvent(1,22))}
  shot("movies")
  a.screen.catalogue(true);shot("series")
  a.finish();controller.pause().stop().destroy();repo.clear()
 }
}
