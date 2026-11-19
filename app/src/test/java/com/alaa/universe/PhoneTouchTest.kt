package com.alaa.universe

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneTouchTest {
 private lateinit var activity:Activity
 @Before fun setup(){
    activity=Robolectric.buildActivity(Activity::class.java).setup().get()
    shadowOf(activity.packageManager).setSystemFeature("android.hardware.touchscreen",true)
 }
 private fun layout(view:View,w:Int=960,h:Int=540){
    view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
    view.layout(0,0,w,h)
 }
 @Test fun identicalSceneCoordinatesAtPhoneSize(){
    val stage=Stage(activity)
    val row=TextView(activity).apply{text="TV layout";isClickable=true}
    stage.put(row,100,200,400,80,40f)
    layout(stage)
    assertEquals(50,row.left);assertEquals(100,row.top)
    assertEquals(200,row.width);assertEquals(40,row.height)
    assertEquals(20f,row.textSize,0.01f)
    layout(stage,1920,1080)
    assertEquals(100,row.left);assertEquals(200,row.top)
    assertEquals(400,row.width);assertEquals(80,row.height)
 }
 private fun swipe(stage:Stage,x:Float=700f,y:Float=400f){
    val now=android.os.SystemClock.uptimeMillis()
    val down=MotionEvent.obtain(now,now,0,x,y,0)
    val move=MotionEvent.obtain(now,now+20,2,x,y-150,0)
    val up=MotionEvent.obtain(now,now+40,1,x,y-150,0)
    stage.onInterceptTouchEvent(down)
    assertTrue(stage.onInterceptTouchEvent(move))
    stage.onTouchEvent(move);stage.onTouchEvent(up)
    down.recycle();move.recycle();up.recycle()
 }
 @Test fun swipePagesWithoutMovingScene(){
    val stage=Stage(activity);var calls=0
    stage.swipe={x,_,_,dy->assertEquals(1400f,x,0.01f);assertTrue(dy<0);calls++}
    layout(stage);swipe(stage)
    assertEquals(1,calls);assertEquals(0,stage.scrollY)
 }
 @Test fun firstTapActivatesWithoutFocusPriming(){
    val stage=Stage(activity);activity.setContentView(stage);var clicks=0
    val button=TextView(activity).apply{text="Tap";isFocusableInTouchMode=true;setOnClickListener{clicks++}}
    stage.put(button,100,100,400,80,40f);layout(stage)
    val now=android.os.SystemClock.uptimeMillis()
    val down=MotionEvent.obtain(now,now,0,100f,60f,0)
    val up=MotionEvent.obtain(now,now+30,1,100f,60f,0)
    stage.dispatchTouchEvent(down);stage.dispatchTouchEvent(up)
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(1,clicks);down.recycle();up.recycle()
 }
 @Test fun televisionWithTouchscreenKeepsOriginalGeometry(){
    shadowOf(activity.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager)
        .setCurrentModeType(android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
    assertFalse(PhoneInput.enabled(activity))
    val stage=Stage(activity);val button=TextView(activity).apply{text="TV";isClickable=true}
    stage.put(button,100,200,400,80,40f);layout(stage,1920,1080)
    assertEquals(100,button.left);assertEquals(200,button.top);assertEquals(400,button.width);assertEquals(80,button.height)
 }
 @Test fun phonePlayerUsesSameNativeControls(){
    val p=PlaybackSession(activity,{ProviderRepository(activity)}){}
    var back=false
    val controls=FullscreenControls(activity,p,MediaEntry("1","Live","c","live"),Harak.GOLD,"Live",false,{back=true},{},{},{false},{})
    layout(controls)
    assertEquals(1,controls.childCount);assertEquals(View.VISIBLE,controls.stage.visibility)
    controls.stage.findViewWithTag<View>("universe-back").performClick()
    assertTrue(back)
    controls.conceal();assertTrue(controls.reveal());assertEquals(View.VISIBLE,controls.visibility)
    p.stop()
 }
 @Test fun capturePhoneAndTvSameScenes(){
    val s=UniverseScreen(activity);s.fixedClock="12:34";s.provider.testMode=true
    val out=File("/home/kit/universe-Alaa/artifacts/qa-0.4.7").apply{mkdirs()}
    fun shot(name:String){
        for((label,size) in listOf("phone" to (960 to 540),"tv" to (1920 to 1080))){
            layout(s,size.first,size.second)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            val bitmap=Bitmap.createBitmap(size.first,size.second,Bitmap.Config.ARGB_8888)
            s.draw(Canvas(bitmap));File(out,"$label-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    val p=s.provider
    fun data(kind:String){
        ProviderUi::class.java.getDeclaredField("snapshot").apply{isAccessible=true}.set(p,ProviderSnapshot(
            "Layout QA",null,(1..30).map{MediaEntry("$kind-$it","Title $it","cat",kind)},
            listOf(MediaCategory("cat","4K NETFLIX",kind))
        ))
    }
    for(theme in listOf("red","gold","royal","harak")){
        s.theme=theme;s.home();shot("$theme-home")
        s.openThemes();shot("$theme-settings")
        for(kind in listOf("live","movie","series")){data(kind);p.browse(kind);shot("$theme-$kind")}
        val controls=FullscreenControls(activity,p.playback,MediaEntry("1","Channel 1","cat","live"),
            if(theme=="harak")Harak.GOLD else android.graphics.Color.RED,"Live TV",false,{},{},{},{false},{})
        s.removeAllViews();s.addView(controls);shot("$theme-player")
    }
    data("movie");p.browse("movie");layout(s)
    val stage=s.getChildAt(0) as Stage
    swipe(stage)
    val offset=ProviderUi::class.java.getDeclaredField("offset").apply{isAccessible=true}.getInt(p)
    assertEquals(10,offset)
    p.close()
 }
}
