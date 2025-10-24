package de.westnordost.streetmeasure

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object AppPrefs {
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }
    
    // Display preferences
    fun getUnits(): String = prefs.getString("pref_units", "imperial") ?: "imperial"
    
    fun getPrecision(): Int = prefs.getString("pref_precision", "2")?.toIntOrNull() ?: 2
    
    fun getThemeMode(): String = prefs.getString("pref_theme_mode", "system") ?: "system"
    
    // AR preferences
    fun getMarkerColor(): String = prefs.getString("pref_marker_color", "yellow") ?: "yellow"
    
    fun getOutlineColor(): String = prefs.getString("pref_outline_color", "orange") ?: "orange"
    
    fun getShowGrid(): Boolean = prefs.getBoolean("pref_show_grid", true)
    
    fun getShowLabels(): Boolean = prefs.getBoolean("pref_show_labels", true)
    
    // Tile Calculator preferences
    fun getDefaultTileLength(): Float? {
        val value = prefs.getString("pref_tile_length", "")
        return if (value.isNullOrBlank()) null else value.toFloatOrNull()
    }
    
    fun getDefaultTileWidth(): Float? {
        val value = prefs.getString("pref_tile_width", "")
        return if (value.isNullOrBlank()) null else value.toFloatOrNull()
    }
    
    fun getIncludeGrout(): Boolean = prefs.getBoolean("pref_include_grout", true)
    
    fun getForceRoundUp(): Boolean = prefs.getBoolean("pref_round_up", true)
    
    // Helper methods for color conversion
    fun getMarkerColorInt(): Int {
        return when (getMarkerColor()) {
            "yellow" -> android.graphics.Color.YELLOW
            "red" -> android.graphics.Color.RED
            "blue" -> android.graphics.Color.BLUE
            "green" -> android.graphics.Color.GREEN
            else -> android.graphics.Color.YELLOW
        }
    }
    
    fun getOutlineColorInt(): Int {
        return when (getOutlineColor()) {
            "orange" -> android.graphics.Color.parseColor("#FFA500")
            "cyan" -> android.graphics.Color.CYAN
            "lime" -> android.graphics.Color.parseColor("#00FF00")
            "white" -> android.graphics.Color.WHITE
            else -> android.graphics.Color.parseColor("#FFA500")
        }
    }
    
    // Helper method for formatting area with precision
    fun formatArea(area: Float): String {
        val precision = getPrecision()
        val units = getUnits()
        val unitSymbol = if (units == "imperial") "ft²" else "m²"
        return "%.${precision}f $unitSymbol".format(area)
    }
}
