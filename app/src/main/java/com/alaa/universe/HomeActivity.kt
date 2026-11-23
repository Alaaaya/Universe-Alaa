package com.alaa.universe

import android.app.Activity
import android.os.Bundle
import android.view.View

class HomeActivity : Activity() {
    lateinit var updater:AppUpdater
    lateinit var screen: UniverseScreen
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        com.bumptech.glide.Glide.init(applicationContext,com.bumptech.glide.GlideBuilder().setLogLevel(android.util.Log.ERROR))
        screen = UniverseScreen(this)
        setContentView(screen)
        screen.home()
        updater=AppUpdater(this)
        if(!packageName.endsWith(".atlaspreview"))updater.check(quiet=true)
    }
    override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {
        // Window dispatch can skip an unfocused content tree after controls auto-hide.
        if(::screen.isInitialized && !screen.previewFixtures && screen.provider.fullscreenKey(event))return true
        return super.dispatchKeyEvent(event)
    }
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){
        super.onConfigurationChanged(newConfig)
        screen.requestLayout()
    }
    override fun onStart(){super.onStart();screen.provider.start()}
    override fun onStop(){screen.provider.stop();super.onStop()}
    override fun onResume(){super.onResume();if(::updater.isInitialized)updater.resume()}
    override fun onDestroy(){if(::updater.isInitialized)updater.close();screen.provider.close();super.onDestroy()}
    @Deprecated("TV Back compatibility")
    private var lastBack = 0L
    override fun onBackPressed() {
        if (screen.back()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBack < 2000) { finish(); return }
        lastBack = now
        PremiumDialogs.toast(this, "اضغط رجوع مرة تانية للخروج")
        screen.provider.start()
    }
}
