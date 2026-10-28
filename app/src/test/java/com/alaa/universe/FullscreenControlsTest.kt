package com.alaa.universe

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullscreenControlsTest {
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))} else emptyList()
 private fun layout(v:View){v.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));v.layout(0,0,1920,1080)}
 private fun field(p:Any,n:String,v:Any){p.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(p,v)}
 @Test fun fullscreenNativeShotsAndThemes(){
  val controller=Robolectric.buildActivity(HomeActivity::class.java).create()
  val a=controller.get();val p=a.screen.provider;p.testMode=true
  controller.start().resume().visible()
  val exo=ExoPlayer.Builder(a).build();field(p.playback,"player",exo)
  for(kind in listOf("live","episode")){
   val item=MediaEntry("fixture-$kind",if(kind=="live")"Al Jazeera HD" else "S1E1 · The Sea Will Overflow","news",kind,season=1)
   field(p.playback,"entry",item)
   field(p,"selected",if(kind=="episode")MediaEntry("series","The Sea Will Overflow","drama","series") else item)
   field(p,"episodeList",listOf(item))
   ProviderUi::class.java.getDeclaredMethod("fullscreen").apply{isAccessible=true}.invoke(p)
   layout(a.screen);shadowOf(Looper.getMainLooper()).idle()
   val overlay=all(a.screen).filterIsInstance<FullscreenControls>().single()
   assertEquals(View.LAYOUT_DIRECTION_LTR,overlay.layoutDirection)
   assertEquals(9,all(overlay).filterIsInstance<FullscreenControls.Control>().size)
   assertFalse(all(overlay).filterIsInstance<TextView>().any{it.text.contains("@total")||it.text.contains("@time")})
   assertEquals(if(kind=="live")View.INVISIBLE else View.VISIBLE,all(overlay).filterIsInstance<FullscreenControls.Progress>().single().visibility)
   assertTrue(all(overlay).filterIsInstance<FullscreenControls.Control>().first{it.contentDescription=="Play or pause"}.hasFocus())
   val out=File("/home/kit/tmp/ua/player04");out.mkdirs()
   overlay.initialFocus()
   val b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);a.screen.draw(Canvas(b))
   File(out,if(kind=="live")"live.png" else "series.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4100))
   assertEquals(View.INVISIBLE,overlay.visibility)
   assertTrue(a.screen.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)))
   assertEquals(View.VISIBLE,overlay.visibility)
   assertTrue(all(overlay).any{it.hasFocus()})
   a.screen.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
   a.screen.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT))
   assertEquals(if(kind=="live")"Next" else "Forward 10 seconds",overlay.findFocus()?.contentDescription)
   exo.playWhenReady=true
   all(overlay).first{it.contentDescription=="Play or pause"}.performClick()
   assertFalse("Pause must work while player has no ready media",exo.playWhenReady)
   all(overlay).first{it.contentDescription=="Play or pause"}.performClick()
   assertTrue(exo.playWhenReady)
  }
  for(color in listOf(0xffe50914.toInt(),0xffd8b602.toInt(),0xff518df4.toInt())){
   var saved=false;var prev=0;var next=0
   val overlay=FullscreenControls(a,p.playback,MediaEntry("x","Channel","c","live"),color,"News",false,{},{prev++},{next++},{saved=!saved;saved},{})
   assertTrue(all(overlay).first{it.contentDescription=="Favorite"}.performClick());assertTrue(saved)
   all(overlay).first{it.contentDescription=="Previous"}.performClick();assertEquals(1,prev)
   all(overlay).first{it.contentDescription=="Next"}.performClick();assertEquals(1,next)
   assertFalse(all(overlay).first{it.contentDescription=="Rewind 10 seconds"}.isEnabled)
  }
  controller.pause().stop().destroy()
 }
 @Test fun timesAreSafe(){
  assertEquals("00:00",FullscreenControls.clock(-1))
  assertEquals("09:49",FullscreenControls.clock(589000))
  assertEquals("1:01:01",FullscreenControls.clock(3661000))
 }
}
