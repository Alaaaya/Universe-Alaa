package com.alaa.universe
import android.graphics.*
import android.view.*
import android.os.Looper
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhaseOneTest {
 private fun activity()=Robolectric.buildActivity(HomeActivity::class.java).setup().visible().get().also{it.screen.fixedClock="19:42";it.screen.home();layout(it)}
 private fun layout(a:HomeActivity){a.screen.measure(View.MeasureSpec.makeMeasureSpec(1920,1073741824),View.MeasureSpec.makeMeasureSpec(1080,1073741824));a.screen.layout(0,0,1920,1080);shadowOf(Looper.getMainLooper()).idle()}
 private fun key(a:HomeActivity,c:Int){a.dispatchKeyEvent(KeyEvent(0,c));a.dispatchKeyEvent(KeyEvent(1,c));layout(a)}
 private fun shot(a:HomeActivity,n:String){layout(a);val b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);a.screen.draw(Canvas(b));File("/home/kit/tmp/ua/blue-shots").mkdirs();File("/home/kit/tmp/ua/blue-shots/$n.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun homeNavigationAndThemes(){
  val a=activity();shot(a,"home-en")
  val labels=mutableSetOf(a.screen.findFocus()?.contentDescription.toString())
  repeat(5){key(a,22);labels.add(a.screen.findFocus()?.contentDescription.toString())}
  assertEquals(6,labels.size)
  a.screen.arabic=true;a.screen.theme="royal";a.screen.home();shot(a,"home-ar")
  assertEquals(View.LAYOUT_DIRECTION_LTR,a.screen.layoutDirection)
  a.screen.theme="gold";a.screen.home();shot(a,"home-gold");a.finish()
 }
 @Test fun categoriesAndChannels(){
  val a=activity();key(a,23);assertEquals("categories",a.screen.page);shot(a,"categories-en")
  repeat(3){key(a,20)};shot(a,"categories-focused");key(a,23)
  assertEquals("live",a.screen.page);shot(a,"channels-category");a.screen.categories();layout(a);repeat(3){key(a,19)};key(a,23);shot(a,"channels-en")
  assertTrue(a.screen.back());assertEquals("categories",a.screen.page)
  assertTrue(a.screen.back());assertEquals("home",a.screen.page)
  a.screen.arabic=true;a.screen.categories();shot(a,"categories-ar");a.screen.live();shot(a,"channels-ar");a.finish()
 }
 @Test fun scrollAndFullscreen(){
  val a=activity();a.screen.live();layout(a)
  val labels=mutableSetOf(a.screen.findFocus()?.contentDescription)
  repeat(15){key(a,20);labels.add(a.screen.findFocus()?.contentDescription)}
  assertEquals(16,labels.size);shot(a,"channels-scrolled")
  key(a,23);key(a,23);assertTrue(a.screen.fullscreen);assertTrue(a.screen.back())
  key(a,22);key(a,23);a.finish()
 }
}
