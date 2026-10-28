package com.alaa.universe

import android.app.Activity
import android.os.Bundle
import android.view.View

class HomeActivity : Activity() {
    lateinit var screen: UniverseScreen
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        screen = UniverseScreen(this)
        setContentView(screen)
        screen.home()
    }
    @Deprecated("TV Back compatibility")
    override fun onBackPressed() { if (!screen.back()) super.onBackPressed() }
}
