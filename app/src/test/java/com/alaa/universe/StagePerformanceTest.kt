package com.alaa.universe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class StagePerformanceTest {
    @Test fun staticThemeBackgroundIsRasterizedOnceUntilItsStyleChanges() {
        val context=Robolectric.buildActivity(HomeActivity::class.java).create().get()
        val stage=Stage(context).apply{theme="gold";waves=true}
        stage.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY))
        stage.layout(0,0,960,540)
        val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        repeat(5){stage.invalidate();stage.draw(canvas)}
        assertEquals(1,stage.backgroundRenderCount)
        stage.waves=false;stage.invalidate();stage.draw(canvas)
        assertEquals(2,stage.backgroundRenderCount)
        stage.theme="royal";stage.invalidate();stage.draw(canvas)
        assertEquals(3,stage.backgroundRenderCount)
        bitmap.recycle()
    }
}
