package com.cyberzilla.islamicwidget

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object IconHelper {
    fun updateLauncherIcon(context: Context, hijriDay: Int) {
        val packageName = context.packageName
        val packageManager = context.packageManager
        val flags = PackageManager.DONT_KILL_APP

        val dayToSet = if (hijriDay in 1..30) hijriDay else 1

        val actions = mutableListOf<Pair<ComponentName, Int>>()

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (!isDebuggable) {
            val mainActivity = ComponentName(context, "$packageName.MainActivity")
            actions.add(Pair(mainActivity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
        }

        // 2. Siapkan status untuk ke-30 Alias
        for (i in 1..30) {
            val aliasName = "$packageName.MainActivityAlias$i"
            val componentName = ComponentName(packageName, aliasName)

            val newState = if (i == dayToSet) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            actions.add(Pair(componentName, newState))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = actions.map { (componentName, newState) ->
                PackageManager.ComponentEnabledSetting(componentName, newState, flags)
            }
            packageManager.setComponentEnabledSettings(settings)
        } else {
            actions.forEach { (componentName, newState) ->
                try {
                    packageManager.setComponentEnabledSetting(componentName, newState, flags)
                } catch (e: Exception) {}
            }
        }
    }
}