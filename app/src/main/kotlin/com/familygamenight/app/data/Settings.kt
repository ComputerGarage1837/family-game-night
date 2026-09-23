package com.familygamenight.app.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var currentProfileId: String?
        get() = prefs.getString("current_profile", null)
        set(v) = prefs.edit().putString("current_profile", v).apply()

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean("auto_updates", true)
        set(v) = prefs.edit().putBoolean("auto_updates", v).apply()

    /** Release the user said "later" to, so we don't nag on every launch. */
    var skippedVersion: Int
        get() = prefs.getInt("skipped_version", 0)
        set(v) = prefs.edit().putInt("skipped_version", v).apply()

    var lastJoinAddress: String
        get() = prefs.getString("last_join", "") ?: ""
        set(v) = prefs.edit().putString("last_join", v).apply()
}
