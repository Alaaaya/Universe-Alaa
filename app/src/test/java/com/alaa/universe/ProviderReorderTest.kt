package com.alaa.universe

import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ProviderReorderTest {
    private fun snapshot(n:Int=4)=ProviderSnapshot("Fixture",null,
        (1..n).flatMap{c->(1..16).map{MediaEntry("$c-$it","Channel $c-$it","$c","live")}},
        listOf("live","movie","series").flatMap{kind->(1..n).map{MediaCategory("$it","$kind category $it",kind)}})
    private fun load(p:ProviderUi,s:ProviderSnapshot){ProviderUi::class.java.getDeclaredField("snapshot").apply{isAccessible=true}.set(p,s)}
    private fun scenario(clear:Boolean=true,test:(UniverseScreen,ProviderUi)->Unit){
        val c=Robolectric.buildActivity(HomeActivity::class.java).create()
        val s=c.get().screen;val p=s.provider;p.testMode=true
        if(clear){s.context.getSharedPreferences("media-order-v1",0).edit().clear().commit();s.context.getSharedPreferences("media-state",0).edit().clear().commit()}
        load(p,snapshot())
        try{test(s,p)}finally{c.destroy()}
    }
    private fun buttons(s:UniverseScreen):List<TextView>{val g=s.getChildAt(0) as ViewGroup;return (0 until g.childCount).map{g.getChildAt(it)}.filterIsInstance<TextView>().filter{it.isFocusable}}
    private fun row(s:UniverseScreen,text:String)=buttons(s).first{it.text.toString().removePrefix("↕ ")==text}
    private fun names(s:UniverseScreen,prefix:String)=buttons(s).map{it.text.toString().removePrefix("↕ ")}.filter{it.startsWith(prefix)}
    private fun down(s:UniverseScreen,key:Int=23,repeat:Int=0,flags:Int=0)=s.dispatchKeyEvent(KeyEvent(1,2,KeyEvent.ACTION_DOWN,key,repeat,0,0,0,flags))
    private fun up(s:UniverseScreen,key:Int=23,flags:Int=0)=s.dispatchKeyEvent(KeyEvent(1,3,KeyEvent.ACTION_UP,key,0,0,0,0,flags))
    private fun tap(s:UniverseScreen,key:Int=23){down(s,key);up(s,key)}
    private fun chooseMenu(index:Int){
        val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog);assertTrue(dialog.isShowing)
        dialog.listView.performItemClick(dialog.listView.getChildAt(index),index,index.toLong())
        dialog.dismiss()
    }
    private fun openMenu(s:UniverseScreen,text:String,key:Int=23){row(s,text).requestFocus();assertTrue("opening down",down(s,key));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong()+1));assertTrue("opening up",up(s,key))}
    private fun hold(s:UniverseScreen,text:String,key:Int=23){openMenu(s,text,key);chooseMenu(0)}
    private fun enterCategory(s:UniverseScreen,id:Int=1){s.categories();row(s,"live category $id").requestFocus();tap(s)}

    @Test fun shortOkIsUnchangedAndCanceledOkDoesNotClick()=scenario{s,p->
        s.categories();row(s,"live category 2").requestFocus()
        down(s);up(s,flags=KeyEvent.FLAG_CANCELED)
        assertEquals("categories",s.page)
        tap(s);assertEquals("live",s.page)
        assertEquals("Channel 2-1",names(s,"Channel").first())
        assertNull(p.playback.entry)
    }
    @Test fun longChannelReleaseDoesNotPlayRepeatedOkDoesNotSaveAndBackCancels()=scenario{s,p->
        enterCategory(s)
        row(s,"Channel 1-2").requestFocus();down(s)
        down(s,repeat=1,flags=KeyEvent.FLAG_LONG_PRESS)
        repeat(3){down(s,repeat=it+2,flags=KeyEvent.FLAG_LONG_PRESS)}
        up(s);assertNull(p.playback.entry)
        chooseMenu(0)
        tap(s,20)
        assertEquals(listOf("Channel 1-1","Channel 1-3","Channel 1-2"),names(s,"Channel").take(3))
        assertTrue(s.findFocus()!!.contentDescription.toString().contains("Moving"))
        // Still in move mode, despite repeats from the initial OK hold.
        tap(s,4)
        assertEquals(listOf("Channel 1-1","Channel 1-2","Channel 1-3"),names(s,"Channel").take(3))
        assertEquals("live",s.page);assertNull(p.playback.entry)
    }
    @Test fun channelSaveSurvivesActivityReopenAndProviderRefresh() {
        scenario{s,p->
            enterCategory(s);hold(s,"Channel 1-2");tap(s,19);tap(s)
            assertEquals(listOf("Channel 1-2","Channel 1-1"),names(s,"Channel").take(2))
            // Refresh has provider order, one absent ID and a new ID, without changing the playlist identity.
            val fresh=snapshot().let{it.copy(entries=it.entries.filterNot{e->e.id=="1-1"}+MediaEntry("new","Channel new","1","live"))}
            load(p,fresh);p.browse("live")
            assertEquals("Channel 1-2",names(s,"Channel").first())
            assertNull(p.playback.entry)
        }
        scenario(clear=false){s,_->enterCategory(s);assertEquals(listOf("Channel 1-2","Channel 1-1"),names(s,"Channel").take(2))}
    }
    @Test fun categoryOrdersPersistAndAreIndependentAcrossMediaKinds() {
        scenario{s,p->
            for(kind in listOf("live","movie","series")){
                p.browse(kind,kind=="live")
                hold(s,"$kind category 2");tap(s,19);tap(s)
                assertEquals(listOf("$kind category 2","$kind category 1"),names(s,"$kind category").take(2))
            }
        }
        scenario(clear=false){s,p->
            // Verify via rendered buttons in the new activity rather than reading preferences.
            for(kind in listOf("live","movie","series")){
                p.browse(kind,kind=="live")
                assertEquals(listOf("$kind category 2","$kind category 1"),names(s,"$kind category").take(2))
            }
        }
    }
    @Test fun channelsAreScopedPerCategoryAndBackRestoresSavedOrder()=scenario{s,p->
        enterCategory(s);hold(s,"Channel 1-2",66);tap(s,19);tap(s,66)
        hold(s,"Channel 1-2");tap(s,20)
        assertTrue(s.back());assertEquals("Channel 1-2",names(s,"Channel").first())
        enterCategory(s,2);assertEquals(listOf("Channel 2-1","Channel 2-2"),names(s,"Channel").take(2))
        assertNull(p.playback.entry)
    }
    @Test fun movingAcrossChannelAndCategoryPageEdgesKeepsTargetFocused()=scenario{s,p->
        enterCategory(s);hold(s,"Channel 1-12")
        repeat(3){tap(s,20)}
        assertTrue(s.findFocus()!!.contentDescription.toString().contains("Channel 1-12"))
        tap(s);assertNull(p.playback.entry)
        load(p,snapshot(15));s.categories();hold(s,"live category 7")
        repeat(5){tap(s,20)}
        assertTrue(s.findFocus()!!.contentDescription.toString().contains("live category 7"))
        tap(s,4);assertEquals("categories",s.page)
        assertTrue(s.findFocus()!!.contentDescription.toString().contains("live category 7"))
    }
    @Test fun repeatedSaveOkAndReleaseCannotActivateChannelOrFullscreen()=scenario{s,p->
        enterCategory(s);hold(s,"Channel 1-2");tap(s,19)
        down(s);repeat(3){down(s,repeat=it+1,flags=KeyEvent.FLAG_LONG_PRESS)};up(s)
        assertEquals("Channel 1-2",names(s,"Channel").first());assertNull(p.playback.entry)
        // A subsequent new gesture must work, and Back must cancel it instead of leaving the screen.
        hold(s,"Channel 1-2");tap(s,20);tap(s,4)
        assertEquals("Channel 1-2",names(s,"Channel").first())
    }
    @Test fun refreshDuringMoveMergesNewIdsAndCancelUsesPreviouslySavedOrder()=scenario{s,p->
        enterCategory(s);hold(s,"Channel 1-2");tap(s,19);tap(s)
        hold(s,"Channel 1-2");tap(s,20)
        load(p,snapshot().let{it.copy(entries=it.entries.reversed()+MediaEntry("new","Channel new","1","live"))})
        tap(s,20);tap(s,4)
        assertEquals(listOf("Channel 1-2","Channel 1-1","Channel 1-3"),names(s,"Channel").take(3))
        assertNull(p.playback.entry)
    }
    @Test fun repeatedDirectionMovesOnlyInMoveModeAndStopDiscardsDraft()=scenario{s,p->
        enterCategory(s);hold(s,"Channel 1-2")
        down(s,20);down(s,20,1);down(s,20,2);up(s,20)
        assertEquals("Channel 1-2",names(s,"Channel")[4])
        p.stop();p.browse("live")
        assertEquals("Channel 1-2",names(s,"Channel")[1]);assertNull(p.playback.entry)
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun nativeMoveIndicatorRetainsEveryThemeAndLtrLayout()=scenario{s,p->
        val out=java.io.File("/home/kit/tmp/ua/reorder03/screenshots").apply{mkdirs()}
        fun shot(name:String){
            s.measure(android.view.View.MeasureSpec.makeMeasureSpec(1920,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(1080,1073741824));s.layout(0,0,1920,1080)
            val bitmap=android.graphics.Bitmap.createBitmap(1920,1080,android.graphics.Bitmap.Config.ARGB_8888)
            s.draw(android.graphics.Canvas(bitmap))
            java.io.File(out,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        for(theme in listOf("red","gold","blue","royal")){
            s.theme=theme;enterCategory(s);hold(s,"Channel 1-2");tap(s,20)
            assertEquals(android.view.View.LAYOUT_DIRECTION_LTR,s.layoutDirection)
            assertTrue((s.findFocus() as TextView).text.toString().startsWith("↕ "))
            shot("$theme-channel-moving");tap(s,4)
        }
        s.categories();hold(s,"live category 2");tap(s,19);shot("category-moving");tap(s,4)
        assertNull(p.playback.entry)
    }

    @Test fun longPressMenuOffersMoveAndFavoriteToggleAndCategoryMoveOnly()=scenario{s,p->
        enterCategory(s);openMenu(s,"Channel 1-2")
        var dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("نقل",dialog.listView.adapter.getItem(0))
        assertEquals("أضف إلى المفضلة",dialog.listView.adapter.getItem(1))
        assertNull(p.playback.entry);chooseMenu(1)
        openMenu(s,"Channel 1-2");dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("احذف من المفضلة",dialog.listView.adapter.getItem(1));dialog.cancel()
        s.categories();openMenu(s,"live category 1")
        assertEquals(1,org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().listView.adapter.count)
        chooseMenu(0);tap(s,4);assertEquals("categories",s.page)
    }
    private fun showFavorites(s:UniverseScreen){s.categories();row(s,"Favorites").requestFocus();tap(s)}
    @Test fun favoritesToggleAndReorderSurviveReopenAndRefreshAndBackCancels(){
        scenario{s,p->
            enterCategory(s)
            for(id in listOf(1,2,3)){openMenu(s,"Channel 1-$id");chooseMenu(1)}
            showFavorites(s);assertEquals(listOf("Channel 1-1","Channel 1-2","Channel 1-3"),names(s,"Channel"))
            hold(s,"Channel 1-3");tap(s,19);tap(s,19);tap(s)
            assertEquals(listOf("Channel 1-3","Channel 1-1","Channel 1-2"),names(s,"Channel"))
            hold(s,"Channel 1-3");tap(s,20);tap(s,4)
            assertEquals("Channel 1-3",names(s,"Channel").first())
            load(p,snapshot().let{it.copy(entries=it.entries.reversed())});p.browse("live")
            assertEquals("Channel 1-3",names(s,"Channel").first())
        }
        scenario(clear=false){s,p->
            showFavorites(s);assertEquals(listOf("Channel 1-3","Channel 1-1","Channel 1-2"),names(s,"Channel"))
            openMenu(s,"Channel 1-1");chooseMenu(1)
            assertEquals(listOf("Channel 1-3","Channel 1-2"),names(s,"Channel"))
            assertNull(p.playback.entry)
        }
    }
    @Test fun deletedFavoriteIdsArePrunedAndCannotResurrectFromCacheRefresh(){
        scenario{s,p->
            enterCategory(s);openMenu(s,"Channel 1-1");chooseMenu(1)
            val reduced=snapshot().let{it.copy(entries=it.entries.filterNot{e->e.id=="1-1"})}
            load(p,reduced);showFavorites(s);assertTrue(names(s,"Channel").isEmpty())
            load(p,snapshot());p.browse("live");assertTrue(names(s,"Channel").isEmpty())
        }
        scenario(clear=false){s,_->showFavorites(s);assertTrue(names(s,"Channel").isEmpty())}
    }
    @Test fun favoritesAreIsolatedBetweenProviderAccounts()=scenario{s,p->
        enterCategory(s);openMenu(s,"Channel 1-1");chooseMenu(1)
        load(p,snapshot().copy(name="Other fixture account"));showFavorites(s);assertTrue(names(s,"Channel").isEmpty())
        load(p,snapshot());showFavorites(s);assertEquals(listOf("Channel 1-1"),names(s,"Channel"))
    }

    @Test fun dialogConsumesOpeningHoldRepeatsAndReleaseWithoutSelectingMenu()=scenario{s,p->
        enterCategory(s);row(s,"Channel 1-2").requestFocus();down(s)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong()+1))
        val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertTrue(dialog.dispatchKeyEvent(KeyEvent(1,2,KeyEvent.ACTION_DOWN,23,2,0,0,0,KeyEvent.FLAG_LONG_PRESS)))
        assertTrue(dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,23)))
        assertTrue(dialog.isShowing);assertNull(p.playback.entry)
        dialog.listView.setSelection(0);dialog.listView.requestFocus()
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,23))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,23))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertTrue(s.findFocus()!!.contentDescription.toString().contains("Moving"))
        tap(s,20);tap(s,4)
        assertEquals("Channel 1-2",names(s,"Channel")[1])
    }

    @Test fun canceledSaveOkLeavesDraftUncommittedAndBackRestoresOrder()=scenario{s,p->
        enterCategory(s);hold(s,"Channel 1-2");tap(s,19)
        down(s);up(s,flags=KeyEvent.FLAG_CANCELED)
        assertTrue(s.findFocus()!!.contentDescription.toString().contains("Moving"))
        tap(s,4);assertEquals(listOf("Channel 1-1","Channel 1-2"),names(s,"Channel").take(2))
        assertNull(p.playback.entry)
    }

    @Test fun upgradeMigratesExistingFavoritesOnceAndPrunesUnknownIds()=scenario{s,p->
        s.context.getSharedPreferences("media-state",0).edit().putStringSet("favorites",setOf("live:1-2","live:missing")).commit()
        showFavorites(s);assertEquals(listOf("Channel 1-2"),names(s,"Channel"))
        assertFalse(s.context.getSharedPreferences("media-state",0).contains("favorites"))
        load(p,snapshot().copy(name="Other account"));showFavorites(s);assertTrue(names(s,"Channel").isEmpty())
        load(p,snapshot());showFavorites(s);assertEquals(listOf("Channel 1-2"),names(s,"Channel"))
    }

}
