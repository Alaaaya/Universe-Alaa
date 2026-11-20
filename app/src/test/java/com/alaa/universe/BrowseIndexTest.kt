package com.alaa.universe

import org.junit.Assert.*
import org.junit.Test

class BrowseIndexTest {
    private fun snapshot()=ProviderSnapshot("Large fixture",null,
        (0 until 9175).map{MediaEntry("$it","Channel $it","${it%20}","live")}+
            (0 until 10).map{MediaEntry("m$it","Movie $it","0","movie")},
        (0 until 20).map{MediaCategory("$it","Group $it","live")})
    @Test fun indexFiltersOnceAndOrdinaryPagesNeverRescanCatalogue() {
        var visibilityChecks=0
        val index=BrowseIndex(snapshot(),{visibilityChecks++;it.categoryId!="19"})
        assertEquals(9185,visibilityChecks)
        val all=index.select("live","",emptySet(),emptySet())
        val group=index.select("live","2",emptySet(),emptySet())
        repeat(1000){
            assertSame(all,index.select("live","",emptySet(),emptySet()))
            assertSame(group,index.select("live","2",emptySet(),emptySet()))
            assertEquals(12,BrowseIndex.page(all,7000,12).size)
        }
        assertEquals(9185,visibilityChecks)
        assertEquals(10,index.count("movie"));assertEquals(6,index.home.size)
        assertTrue(group.all{it.categoryId=="2"})
    }
    @Test fun pageAccessIsBoundedAtDeepOffsetsAndEnds() {
        var reads=0
        val source=object:AbstractList<Int>(){
            override val size=9175
            override fun get(index:Int):Int { reads++;return index }
        }
        assertEquals((9000..9011).toList(),BrowseIndex.page(source,9000,12).toList())
        assertEquals(12,reads)
        assertEquals(5,BrowseIndex.page(source,9170,12).size)
        assertTrue(BrowseIndex.page(source,20000,12).isEmpty())
    }
    @Test fun favoritesRecentsAndSavedOrderKeepKindAndPrimaryCategorySemantics() {
        val index=BrowseIndex(snapshot(),{true})
        val f=index.select("live","favorites",setOf("live:1","live:21","movie:m1"),emptySet())
        assertEquals(listOf("1","21"),f.map{it.id})
        assertEquals(listOf("21","1"),BrowseIndex.ordered(f,listOf("21","missing","1")).map{it.id})
        assertEquals(listOf("2"),index.select("live","recent",emptySet(),setOf("live:2")).map{it.id})
        assertTrue(index.select("series","",emptySet(),emptySet()).isEmpty())
    }
    @Test fun visibilityAndHiddenCategoryNamesAreBothExcluded() {
        val s=snapshot()
        val index=BrowseIndex(s,{it.categoryId!="1"},{it.id!="1"})
        assertFalse(index.all.any{it.categoryId=="1"})
        assertFalse(index.categories["live"]!!.any{it.id=="1"})
    }
}
