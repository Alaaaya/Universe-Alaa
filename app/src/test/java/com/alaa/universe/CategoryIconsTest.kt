package com.alaa.universe

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
class CategoryIconsTest {
 @Test fun realStyleNames(){
    mapOf("TR|" to "🇹🇷","|AR|" to "🌍","SY " to "🇸🇾","ARABIC" to "🌍","عربي" to "🌍",
      "تركي" to "🇹🇷","سوريا" to "🇸🇾","NETFLIX" to "N","4K NETFLIX" to "N",
      "DE: Nachrichten HD" to "🇩🇪","DE | GERMANY" to "🇩🇪","AR | Cinema" to "🌍",
      "beIN AR" to "beIN","UK FHD" to "🇬🇧","Turkey Sports" to "🇹🇷","Germany" to "🇩🇪",
      "عربي | سوريا" to "🇸🇾","Netflix DE 4K" to "N","Disney+" to "Disney+",
      "Amazon Prime" to "prime","Apple TV+" to "tv+","HBO Max" to "HBO","Shahid VIP" to "شاهد",
      "OSN Movies" to "OSN","MBC HD" to "MBC","STARZPLAY" to "STARZ","Paramount+" to "P+",
      "Sports HD" to "⚽","Kids" to "🧸","Movies" to "🎬","News" to "📰",
      "Documentary" to "🎥","Music" to "🎵","Quran" to "🕌","أطفال" to "🧸",
      "الإمارات" to "🇦🇪","المغرب" to "🇲🇦","الدول العربية" to "🌍",
      "Deutschland HD" to "🇩🇪","Türkei" to "🇹🇷","UNKNOWN" to "▦").forEach{(name,icon)->
        assertEquals(name,icon,CategoryIcons.classify(name).badge)
    }
 }
 @Test fun noPartialWordMatches(){
    listOf("Friday","History","Classic","Drama","Best quality","Showcase").forEach{
      assertEquals(it,"generic",CategoryIcons.classify(it).kind)
    }
 }
 @Test fun leavesOriginalNameIntact(){assertEquals("🇹🇷  TR| Sports",CategoryIcons.label("TR| Sports"))}
 @Test fun all904RealProviderNames(){
    val raw=javaClass.getResource("/real-category-names.json")!!.readText()
    val json=org.json.JSONArray(raw)
    val names=(0 until json.length()).map{json.getString(it)}
    assertEquals(904,names.size)
    names.forEach{name->assertTrue(name,CategoryIcons.classify(name).badge.isNotBlank());assertTrue(CategoryIcons.label(name).endsWith(name))}
    for((term,badge) in listOf("SYRIA" to "🇸🇾","LEBANON" to "🇱🇧","NETFLIX" to "N","BEIN" to "beIN")){
        val matches=names.filter{it.contains(term,ignoreCase=true)&&!it.contains("USA")&&!it.contains("+")}
        if(matches.isNotEmpty())matches.forEach{assertEquals(it,badge,CategoryIcons.classify(it).badge)}
    }
 }
 @Test fun manifestUpdateComparison(){
    assertTrue(AppUpdater.newer("""{"versionCode":10}""",9))
    assertFalse(AppUpdater.newer("""{"versionCode":10}""",10))
    assertFalse(AppUpdater.newer("""{"versionCode":9}""",10))
 }
}
