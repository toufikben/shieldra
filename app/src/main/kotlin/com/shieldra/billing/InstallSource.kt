package com.shieldra.billing

import android.content.Context
import android.os.Build

/** Whether this copy of the app was installed by Google Play (a sideloaded test build was not). */
object InstallSource {
    private const val PLAY_STORE = "com.android.vending"

    fun isGooglePlay(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE
    }.getOrDefault(false)
}
