package com.alaa.universe

import android.net.Uri
import androidx.media3.datasource.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PlaylistDataSourceTest {
 private class Fake:DataSource {
  val opened=mutableListOf<String>()
  override fun open(s:DataSpec):Long{opened+=s.uri.toString();return 0}
  override fun read(b:ByteArray,o:Int,n:Int) = -1
  override fun close(){}
  override fun getUri():Uri?=null
  override fun addTransferListener(l:TransferListener){}
 }
 private fun blocked(f:()->Unit){try{f();fail("Must reject nested request")}catch(e:IOException){assertFalse(e.message!!.contains("token="))}}
 @Test fun httpsHlsCannotDowngradeSegmentsOrEncryptionKeys(){
  val fake=Fake();val source=PlaylistDataSource(fake){false}
  source.open(DataSpec(Uri.parse("https://example.test/playlist.m3u8")))
  val manifest="#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"http://example.test/key?token=private\"\n#EXTINF:10,\nhttp://example.test/segment.ts?token=private"
  val nested=listOf(Regex("URI=\"([^\"]+)\"").find(manifest)!!.groupValues[1],manifest.lineSequence().last())
  nested.forEach{url->blocked{source.open(DataSpec(Uri.parse(url)))}}
  assertEquals(1,fake.opened.size)
 }
 @Test fun permitsExplicitHttpButNeverLocalReferences(){
  val fake=Fake();val source=PlaylistDataSource(fake){true}
  source.open(DataSpec(Uri.parse("http://example.test/segment.ts")))
  listOf("file:///private/credentials","content://private/data","data:text/plain,secret","/private/local").forEach{url->blocked{source.open(DataSpec(Uri.parse(url)))}}
  assertEquals(1,fake.opened.size)
 }
 @Test fun policyIsRecheckedForEachOpen(){
  val fake=Fake();var allowed=true;val source=PlaylistDataSource(fake){allowed}
  source.open(DataSpec(Uri.parse("http://example.test/first")));allowed=false
  blocked{source.open(DataSpec(Uri.parse("http://example.test/second")))}
  source.open(DataSpec(Uri.parse("https://example.test/third")))
  assertEquals(2,fake.opened.size)
 }
}
