package com.alaa.universe

import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-television-mdpi")
class PrayerThemeRefreshTest {
    @Test fun minuteRefreshUpdatesPrayerHomeWithoutRebuildingItsViews() {
        val controller=Robolectric.buildActivity(HomeActivity::class.java).create()
        val screen=controller.get().screen
        screen.provider.testMode=true
        screen.homeTheme="prayer"
        screen.fixedClock="19:42"
        screen.home()
        screen.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY))
        screen.layout(0,0,1920,1080)
        val stage=screen.getChildAt(0)
        val clock=screen.findViewWithTag<TextView>("prayer-clock")
        assertEquals("19:42",clock.text.toString())
        screen.fixedClock="19:43"
        val tick=UniverseScreen::class.java.getDeclaredField("prayerTick").apply{isAccessible=true}.get(screen) as Runnable
        tick.run()
        assertSame(stage,screen.getChildAt(0))
        assertEquals("19:43",screen.findViewWithTag<TextView>("prayer-clock").text.toString())
        controller.destroy()
    }
}
