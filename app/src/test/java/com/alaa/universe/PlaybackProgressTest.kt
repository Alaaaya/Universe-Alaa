package com.alaa.universe

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PlaybackProgressTest {
    @Test fun searchCoversOnlyChannelsMoviesAndSeriesCaseInsensitively() {
        val items=listOf(
            MediaEntry("c","Sea News","1","live"),
            MediaEntry("m1","Sea at Night","1","movie"),
            MediaEntry("s","Seasons","1","series"),
            MediaEntry("e","Sea episode","1","episode")
        )
        assertEquals(listOf("c","m1","s"),MediaQuickSearch.search(items,"sea").map{it.id})
        assertEquals("c",MediaQuickSearch.search(items," sea news ").single().id)
        assertTrue(MediaQuickSearch.search(items," ").isEmpty())
    }

    @Test fun tenThousandChannelBroadSearchKeepsStablePrefixFirstResultsAndIsBounded() {
        val channels=(0 until 10_000).map { i ->
            MediaEntry("c$i", if (i % 97 == 0) "News Channel $i" else "Channel News $i", "1", "live")
        }
        val samples=50
        val started=System.nanoTime()
        repeat(samples) {
            val result=MediaQuickSearch.search(channels,"news")
            assertEquals(60,result.size)
            assertTrue(result.takeWhile { it.name.startsWith("News", ignoreCase=true) }
                .all { it.name.startsWith("News", ignoreCase=true) })
            assertEquals((0..59).map { "c${it*97}" },result.takeWhile { it.name.startsWith("News", ignoreCase=true) }.map { it.id })
        }
        val elapsedMs=(System.nanoTime()-started)/1_000_000.0
        System.out.println("10k channel broad-search mean: ${"%.3f".format(java.util.Locale.ROOT,elapsedMs/samples)} ms over $samples runs (host JVM; not Android-device timing)")
    }

    @Test fun sortModesAreStableUnknownMetadataIsLastAndLiveOrderIsUntouched() {
        val items=listOf(
            MediaEntry("unknown","Unknown","1","movie"),
            MediaEntry("old","Older","1","movie",releaseYear=2001,addedTimestamp=100,viewCount=5),
            MediaEntry("new","Newer","1","movie",releaseYear=2024,addedTimestamp=300,viewCount=100),
            MediaEntry("middle","Middle","1","movie",releaseYear=2018,addedTimestamp=200,viewCount=20)
        )
        assertEquals(listOf("new","middle","old","unknown"),MediaSort.sort(items,"movie","year").map{it.id})
        assertEquals(listOf("new","middle","old","unknown"),MediaSort.sort(items,"movie","newest").map{it.id})
        assertEquals(listOf("new","middle","old","unknown"),MediaSort.sort(items,"movie","popular").map{it.id})
        assertEquals(items,MediaSort.sort(items,"live","year"))
        assertEquals(items,MediaSort.sort(items,"series","default"))
    }

    @Test fun resumeCheckpointSurvivesRelaunchWithoutStreamUrlsOrCredentials() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("playback-progress-v1",0).edit().clear().commit()
        val first=PlaybackProgressStore(context,"provider/username secret")
        first.save(MediaEntry("movie-7","A film","12","movie",directUrl="https://user:pass@example.test/film?token=secret"),2_400_000,5_000_000)
        val reopened=PlaybackProgressStore(context,"provider/username secret")
        val point=reopened.read()!!
        assertEquals("movie-7",point.id);assertEquals(2_400_000,point.positionMs)
        assertEquals("40:00",PlaybackProgressStore.formatTime(point.positionMs))
        val raw=context.getSharedPreferences("playback-progress-v1",0).all.values.joinToString()
        assertFalse(raw.contains("example.test"));assertFalse(raw.contains("secret"));assertFalse(raw.contains("pass"))
        reopened.clear(MediaEntry("different","Other","12","movie"));assertNotNull(reopened.read())
        reopened.clear(MediaEntry("movie-7","A film","12","movie"));assertNull(reopened.read())
    }

    @Test fun resumeIsNotRetainedForLiveOrNearCompletedVods() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("playback-progress-v1",0).edit().clear().commit()
        val store=PlaybackProgressStore(context,"scope")
        store.save(MediaEntry("live","Live","1","live"),50_000,100_000)
        assertNull(store.read())
        store.save(MediaEntry("movie","Movie","1","movie"),95_000,100_000)
        assertNull(store.read())
    }
}
