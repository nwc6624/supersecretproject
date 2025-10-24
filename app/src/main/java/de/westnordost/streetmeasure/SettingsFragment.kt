package de.westnordost.streetmeasure

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*

class SettingsFragment : PreferenceFragmentCompat() {
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        
        // Set up summary providers for ListPreferences
        setupSummaryProviders()
    }
    
    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
    
    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
    
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "pref_theme_mode" -> {
                val themeMode = sharedPreferences.getString(key, "system")
                applyThemeMode(themeMode)
            }
            "pref_units", "pref_precision", "pref_marker_color", "pref_outline_color" -> {
                // Update summary for ListPreferences
                findPreference<ListPreference>(key)?.let { preference ->
                    preference.summary = preference.entry
                }
            }
            "pref_tile_length", "pref_tile_width" -> {
                // Update summary for EditTextPreferences
                findPreference<EditTextPreference>(key)?.let { preference ->
                    preference.summary = preference.text ?: "Not set"
                }
            }
        }
    }
    
    private fun setupSummaryProviders() {
        // ListPreferences
        listOf("pref_units", "pref_precision", "pref_marker_color", "pref_outline_color").forEach { key ->
            findPreference<ListPreference>(key)?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        
        // EditTextPreferences
        listOf("pref_tile_length", "pref_tile_width").forEach { key ->
            findPreference<EditTextPreference>(key)?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
                preference.text ?: "Not set"
            }
        }
        
        // Set up click listeners for special preferences
        findPreference<Preference>("pref_clear_history")?.setOnPreferenceClickListener {
            showClearHistoryDialog()
            true
        }
        
        findPreference<Preference>("pref_export_history")?.setOnPreferenceClickListener {
            showExportDialog()
            true
        }
        
        findPreference<Preference>("pref_reset")?.setOnPreferenceClickListener {
            showResetDialog()
            true
        }
        
        findPreference<Preference>("pref_about")?.setOnPreferenceClickListener {
            showAboutDialog()
            true
        }
    }
    
    private fun applyThemeMode(themeMode: String?) {
        when (themeMode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Saved Measurements")
            .setMessage("Are you sure you want to delete all saved measurement history? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                MeasurementStore.clear()
                Toast.makeText(requireContext(), "Measurement history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showExportDialog() {
        Toast.makeText(requireContext(), "Export feature coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun showResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset All Settings")
            .setMessage("Are you sure you want to reset all settings to their default values?")
            .setPositiveButton("Reset") { _, _ ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                prefs.edit().clear().apply()
                Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAboutDialog() {
        val versionName = BuildConfig.VERSION_NAME
        AlertDialog.Builder(requireContext())
            .setTitle("About TileVision")
            .setMessage("Version: $versionName\n\nTileVision helps measure surfaces in AR for tile, flooring, and countertops.")
            .setPositiveButton("OK", null)
            .show()
    }
}
