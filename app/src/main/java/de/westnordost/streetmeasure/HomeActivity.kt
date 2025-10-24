package de.westnordost.streetmeasure

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.westnordost.streetmeasure.databinding.ActivityHomeBinding
import java.util.Date

class HomeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityHomeBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
    }
    
    override fun onResume() {
        super.onResume()
        populateRecentMeasurements()
    }
    
    private fun setupClickListeners() {
        binding.btnStartMeasurement.setOnClickListener {
            startMeasurement()
        }
        
        findViewById<Button>(R.id.openCalculatorButton).setOnClickListener {
            val intent = Intent(this, TileCalculatorActivity::class.java)
            // do NOT put area extra here, user will type manually
            startActivity(intent)
        }
    }
    
    private fun startMeasurement() {
        val intent = Intent(this, MeasureActivity::class.java)
        // Force the measuring activity into surface area mode
        intent.putExtra(MeasureActivity.EXTRA_FORCE_SURFACE_MODE, true)
        startActivity(intent)
    }
    
    private fun populateRecentMeasurements() {
        val recentContainer = binding.recentContainer
        recentContainer.removeAllViews()
        
        val measurements = MeasurementStore.getAll()
        
        if (measurements.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No recent measurements"
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                setPadding(0, 16, 0, 16)
            }
            recentContainer.addView(emptyText)
        } else {
            // Show most recent measurements first
            measurements.reversed().forEach { measurement ->
                val measurementText = TextView(this).apply {
                    text = formatMeasurement(measurement)
                    textSize = 16f
                    setTextColor(resources.getColor(android.R.color.black, null))
                    setPadding(0, 8, 0, 8)
                }
                recentContainer.addView(measurementText)
            }
        }
    }
    
    private fun formatMeasurement(measurement: MeasurementRecord): String {
        val date = Date(measurement.timestampMillis)
        val dateFormat = DateFormat.getDateFormat(this)
        val timeFormat = DateFormat.getTimeFormat(this)
        val formattedDate = dateFormat.format(date)
        val formattedTime = timeFormat.format(date)
        
        return "${measurement.areaSqFt} ft² • $formattedDate $formattedTime"
    }
}
