package com.alaa.universe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.ui.PlayerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-television-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AtlasThemeTest {
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun set(obj:Any, field:String, value:Any) {
        obj.javaClass.getDeclaredField(field).apply{isAccessible=true}.set(obj,value)
    }
    private fun layout(s: UniverseScreen,w:Int=1920,h:Int=1080) {
        s.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        s.layout(0,0,w,h);shadowOf(Looper.getMainLooper()).idle()
        s.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));s.layout(0,0,w,h)
    }
    private fun shot(s:UniverseScreen,name:String){
        layout(s);val path=File("build/atlas-captures").apply{mkdirs()}
        val b=Bitmap.createBitmap(s.width,s.height,Bitmap.Config.ARGB_8888);s.draw(Canvas(b))
        File(path,"$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun load(s:UniverseScreen) {
        val kinds=listOf("live","movie","series")
        val cats=listOf("رياضة","أخبار","أطفال","وثائقي","موسيقى","ثقافة","ترفيه","عروض","سفر","محليات")
        val liveNames=listOf("الملاعب","أخبار اليوم","نافذة على العالم","قناة المعرفة","مسرح المدينة","رحلات وثائقية","صوت المسرح","العائلة","الفن والثقافة","استوديو المساء")
        val movieNames=listOf("الطريق إلى البحر","بيت من ورق","مرافئ بعيدة","صباح آخر","عين المدينة","أغنية الغيم","على مهل","بين جبلين","الضوء الأخير","بداية جديدة")
        val seriesNames=listOf("حكايات المساء","شرفة على النهر","خط الشمال","آخر المحطات","حي الياسمين","وقت العبور","محطة ٧","ظلال الصيف","بيت الجيران","ما وراء التل")
        val entries=kinds.flatMap{ kind->(1..if(kind=="live")9000 else 24).map { i->
            val names=when(kind){"live"->liveNames;"movie"->movieNames;else->seriesNames}
            MediaEntry("$kind-$i",names[(i-1)%10]+if(i>10)" · ${String.format(Locale.US,"%04d",i)}" else "",((i-1)%cats.size+1).toString(),kind)
        }}
        set(s.provider,"snapshot",ProviderSnapshot("مكتبة العرض التجريبية",null,entries,kinds.flatMap{ kind->cats.mapIndexed{i,n->MediaCategory("${i+1}",n,kind)} }))
    }
    private fun rows(s:UniverseScreen,prefix:String)=views(s).filter{it.tag?.toString()?.startsWith(prefix)==true}
    private fun key(a:HomeActivity,k:Int){a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,k));a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,k))}
    @Test fun capturesFiveNativeSurfaces() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create(); val s=c.get().screen
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID;s.homeTheme="classic"
        s.home();shot(s,"01-home")
        s.categories();shot(s,"02-live")
        s.catalogue(false);shot(s,"03-movies")
        s.catalogue(true);shot(s,"04-series")
        s.openThemes();shot(s,"05-settings")
        c.pause().stop().destroy()
    }
    @Test fun landscapeNativeScreensAndRemoteWith9000Channels() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create(); val a=c.get(); val s=a.screen
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID;s.homeTheme="classic"
        s.home();shot(s,"01-home");assertEquals("البث المباشر",s.findFocus()?.contentDescription)
        key(a,KeyEvent.KEYCODE_DPAD_CENTER);shot(s,"02-live"); assertEquals("live",s.page)
        val cats=rows(s,"atlas-category-"); val channels=rows(s,"atlas-entry-")
        assertEquals(Atlas.CAT_PAGE,cats.size);assertEquals(Atlas.LIVE_PAGE,channels.size)
        assertEquals(9000,s.provider.homeCount("live"));assertTrue(cats.all{it.left==Atlas.CATEGORY_X})
        assertTrue(channels.all{it.left==Atlas.LIVE_X && ((it as TextView).gravity and Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.RIGHT})
        val preview=views(s).filterIsInstance<PlayerView>().single();assertEquals("atlas-preview",preview.tag)
        assertTrue(s.childCount==1 && preview.right<channels.first().left)
        key(a,KeyEvent.KEYCODE_DPAD_LEFT);assertEquals("atlas-entry-live-1",s.findFocus()?.tag)
        repeat(Atlas.LIVE_PAGE){key(a,KeyEvent.KEYCODE_DPAD_DOWN);layout(s)}
        assertEquals("atlas-entry-live-11",s.findFocus()?.tag)
        assertSame(preview,views(s).filterIsInstance<PlayerView>().single())
        set(s.provider,"offset",8990);s.provider.browse("live");layout(s)
        assertEquals("atlas-entry-live-9000",rows(s,"atlas-entry-").last().tag)
        assertTrue((s.getChildAt(0) as Stage).childCount < 50)
        assertTrue(s.back());assertEquals("home",s.page)
        for((isSeries,n) in listOf(false to "03-movies",true to "04-series")){
            s.catalogue(isSeries);shot(s,n)
            val cards=rows(s,"atlas-entry-");assertEquals(Atlas.GRID_PAGE,cards.size)
            assertEquals(2,cards.map{it.top}.distinct().size);assertEquals(5,cards.map{it.left}.distinct().size)
            assertTrue(cards[0].left>cards[1].left)
            cards.first().requestFocus();key(a,KeyEvent.KEYCODE_DPAD_LEFT);assertEquals(cards[1].tag,s.findFocus()?.tag)
            key(a,KeyEvent.KEYCODE_DPAD_DOWN);assertEquals(cards[6].tag,s.findFocus()?.tag)
            key(a,KeyEvent.KEYCODE_DPAD_DOWN);layout(s)
            assertTrue(s.findFocus()?.tag.toString().endsWith("-12"))
        }
        s.openThemes();shot(s,"05-settings");assertEquals("themes",s.page)
        views(s).filterIsInstance<TextView>().first{it.text=="أحمر"}.performClick()
        layout(s);assertEquals("red",s.theme);assertEquals("red",(s.getChildAt(0) as Stage).theme)
        s.openThemes();layout(s);views(s).filterIsInstance<TextView>().first{it.text=="أطلس · Atlas"}.performClick()
        layout(s);assertEquals(Atlas.ID,s.theme)
        assertEquals(Atlas.ID,a.getSharedPreferences("appearance",0).getString("theme",null))
        c.pause().stop().destroy()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-mdpi") fun phoneLandscapeTapAndVerticalSwipe() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create();val a=c.get();val s=a.screen
        shadowOf(a.packageManager).setSystemFeature("android.hardware.touchscreen",true)
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID;s.home()
        layout(s,960,540);assertTrue(PhoneInput.enabled(a))
        fun motion(start:Long,t:Long,action:Int,x:Float,y:Float){val e=MotionEvent.obtain(start,t,action,x,y,0);s.dispatchTouchEvent(e);e.recycle()}
        fun tap(x:Float,y:Float){motion(0,0,0,x,y);motion(0,80,1,x,y);shadowOf(Looper.getMainLooper()).idle()}
        tap(720f,160f);layout(s,960,540);assertEquals("live",s.page)
        motion(100,100,0,600f,405f);motion(100,130,2,600f,288f);motion(100,190,1,600f,190f)
        layout(s,960,540);assertEquals("atlas-entry-live-11",rows(s,"atlas-entry-").first().tag)
        s.home();layout(s,960,540);tap(740f,220f);layout(s,960,540);assertEquals("movies",s.page)
        assertEquals(10,rows(s,"atlas-entry-").size)
        s.home();layout(s,960,540);tap(740f,332f);layout(s,960,540);assertEquals("themes",s.page)
        c.pause().stop().destroy()
    }
}
