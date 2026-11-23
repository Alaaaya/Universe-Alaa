package com.alaa.universe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
    private fun shot(s:UniverseScreen,name:String,w:Int=1920,h:Int=1080){
        layout(s,w,h);val path=File("build/atlas-captures").apply{mkdirs()}
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
        val firstPageViewCount=(s.getChildAt(0) as Stage).childCount
        assertEquals(Atlas.CAT_PAGE,cats.size);assertEquals(Atlas.LIVE_PAGE,channels.size)
        assertEquals(9000,s.provider.homeCount("live"));assertTrue(cats.all{it.left==1920-Atlas.CATEGORY_X-Atlas.CATEGORY_W})
        assertTrue(channels.all{it.left==1920-Atlas.LIVE_X-Atlas.LIVE_W && ((it as TextView).gravity and Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.LEFT})
        val preview=views(s).filterIsInstance<PlayerView>().single();assertEquals("atlas-preview",preview.tag)
        assertTrue(s.childCount==1 && preview.left>channels.first().right)
        assertEquals("atlas-category-",s.findFocus()?.tag)
        key(a,KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("atlas-entry-live-1",s.findFocus()?.tag)
        repeat(Atlas.LIVE_PAGE){key(a,KeyEvent.KEYCODE_DPAD_DOWN);layout(s)}
        assertEquals("atlas-entry-live-11",s.findFocus()?.tag)
        assertSame(preview,views(s).filterIsInstance<PlayerView>().single())
        set(s.provider,"offset",8990);s.provider.browse("live");layout(s)
        assertEquals("atlas-entry-live-9000",rows(s,"atlas-entry-").last().tag)
        assertEquals(Atlas.LIVE_PAGE,rows(s,"atlas-entry-").size)
        // Static decorations may vary by design; page size must not grow with the catalogue.
        assertEquals(firstPageViewCount,(s.getChildAt(0) as Stage).childCount)
        assertTrue(s.back());assertEquals("home",s.page)
        for((isSeries,n) in listOf(false to "03-movies",true to "04-series")){
            s.catalogue(isSeries);shot(s,n)
            val cards=rows(s,"atlas-entry-");assertEquals(Atlas.GRID_PAGE,cards.size)
            assertEquals(2,cards.map{it.top}.distinct().size);assertEquals(5,cards.map{it.left}.distinct().size)
            assertTrue(cards[0].left<cards[1].left)
            assertEquals("atlas-category-",s.findFocus()?.tag)
            key(a,KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(cards[0].tag,s.findFocus()?.tag)
            key(a,KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(cards[1].tag,s.findFocus()?.tag)
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
    @Test fun atlasShowsATVBadgeForEveryChannelInAllAndFilteredLists() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create();val s=c.get().screen
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID
        s.categories();layout(s)
        fun checkBadges(expectedFirst:String){
            val channels=rows(s,"atlas-entry-").map{it as TextView}
            val logos=rows(s,"atlas-channel-logo-").map{it as ImageView}
            assertEquals(Atlas.LIVE_PAGE,channels.size);assertEquals(channels.size,logos.size)
            assertEquals("atlas-channel-logo-$expectedFirst",logos.first().tag)
            channels.zip(logos).forEach{(row,logo)->
                assertEquals(row.tag.toString().removePrefix("atlas-entry-"),logo.tag.toString().removePrefix("atlas-channel-logo-"))
                assertNotNull(logo.drawable);assertFalse(logo.isFocusable);assertFalse(logo.isClickable)
                assertTrue(logo.left>=row.left && logo.right<=row.left+row.paddingLeft)
            }
        }
        checkBadges("live-1")
        s.findViewWithTag<View>("atlas-category-2").performClick();layout(s)
        checkBadges("live-2");shot(s,"06-live-category")
        c.pause().stop().destroy()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-mdpi") fun phoneLandscapeTapAndVerticalSwipe() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create();val a=c.get();val s=a.screen
        shadowOf(a.packageManager).setSystemFeature("android.hardware.touchscreen",true)
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID;s.home()
        layout(s,960,540);assertTrue(PhoneInput.enabled(a))
        fun motion(start:Long,t:Long,action:Int,x:Float,y:Float){val e=MotionEvent.obtain(start,t,action,x,y,0);s.dispatchTouchEvent(e);e.recycle()}
        fun tap(x:Float,y:Float){motion(0,0,0,x,y);motion(0,80,1,x,y);shadowOf(Looper.getMainLooper()).idle()}
        tap(240f,160f);layout(s,960,540);assertEquals("live",s.page)
        motion(100,100,0,360f,405f);motion(100,130,2,360f,288f);motion(100,190,1,360f,190f)
        layout(s,960,540);assertEquals("atlas-entry-live-11",rows(s,"atlas-entry-").first().tag)
        s.home();layout(s,960,540);tap(220f,220f);layout(s,960,540);assertEquals("movies",s.page)
        assertEquals(10,rows(s,"atlas-entry-").size)
        s.home();layout(s,960,540);tap(220f,332f);layout(s,960,540);assertEquals("themes",s.page)
        c.pause().stop().destroy()
    }
    private fun openFixturePlayer(s:UniverseScreen) : FullscreenControls {
        s.categories();layout(s)
        val item=MediaEntry("live-1","الملاعب","1","live")
        val session=s.provider.playback
        set(session,"player",androidx.media3.exoplayer.ExoPlayer.Builder(s.context).build())
        set(session,"entry",item)
        s.provider.javaClass.getDeclaredMethod("fullscreen").apply{isAccessible=true}.invoke(s.provider)
        layout(s)
        return views(s).filterIsInstance<FullscreenControls>().single()
    }
    private fun fixtureStill(a:HomeActivity):ImageView=ImageView(a).apply{
        val supplied=System.getenv("ATLAS_REFERENCE_STILL")?.let{android.graphics.BitmapFactory.decodeFile(it)}
        if(supplied!=null)setImageBitmap(supplied) else setImageResource(R.drawable.atlas_orbit)
        scaleType=ImageView.ScaleType.CENTER_CROP
    }
    @Test fun fullscreenAtlasDockKeepsChannelFavoriteQualityAndTransportOnRemote() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create();val a=c.get();val s=a.screen
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID
        val controls=openFixturePlayer(s)
        val video=views(s).filterIsInstance<PlayerView>().single()
        assertEquals(1920,video.width);assertEquals(1080,video.height)
        val dock=s.findViewWithTag<View>("atlas-live-dock")
        val logo=s.findViewWithTag<ImageView>("atlas-player-logo")
        assertTrue(dock.top>video.height*3/4 && dock.height<video.height/5);assertNotNull(logo.drawable)
        assertFalse(s.findViewWithTag<TextView>("atlas-player-clock").text.isNullOrBlank())
        val channels=s.findViewWithTag<View>("atlas-player-channels")
        val favorite=s.findViewWithTag<FullscreenControls.Control>("atlas-player-favorite")
        val quality=s.findViewWithTag<View>("atlas-player-quality")
        assertTrue(logo.left < channels.left && channels.left < favorite.left && favorite.left < quality.left)
        assertSame(channels,s.findFocus())
        // Render native controls over a known still instead of implying live playback.
        val still=fixtureStill(a)
        val scene=video.parent as Stage
        scene.put(still,0,0,1920,1080);controls.bringToFront()
        shot(s,"07-fullscreen-live");scene.removeView(still)
        key(a,KeyEvent.KEYCODE_DPAD_CENTER);layout(s)
        assertEquals(View.INVISIBLE,controls.visibility)
        assertEquals(1,views(s).filterIsInstance<ChannelOverlay>().size)
        assertTrue(s.back());controls.reveal();assertSame(channels,s.findFocus())
        key(a,KeyEvent.KEYCODE_DPAD_RIGHT);assertSame(favorite,s.findFocus())
        key(a,KeyEvent.KEYCODE_DPAD_CENTER);assertTrue(favorite.selectedIcon)
        key(a,KeyEvent.KEYCODE_DPAD_RIGHT);assertSame(quality,s.findFocus())
        key(a,KeyEvent.KEYCODE_DPAD_CENTER)
        val qualityDialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull(qualityDialog);assertTrue(qualityDialog.isShowing)
        assertTrue(views(qualityDialog.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="Quality"})
        qualityDialog.dismiss();layout(s)
        quality.requestFocus();key(a,KeyEvent.KEYCODE_DPAD_RIGHT)
        key(a,KeyEvent.KEYCODE_DPAD_CENTER);layout(s)
        assertEquals("السابق",s.findFocus()?.contentDescription)
        controls.conceal();controls.reveal()
        assertSame(channels,s.findFocus())
        c.pause().stop().destroy()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-mdpi") fun fullscreenAtlasDockAcceptsPhoneTapAndReveal() {
        val c=Robolectric.buildActivity(HomeActivity::class.java).create();val a=c.get();val s=a.screen
        shadowOf(a.packageManager).setSystemFeature("android.hardware.touchscreen",true)
        s.provider.testMode=true;c.start().resume().visible();load(s);s.theme=Atlas.ID
        val controls=openFixturePlayer(s);layout(s,960,540)
        fun tap(x:Float,y:Float){
            listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEachIndexed{i,action->
                val e=MotionEvent.obtain(100,100L+i*60,action,x,y,0);s.dispatchTouchEvent(e);e.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        controls.conceal();tap(450f,240f);assertEquals(View.VISIBLE,controls.visibility)
        val scene=views(s).filterIsInstance<PlayerView>().single().parent as Stage
        val still=fixtureStill(a);scene.put(still,0,0,1920,1080);controls.bringToFront()
        shot(s,"08-fullscreen-phone",960,540);scene.removeView(still)
        val favorite=s.findViewWithTag<FullscreenControls.Control>("atlas-player-favorite")
        tap((favorite.left+favorite.right)/2f,(favorite.top+favorite.bottom)/2f)
        assertTrue(favorite.selectedIcon)
        val channels=s.findViewWithTag<View>("atlas-player-channels")
        tap((channels.left+channels.right)/2f,(channels.top+channels.bottom)/2f)
        assertEquals(View.INVISIBLE,controls.visibility)
        assertEquals(1,views(s).filterIsInstance<ChannelOverlay>().size)
        c.pause().stop().destroy()
    }

}
