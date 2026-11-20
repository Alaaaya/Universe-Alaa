package com.alaa.universe

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1920dp-h1080dp-land-television-mdpi")
class ProviderBrowseCacheTest {
    private fun set(p:ProviderUi,name:String,value:Any)=ProviderUi::class.java.getDeclaredField(name).apply{isAccessible=true}.set(p,value)
    private fun get(p:ProviderUi,name:String)=ProviderUi::class.java.getDeclaredField(name).apply{isAccessible=true}.get(p)
    @Test fun categoryFirstAndDeepPageRedrawReuseExactOrderedListUntilSnapshotChanges() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen=UniverseScreen(activity);val p=screen.provider;p.testMode=true
        val snap=ProviderSnapshot("Fixture",null,(0 until 9175).map{MediaEntry("$it","Channel $it","g","live")},listOf(MediaCategory("g","Group","live")))
        set(p,"snapshot",snap)
        p.categories()
        assertTrue((get(p,"currentList") as List<*>).isEmpty())
        p.browse("live")
        val list=get(p,"currentList")
        set(p,"offset",9000)
        repeat(3){p.browse("live");assertSame(list,get(p,"currentList"))}
        val stage=screen.getChildAt(0) as ViewGroup
        val names=(0 until stage.childCount).map{stage.getChildAt(it)}.filterIsInstance<TextView>().map{it.text.toString()}.filter{it.startsWith("Channel ")}
        assertEquals((9000..9011).map{"Channel $it"},names)
        set(p,"snapshot",snap.copy(entries=snap.entries.take(2)))
        p.browse("live")
        assertNotSame(list,get(p,"currentList"));assertEquals(2,(get(p,"currentList") as List<*>).size)
        p.close();activity.finish()
    }
    @Test fun frozenPublicM3uIndexesActualLiveCountOnWorker() {
        val file=java.io.File("/home/kit/universe-perf/index.m3u")
        org.junit.Assume.assumeTrue(file.isFile)
        val hex=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
        assertEquals("4af19f1d79ea986c72109a70e47e11bb7eebb691880a3ea67994f685778d9240",hex)
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val vault=CredentialVault(context,{javax.crypto.spec.SecretKeySpec(ByteArray(16){9},"AES")},"browse-frozen-test")
        vault.clear()
        val repo=ProviderRepository(vault){file.readText()}
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val result=executor.submit<BrowseIndex>{
                val snap=repo.connect(ProviderLogin("Frozen fixture","",m3uUrl="https://fixture.invalid/list.m3u"))
                assertEquals(9219,snap.entries.size) // Exact SHA-pinned fixture, not the earlier mutable network count.
                val visibility=repo.visibility()
                BrowseIndex(snap,{visibility.allows(it)})
            }.get(60,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(9218,result.count("live"))
            assertEquals(1,result.count("movie"))
            assertEquals(12,BrowseIndex.page(result.select("live","",emptySet(),emptySet()),9000,12).size)
        } finally {executor.shutdownNow();vault.clear()}
    }

    @Test fun visibilityEpochInvalidatesIndexBeforeHiddenPolicyOrClearCanReuseIt() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val vault=CredentialVault(context,{javax.crypto.spec.SecretKeySpec(ByteArray(16){3},"AES")},"browse-policy-test")
        vault.clear()
        val repo=ProviderRepository(vault)
        val policy=CategoryPolicy("device","playlist","provider","rev1",mapOf("live" to emptySet()))
        repo.applyCategoryPolicy(policy)
        val before=repo.visibility()
        val entry=MediaEntry("1","Shared","7","live",categoryIds=setOf("7","8"))
        assertTrue(before.allows(entry))
        repo.applyCategoryPolicy(policy.copy(revision="rev2",hidden=mapOf("live" to setOf("8"))))
        assertNotEquals(before.revision,repo.visibilityRevision)
        assertFalse(repo.visibility().allows(entry))
        val hiddenEpoch=repo.visibilityRevision
        repo.clear();assertNotEquals(hiddenEpoch,repo.visibilityRevision)
    }
}
