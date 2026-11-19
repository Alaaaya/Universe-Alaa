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
@Config(sdk=[28],qualifiers="w393dp-h852dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneTouchTest {
 private lateinit var activity:Activity
 @Before fun setup(){
    activity=Robolectric.buildActivity(Activity::class.java).setup().get()
    shadowOf(activity.packageManager).setSystemFeature("android.hardware.touchscreen",true)
 }
 private fun layout(stage:Stage){
    stage.measure(View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(852,View.MeasureSpec.EXACTLY))
    stage.layout(0,0,393,852)
 }
 @Test fun readableTargetsAndScrollableList(){
    val stage=Stage(activity);activity.setContentView(stage)
    val rows=(0..30).map{i->TextView(activity).apply{
        text="Channel $i";isClickable=true;isFocusableInTouchMode=true
        stage.put(this,0,160+i*60,400,60,28f)
    }}
    layout(stage)
    assertEquals(56,rows.first().height)
    assertTrue(rows.first().width>=350)
    assertFalse(rows.first().isFocusableInTouchMode)
    val down=MotionEvent.obtain(0,0,0,100f,700f,0)
    stage.onInterceptTouchEvent(down)
    val move=MotionEvent.obtain(0,20,2,100f,100f,0)
    assertTrue(stage.onInterceptTouchEvent(move))
    stage.onTouchEvent(move)
    assertTrue(stage.scrollY>0)
    down.recycle();move.recycle()
 }
 @Test fun capturePhoneScreens(){
    val s=UniverseScreen(activity);s.provider.testMode=true;activity.setContentView(s)
    val out=File("/home/kit/universe-Alaa/artifacts").apply{mkdirs()}
    fun shot(name:String){
        s.measure(View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(852,View.MeasureSpec.EXACTLY))
        s.layout(0,0,393,852)
        // Harak fades in over 250ms on actual devices. Capture its settled frame.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        val bitmap=Bitmap.createBitmap(393,852,Bitmap.Config.ARGB_8888)
        s.draw(Canvas(bitmap));File(out,"phone-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    s.home();shot("home")
    s.openThemes();shot("settings")
    val p=s.provider
    ProviderUi::class.java.getDeclaredField("snapshot").apply{isAccessible=true}.set(p,ProviderSnapshot(
        name="Phone QA",expires=null,categories=listOf(MediaCategory("ar","AR | Syria","live")),
        entries=(1..30).map{MediaEntry("$it","Channel $it","ar","live")}
    ))
    p.browse("live");shot("live")
    s.theme="harak";s.home();shot("harak-home")
    s.theme="red"
    for(kind in listOf("movie","series")){
        ProviderUi::class.java.getDeclaredField("snapshot").apply{isAccessible=true}.set(p,ProviderSnapshot(
            "Phone QA",null,(1..30).map{MediaEntry("$kind-$it","Title $it","cat",kind)},
            listOf(MediaCategory("cat","4K NETFLIX",kind))
        ))
        p.browse(kind);shot(kind)
    }
    val session=p.playback
    val controls=FullscreenControls(activity,session,MediaEntry("1","Channel 1","ar","live"),Harak.GOLD,"Live TV",false,{}, {}, {}, {false},{})
    s.removeAllViews();s.addView(controls);shot("player")
    p.close()
 }
 @Test fun landscapeKeepsReadableTargets(){
    val stage=Stage(activity);activity.setContentView(stage)
    val button=TextView(activity).apply{text="Touch me";isClickable=true}
    stage.put(button,0,0,400,80,40f)
    stage.measure(View.MeasureSpec.makeMeasureSpec(852,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY))
    stage.layout(0,0,852,393)
    assertEquals(56,button.height);assertEquals(820,button.width)
 }
 @Test fun firstTapActivatesWithoutFocusPriming(){
    val stage=Stage(activity);activity.setContentView(stage)
    var clicks=0
    val button=TextView(activity).apply{text="Tap";isFocusableInTouchMode=true;setOnClickListener{clicks++}}
    stage.put(button,0,0,400,80,40f);layout(stage)
    val now=android.os.SystemClock.uptimeMillis()
    val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,100f,30f,0)
    val up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,100f,30f,0)
    stage.dispatchTouchEvent(down);stage.dispatchTouchEvent(up)
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(1,clicks)
    down.recycle();up.recycle()
 }

 @Test fun televisionWithTouchscreenKeepsOriginalGeometry(){
    shadowOf(activity.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager)
        .setCurrentModeType(android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
    assertFalse(PhoneInput.enabled(activity))
    val stage=Stage(activity);val button=TextView(activity).apply{text="TV";isClickable=true}
    stage.put(button,100,200,400,80,40f)
    stage.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY))
    stage.layout(0,0,1920,1080)
    assertEquals(100,button.left);assertEquals(200,button.top);assertEquals(400,button.width);assertEquals(80,button.height)
 }
 @Test fun posterTouchRowsUseFullTitleNotPlaceholder(){
    val stage=Stage(activity)
    val card=TextView(activity).apply{isClickable=true}
    stage.put(card,500,160,253,370)
    stage.put(TextView(activity).apply{text="T"},514,188,225,265)
    stage.put(TextView(activity).apply{text="The Full Movie Title"},512,470,229,52)
    layout(stage)
    assertEquals("The Full Movie Title",card.text.toString())
 }
 @Test fun phonePlayerHasTouchActions(){
    val p=PlaybackSession(activity,{ProviderRepository(activity)}){}
    var skipped=0;var back=false
    val controls=FullscreenControls(activity,p,MediaEntry("1","Live","c","live"),Harak.GOLD,"Live",false,{back=true},{skipped--},{skipped++},{false},{})
    fun all(v:View):List<View> = listOf(v)+if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
    val views=all(controls)
    views.first{it.contentDescription=="Next" && it is android.widget.Button}.performClick()
    assertEquals(1,skipped)
    views.first{it.contentDescription=="Back" && it is android.widget.Button}.performClick()
    assertTrue(back);p.stop()
 }
}
