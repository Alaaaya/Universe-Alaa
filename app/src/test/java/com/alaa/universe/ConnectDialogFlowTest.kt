package com.alaa.universe

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ConnectDialogFlowTest {
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 @Test fun connectPublicM3uThroughRealDialog(){
  val a=Robolectric.buildActivity(Activity::class.java).setup().get()
  val s=UniverseScreen(a);a.setContentView(s);s.provider.testMode=true;s.home();s.provider.loginDialog()
  val d=ShadowAlertDialog.getLatestAlertDialog();val decor=d.window!!.decorView
  val v=all(decor)
  v.filterIsInstance<Button>().first{it.text.toString().contains("M3U URL")}.performClick()
  v.filterIsInstance<EditText>().first{it.hint.toString().contains("playlist.m3u")}.setText("https://iptv-org.github.io/iptv/index.m3u")
  d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
  val t0=System.currentTimeMillis()
  while(System.currentTimeMillis()-t0<100000&&d.isShowing){shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(200)}
  val help=v.filterIsInstance<TextView>().joinToString("|"){it.text}
  assertFalse("still showing: "+help,d.isShowing)
 }
}
