package com.cyberzilla.islamicwidget

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object IconHelper {

    private const val TAG = "IconHelper"
    private const val PREF_PENDING_ICON_DAY = "pendingIconHijriDay"

    private fun isAppInForeground(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses ?: return false
            runningAppProcesses.any {
                it.processName == context.packageName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        } catch (e: Exception) {
            false
        }
    }

    fun updateLauncherIcon(context: Context, hijriDay: Int) {
        if (isAppInForeground(context)) {
            val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            prefs.edit().putInt(PREF_PENDING_ICON_DAY, hijriDay).apply()
            Log.d(TAG, "App is in foreground, deferred icon update to day $hijriDay (will execute on onStop)")
            return
        }

        performIconUpdate(context, hijriDay)
    }

    fun executePendingIconUpdate(context: Context) {
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        val pendingDay = prefs.getInt(PREF_PENDING_ICON_DAY, -1)
        if (pendingDay == -1) return

        prefs.edit().remove(PREF_PENDING_ICON_DAY).apply()

        Log.d(TAG, "Executing deferred icon update: day $pendingDay")
        performIconUpdate(context, pendingDay)
    }

    private fun performIconUpdate(context: Context, hijriDay: Int) {
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

        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("lastIconHijriDay", dayToSet).apply()

        Log.d(TAG, "Launcher icon updated to day $dayToSet")
    }
}

