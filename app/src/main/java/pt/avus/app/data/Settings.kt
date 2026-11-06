package pt.avus.app.data

import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User preferences ("Definições"). */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Wallpaper-based colours (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Daily reminder about homes without an answer for a week. */
    val reminders: Boolean = true,
) {
    fun toJson(): String = JSONObject()
        .put("theme", theme.name).put("dynamicColor", dynamicColor).put("reminders", reminders)
        .toString()

    companion object {
        fun parse(text: String?): AppSettings {
            val o = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return AppSettings()
            return AppSettings(
                theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = o.optBoolean("dynamicColor", true),
                reminders = o.optBoolean("reminders", true),
            )
        }
    }
}
