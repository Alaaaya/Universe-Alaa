package com.alaa.universe

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PremiumDialogsTest {
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 private fun layout(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.AT_MOST));v.layout(0,0,w,v.measuredHeight)}
 @Test fun nativeDialogMatrix(){
    val out=File("/home/kit/universe-Alaa/artifacts/qa-0.4.9").apply{mkdirs()}
    for(device in listOf("phone","tv"))for(theme in listOf("red","gold","blue","royal","prayer","harak")){
        val w=if(device=="tv")1920 else 960;val h=if(device=="tv")1080 else 540
        RuntimeEnvironment.setQualifiers("w"+w+"dp-h"+h+"dp-land"+(if(device=="tv")"-television" else "")+"-mdpi")
        val ctrl=Robolectric.buildActivity(Activity::class.java).setup();val a=ctrl.get()
        a.getSharedPreferences("appearance",0).edit().putString("theme",if(theme=="prayer")"red" else theme).putString("home",if(theme=="prayer")"prayer" else "classic").commit()
        val s=UniverseScreen(a);a.setContentView(s);s.provider.testMode=true;s.home();s.provider.loginDialog()
        val d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing)
        val decor=d.window!!.decorView
        val fields=all(decor).filterIsInstance<EditText>();assertEquals(5,fields.size);assertFalse(fields.last().transformationMethod is android.text.method.PasswordTransformationMethod)
        assertTrue(fields.all{it.background!=null});assertTrue(all(decor).filterIsInstance<ScrollView>().isNotEmpty())
        fun shot(suffix:String,maxH:Int=h){
            layout(decor,minOf(660,w-32),maxH)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            layout(decor,minOf(660,w-32),maxH)
            val bmp=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888);decor.draw(Canvas(bmp))
            File(out,"$device-$theme-$suffix.png").outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        }
        shot("xtream")
        val scroll=all(decor).filterIsInstance<ScrollView>().last()
        fields[3].isFocusableInTouchMode=true;fields[3].requestFocus()
        scroll.fullScroll(View.FOCUS_DOWN);shot("xtream-bottom")
        assertTrue("Scroll must reach password",scroll.scrollY>0 || scroll.height>=scroll.getChildAt(0).height)
        scroll.fullScroll(View.FOCUS_UP)
        val mTab=all(decor).filterIsInstance<Button>().first{it.text.toString()=="M3U URL"};mTab.performClick()
        assertEquals(View.VISIBLE,(fields.last().parent as View).visibility)
        assertEquals(View.GONE,(fields[1].parent as View).visibility)
        shot("m3u")
        d.getButton(-1).isFocusableInTouchMode=true;assertTrue(d.getButton(-1).requestFocus());assertTrue(d.getButton(-1).isFocused)
        if(device=="phone"&&theme=="harak"){
            fields.last().requestFocus();shot("keyboard-resized",300)
            val sc=all(decor).filterIsInstance<ScrollView>().last();sc.fullScroll(View.FOCUS_DOWN);shot("keyboard-bottom",300)
            assertTrue("scroll height=${sc.height} decor=${decor.height}",sc.height>0);assertTrue("button bottom=${d.getButton(-1).bottom} decor=${decor.height}",d.getButton(-1).bottom<=decor.height)
            decor.layoutDirection=View.LAYOUT_DIRECTION_RTL;shot("rtl")
        }
        d.dismiss();s.provider.close();ctrl.pause().stop().destroy()
    }
 }
 @Test fun sharedActionsAndMenusKeepNativeBehavior(){
    val a=Robolectric.buildActivity(Activity::class.java).setup().get();var chosen=-1
    val d=PremiumDialogs.builder(a).setTitle("Audio").setItems(arrayOf("Auto","English")){_,n->chosen=n}.show()
    d.listView.performItemClick(null,1,1);assertEquals(1,chosen)
    var removed=false
    val confirm=PremiumDialogs.builder(a).setTitle("Remove playlist?").setPositiveButton("Remove"){_,_->removed=true}.setNegativeButton("Cancel",null).show()
    shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(confirm.getButton(-1).background);confirm.getButton(-1).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(removed)
 }
}
