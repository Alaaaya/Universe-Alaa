package com.alaa.universe

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MediaOrderTest {
    private fun prefs()=RuntimeEnvironment.getApplication().getSharedPreferences("order-test",0).also{it.edit().clear().commit()}
    private val scope=listOf("account", "live", "channels", "news")
    @Test fun saveSurvivesRestartAndRefreshWithNewRemovedAndReturningIds() {
        val prefs=prefs();val order=MediaOrder(prefs)
        assertTrue(order.save(scope,listOf("c","a","b")))
        val reopened=MediaOrder(prefs)
        assertEquals(listOf("c","a","b"),reopened.ids(scope,listOf("a","b","c")))
        assertEquals(listOf("c","a","d"),reopened.ids(scope,listOf("d","a","c")))
        assertTrue(reopened.save(scope,listOf("a","c","d")))
        assertEquals(listOf("a","c","d","b"),MediaOrder(prefs).ids(scope,listOf("b","d","c","a")))
    }
    @Test fun cancellationDoesNotPersistDraftOrLoseNewIds() {
        val order=MediaOrder(prefs());order.save(scope,listOf("c","b","a"))
        val move=MediaMove(scope,"b",order.ids(scope,listOf("a","b","c")))
        move.move(-1,listOf("a","b","c"))
        assertEquals(listOf("b","c","a","d"),move.ids(listOf("a","d","b","c")))
        assertEquals(listOf("c","b","a","d"),order.ids(scope,listOf("a","d","b","c")))
    }
    @Test fun scopesAreIsolatedByAccountKindAndCategory() {
        val order=MediaOrder(prefs());order.save(scope,listOf("b","a"))
        for(other in listOf(listOf("other","live","channels","news"),listOf("account","movie","channels","news"),listOf("account","live","channels","sport"),listOf("account","live","categories")))
            assertEquals(listOf("a","b"),order.ids(other,listOf("a","b")))
    }
    @Test fun missingMovedItemAndBoundariesAreSafeAndDuplicatesRemoved() {
        val move=MediaMove(scope,"b",listOf("a","b","c"))
        repeat(5){move.move(1,listOf("a","b","c"))}
        assertEquals(listOf("a","c","b"),move.draft)
        move.move(-1,listOf("a","c","d"))
        assertEquals(listOf("a","c","d"),move.ids(listOf("a","c","d","d")))
    }
    @Test fun encryptedProviderCacheReopenAndRefreshKeepSavedIdsAndAppendNewOnes() {
        val context=RuntimeEnvironment.getApplication()
        val secret=javax.crypto.KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
        val vault=CredentialVault(context){secret};vault.clear()
        var refresh=false
        val repo=ProviderRepository(vault){
            if(refresh)"#EXTM3U\n#EXTINF:-1 group-title=\"News\",C\nhttps://example.test/c\n#EXTINF:-1 group-title=\"News\",A\nhttps://example.test/a\n#EXTINF:-1 group-title=\"News\",B\nhttps://example.test/b"
            else "#EXTM3U\n#EXTINF:-1 group-title=\"News\",A\nhttps://example.test/a\n#EXTINF:-1 group-title=\"News\",B\nhttps://example.test/b"
        }
        val initial=repo.connect(ProviderLogin("Fixture","",m3uUrl="https://example.test/list"))
        val prefs=prefs();val order=MediaOrder(prefs)
        assertTrue(order.save(scope,initial.entries.reversed().map{it.id}))
        fun names(snapshot:ProviderSnapshot):List<String>{
            val byId=snapshot.entries.associateBy{it.id}
            return MediaOrder(prefs).ids(scope,snapshot.entries.map{it.id}).map{byId[it]!!.name}
        }
        assertEquals(listOf("B","A"),names(ProviderRepository(vault).cached()!!))
        refresh=true;assertEquals(listOf("B","A","C"),names(repo.refresh()))
        assertEquals(listOf("B","A","C"),names(ProviderRepository(vault).cached()!!))
        vault.clear()
    }

}
