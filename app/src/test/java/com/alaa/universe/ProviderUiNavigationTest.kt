package com.alaa.universe

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
class ProviderUiNavigationTest {
 private fun set(p:ProviderUi,name:String,value:Any){ProviderUi::class.java.getDeclaredField(name).apply{isAccessible=true}.set(p,value)}
 private fun get(p:ProviderUi,name:String)=ProviderUi::class.java.getDeclaredField(name).apply{isAccessible=true}.get(p)
 private fun render(p:ProviderUi){ProviderUi::class.java.getDeclaredMethod("renderEpisodes").apply{isAccessible=true}.invoke(p)}
 private fun scenario(test:(UniverseScreen,ProviderUi)->Unit){
  val c=Robolectric.buildActivity(HomeActivity::class.java).create()
  val s=c.get().screen;val p=s.provider;p.testMode=true
  try{test(s,p)}finally{c.destroy()}
 }
 private fun buttons(s:UniverseScreen):List<TextView>{val g=s.getChildAt(0) as ViewGroup;return (0 until g.childCount).map{g.getChildAt(it)}.filterIsInstance<TextView>().filter{it.isFocusable}}
 private fun episodes(n:Int)= (1..n).map{MediaEntry("$it","Episode $it","c","episode",season=it)}
 @Test fun cachedRefreshCannotReplaceFullscreenPlayer()=scenario{s,p->
  s.home();val original=s.getChildAt(0)
  set(p,"episodeList",episodes(12));set(p,"full",true)
  render(p);assertSame(original,s.getChildAt(0));assertEquals(true,get(p,"full"))
 }
 @Test fun foregroundRecoveryLeavesReleasedFullscreen()=scenario{s,p->
  set(p,"full",true);p.stop()
  assertEquals(false,get(p,"full"));assertEquals("home",s.page)
  assertNull(p.playback.player);assertEquals(1,get(p,"detailGeneration"))
 }
 @Test fun seasonPagingKeepsFocusInSeasons()=scenario{s,p->
  set(p,"episodeList",episodes(13));render(p)
  val tenth=buttons(s).first{it.text=="Season 10"};tenth.requestFocus()
  assertTrue(tenth.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)))
  assertEquals(1,get(p,"seasonOffset"))
  assertTrue(buttons(s).first{it.text=="Season 11"}.hasFocus())
 }
 @Test fun shorterOfflineSeriesClampsStaleOffset()=scenario{s,p->
  set(p,"episodeList",episodes(2));set(p,"seasonOffset",30);set(p,"episodeOffset",30);render(p)
  assertEquals(0,get(p,"seasonOffset"));assertEquals(0,get(p,"episodeOffset"))
  assertTrue(buttons(s).any{it.text=="Season 1"});assertTrue(buttons(s).any{it.text=="Episode 1"})
 }
}
