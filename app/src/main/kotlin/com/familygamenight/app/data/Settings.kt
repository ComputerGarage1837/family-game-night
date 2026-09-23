package com.familygamenight.app.data

import android.content.Context

/** How long computer players take over each move. Fast is how the first version played. */
enum class AiSpeed(val label: String, val delayMs: Long) {
    RELAXED("Relaxed", 3000),
    NORMAL("Normal", 2100),
    FAST("Fast", 1400),
}

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

    var aiSpeed: AiSpeed
        get() = runCatching { AiSpeed.valueOf(prefs.getString("ai_speed", null) ?: "") }.getOrDefault(AiSpeed.NORMAL)
        set(v) = prefs.edit().putString("ai_speed", v.name).apply()

    var lastJoinAddress: String
        get() = prefs.getString("last_join", "") ?: ""
        set(v) = prefs.edit().putString("last_join", v).apply()
}
