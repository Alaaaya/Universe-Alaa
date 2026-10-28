package com.alaa.universe

import android.graphics.*
import android.view.*
import android.widget.TextView
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhaseOneTest {
    private val out=File("/home/kit/tmp/ua/shots").apply{mkdirs()}
    private fun activity():HomeActivity {
        val a=Robolectric.buildActivity(HomeActivity::class.java).setup().visible().get()
        a.screen.fixedClock="08:11"
        a.screen.home()
        layout(a)
        return a
    }
    private fun layout(a:HomeActivity) {
        a.screen.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY))
        a.screen.layout(0,0,1920,1080)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun capture(a:HomeActivity,name:String) {
        layout(a)
        val b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
        a.screen.draw(Canvas(b))
        File(out,name+".png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        assertNotEquals(b.getPixel(960,300),0)
    }
    private fun key(a:HomeActivity,code:Int) {
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
        layout(a)
    }
    private fun label(a:HomeActivity)=(a.screen.findFocus() as? TextView)?.text.toString()
    @Test fun homeScreensAndSixItems() {
        val a=activity()
        capture(a,"home-en")
        val labels=mutableListOf(a.screen.findFocus()?.contentDescription.toString())
        // Home tile accessibility identity is its module label.
        repeat(5){key(a,KeyEvent.KEYCODE_DPAD_RIGHT);labels.add(a.screen.findFocus()?.contentDescription.toString())}
        assertEquals(listOf("Live TV","Movies","Series","Subscriptions","Settings","Reload"),labels)
        key(a,KeyEvent.KEYCODE_DPAD_LEFT)
        a.screen.arabic=true;a.screen.home();capture(a,"home-ar")
        assertEquals(View.LAYOUT_DIRECTION_LTR,a.screen.layoutDirection)
        a.finish()
    }
    @Test fun liveDpadFavoriteDrawerAndBack() {
        val a=activity()
        key(a,KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("live",a.screen.page)
        capture(a,"live-en")
        val first=label(a)
        key(a,KeyEvent.KEYCODE_DPAD_DOWN)
        assertNotEquals(first,label(a))
        key(a,KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(label(a) in listOf("♡","♥"))
        key(a,KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("♥",label(a))
        key(a,KeyEvent.KEYCODE_DPAD_LEFT)
        key(a,KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(a.screen.drawer)
        capture(a,"live-categories")
        key(a,KeyEvent.KEYCODE_DPAD_DOWN)
        key(a,KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1,a.screen.selectedCategory)
        assertFalse(a.screen.drawer)
        assertTrue(a.screen.back())
        assertEquals("home",a.screen.page)
        a.screen.arabic=true;a.screen.live();capture(a,"live-ar")
        a.finish()
    }
    @Test fun selectionFullscreenAndEmptyCategory() {
        val a=activity();a.screen.live();layout(a)
        key(a,KeyEvent.KEYCODE_DPAD_CENTER) // first OK selects, next OK fullscreen
        assertFalse(a.screen.fullscreen)
        key(a,KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(a.screen.fullscreen)
        assertTrue(a.screen.back())
        assertFalse(a.screen.fullscreen)
        a.screen.live(true);layout(a)
        key(a,KeyEvent.KEYCODE_DPAD_DOWN) // Favorites, empty
        key(a,KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1,a.screen.selectedCategory)
        assertNotNull(a.screen.findFocus())
        capture(a,"live-empty")
        a.finish()
    }
    @Test fun scrollEveryChannelAndReturnFocus() {
        val a=activity();a.screen.live();layout(a)
        val visited=mutableSetOf(label(a))
        repeat(11){key(a,KeyEvent.KEYCODE_DPAD_DOWN);visited.add(label(a))}
        assertEquals(12,visited.size)
        assertTrue(label(a).contains("Alaa Classic"))
        capture(a,"live-scrolled")
        val previous=label(a)
        key(a,KeyEvent.KEYCODE_DPAD_RIGHT)
        key(a,KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(previous,label(a))
        repeat(11){key(a,KeyEvent.KEYCODE_DPAD_UP)}
        assertTrue(label(a).contains("Alaa One"))
        a.finish()
    }
    @Test fun reconstructedReferenceDiagrams() {
        // This is an independent diagram from the analyst's proportions, NOT an original APK screenshot.
        for (home in listOf(true,false)) {
            val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
            val c=Canvas(bitmap);c.drawColor(0xff111111.toInt())
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=30f;typeface=Typeface.DEFAULT}
            fun box(x:Float,y:Float,w:Float,h:Float,name:String) {
                p.color=0xff252319.toInt();p.style=Paint.Style.FILL;c.drawRect(x,y,x+w,y+h,p)
                p.color=0xffd8b602.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawRect(x,y,x+w,y+h,p)
                p.style=Paint.Style.FILL;c.drawText(name,x+20,y+45,p)
            }
            p.color=0xffbdbdbd.toInt();c.drawText("RECONSTRUCTED LAYOUT · NOT A RUNTIME SCREENSHOT",60f,1030f,p)
            if(home) {
                box(60f,50f,900f,120f,"Account / expiry · clock / date")
                box(1420f,50f,440f,120f,"City / weather")
                box(690f,226f,540f,340f,"Centered logo / title / tagline")
                val labels=listOf("Live TV","Movies","Series","Subscriptions","Settings","Reload")
                labels.forEachIndexed{i,s->box(64f+i*300,648f,280f,230f,s)}
            } else {
                box(48f,30f,1824f,132f,"Header 15% · logo / title / search / clock")
                box(48f,177f,866f,754f,"Vertical channels · left boundary 50%")
                box(960f,177f,902f,515f,"Inline player · to y=65%")
                box(960f,714f,902f,230f,"Channel name / favorite / description")
                p.color=0xffbdbdbd.toInt();c.drawText("Category drawer overlays left 35% on request",60f,980f,p)
            }
            File(out,if(home)"reference-home.png" else "reference-live.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
