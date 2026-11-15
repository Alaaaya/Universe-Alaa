package com.alaa.universe

import android.graphics.*
import android.os.Looper
import android.view.*
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HarakThemeTest {
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 private fun set(p:Any,n:String,v:Any){p.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(p,v)}
 @Test fun nativeScreensRemoteAndExistingThemes(){
  val c=Robolectric.buildActivity(HomeActivity::class.java).create()
  val a=c.get();val s=a.screen;val p=s.provider;p.testMode=true
  c.start().resume().visible()
  val entries=listOf("live","movie","series").flatMap{kind->(1..12).map{
   MediaEntry("$kind-$it",when(kind){"live"->"القناة الأولى $it";"movie"->"فيلم تجريبي $it";else->"مسلسل تجريبي $it"},"1",kind,plot="وصف تجريبي يقدّمه مزوّد المحتوى لاختبار عرض التفاصيل.")
  }}
  set(p,"snapshot",ProviderSnapshot("المزوّد التجريبي",null,entries,listOf("live","movie","series").flatMap{kind->(1..4).map{MediaCategory("$it",listOf("أخبار","ترفيه","وثائقي","رياضة")[it-1],kind)}}))
  s.theme="harak";s.homeTheme="classic";s.fixedClock="19:42"
  val out=File("/home/kit/tmp/ua/harak-native").apply{mkdirs()}
  fun layout(){s.measure(View.MeasureSpec.makeMeasureSpec(1920,1073741824),View.MeasureSpec.makeMeasureSpec(1080,1073741824));s.layout(0,0,1920,1080);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))}
  fun shot(n:String){layout();val b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);s.draw(Canvas(b));File(out,"$n.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
  fun backButton():View {layout();return all(s).single{it.tag=="universe-back"}.also{assertTrue(it.isFocusable);assertEquals(24,it.left);assertEquals(24,it.top);assertEquals(74,it.width)}}
  s.home();shot("home");assertFalse(all(s).any{it.tag=="universe-back"})
  for(kind in listOf("live","movie","series")){
   set(s,"page",if(kind=="live")"live" else if(kind=="movie")"movies" else "series")
   p.browse(kind);shot(if(kind=="movie")"movies" else kind);backButton()
   if(kind=="movie"){set(p,"selected",entries.first{it.kind==kind});set(s,"page","movie-detail");p.detail(false);shot("movies-detail");backButton()}
  }
  set(s,"page","series-detail");set(p,"selected",entries.first{it.kind=="series"})
  set(p,"episodeList",(1..12).map{MediaEntry("ep$it","الحلقة $it","1","episode",season=if(it<7)1 else 2)})
  ProviderUi::class.java.getDeclaredMethod("renderEpisodes").apply{isAccessible=true}.invoke(p)
  shot("series-detail");backButton()
  val seasonRows=all(s).filter{it.contentDescription?.toString()?.startsWith("الموسم ")==true}
  assertTrue(seasonRows.isNotEmpty())
  assertTrue(seasonRows.all{it.top==568&&it.bottom==638})
  val episodeRows=all(s).filter{it.contentDescription?.toString()?.startsWith("الحلقة ")==true}
  assertEquals(6,episodeRows.size)
  assertTrue(episodeRows.all{it.top>=656&&it.bottom<1080})
  // First episode can reach seasons and the shared Back control entirely with DPAD.
  episodeRows.first().requestFocus()
  a.dispatchKeyEvent(KeyEvent(0,KeyEvent.KEYCODE_DPAD_UP));a.dispatchKeyEvent(KeyEvent(1,KeyEvent.KEYCODE_DPAD_UP))
  assertTrue(seasonRows.any{it.hasFocus()})
  a.dispatchKeyEvent(KeyEvent(0,KeyEvent.KEYCODE_DPAD_UP));a.dispatchKeyEvent(KeyEvent(1,KeyEvent.KEYCODE_DPAD_UP))
  assertEquals("Back",s.findFocus()?.contentDescription)
  val exo=ExoPlayer.Builder(a).build();set(p.playback,"player",exo)
  for(kind in listOf("live","movie","episode")){
   val item=if(kind=="episode")MediaEntry("ep1","الحلقة الأولى","1","episode",season=1)else entries.first{it.kind==kind}
   set(p.playback,"entry",item);set(p,"currentList",entries.filter{it.kind=="live"})
   ProviderUi::class.java.getDeclaredMethod("fullscreen").apply{isAccessible=true}.invoke(p)
   shot(if(kind=="live")"live-detail" else if(kind=="movie")"vod-player" else "episode-player")
   val overlay=all(s).filterIsInstance<FullscreenControls>().single();val back=backButton()
   if(kind=="live"){
    val visible=all(overlay).filterIsInstance<FullscreenControls.Control>().filter{it.visibility==View.VISIBLE}
    assertEquals(setOf("Back","Channels","Favorite","Quality"),visible.map{it.contentDescription.toString()}.toSet())
    assertFalse(visible.any{it.contentDescription=="Play or pause"})
    overlay.conceal();s.clearFocus()
    fun key(k:Int){assertTrue(a.dispatchKeyEvent(KeyEvent(0,k)));assertTrue(a.dispatchKeyEvent(KeyEvent(1,k)))}
    key(KeyEvent.KEYCODE_DPAD_CENTER)
    assertTrue(all(s).any{it is ChannelOverlay})
    shot("live-channel-list")
    key(KeyEvent.KEYCODE_BACK);assertTrue(all(s).none{it is ChannelOverlay})
    assertEquals(View.INVISIBLE,overlay.visibility)
    key(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals(View.VISIBLE,overlay.visibility)
    assertEquals("Channels",overlay.findFocus()?.contentDescription)
    assertTrue(back.requestFocus());assertEquals("Back",s.findFocus()?.contentDescription)
   }
  }
  // Existing home choices remain separate and render without adopting Harak.
  for(t in listOf("red","gold","blue","royal")){s.theme=t;s.home();layout();assertEquals(t,(s.getChildAt(0) as Stage).theme)}
  s.theme="harak";s.openThemes();layout()
  assertEquals("هارك · Noir et Or",(s.findFocus() as? android.widget.TextView)?.text)
  assertTrue(s.findFocus()!!.performClick());assertEquals("harak",s.theme);assertEquals("classic",s.homeTheme)
  assertEquals("harak",a.getSharedPreferences("appearance",0).getString("theme",null))
  c.pause().stop().destroy()
 }
}
