package com.alaa.universe

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

object PhoneInput {
    fun enabled(context:Context):Boolean {
        val mode=context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val configuredMode=context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return configuredMode!=Configuration.UI_MODE_TYPE_TELEVISION &&
            mode?.currentModeType!=Configuration.UI_MODE_TYPE_TELEVISION &&
            context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    }
}
