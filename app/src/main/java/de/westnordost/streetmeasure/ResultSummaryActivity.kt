package de.westnordost.streetmeasure

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.westnordost.streetmeasure.databinding.ActivityResultSummaryBinding

class ResultSummaryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityResultSummaryBinding
    private var areaSqFeet: Float = 0f
    private var areaSqMeters: Float = 0f
    
    companion object {
        const val EXTRA_AREA_SQ_FEET = "area_sq_feet"
        const val EXTRA_AREA_SQ_METERS = "area_sq_meters"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get area data from intent extras - enforce polygon usage
        val areaFt2 = intent.getFloatExtra("areaSqFeet", 0f)
        val areaM2 = intent.getFloatExtra("areaSqMeters", 0f)
        
        // Store for use in click listeners
        areaSqFeet = areaFt2
        areaSqMeters = areaM2
        
        // Display the values
        displayAreaValues()
        
        // Save measurement to store
        saveMeasurement()
        
        // Setup click listeners
        setupClickListeners()
    }
    
    private fun displayAreaValues() {
        // Display big bold area in square feet
        binding.tvAreaSqFeet.text = "${String.format("%.1f", areaSqFeet)} ft²"
        
        // Display smaller secondary area in square meters
        binding.tvAreaSqMeters.text = "(${String.format("%.1f", areaSqMeters)} m²)"
        
        // TODO: If areaSqFeet == 0f, confirm that MeasureActivity is correctly passing extras
        if (areaSqFeet == 0f) {
            // This indicates something broke in the measurement flow
            // The area should never be 0 for a valid polygon measurement
        }
    }
    
    private fun saveMeasurement() {
        val measurementRecord = MeasurementRecord(
            areaSqFt = areaSqFeet,
            timestampMillis = System.currentTimeMillis()
        )
        MeasurementStore.add(measurementRecord)
    }
    
    private fun setupClickListeners() {
        // "Use in Tile Calculator" button - launches TileCalculatorActivity with areaFt2
        findViewById<Button>(R.id.goToCalculatorButton).setOnClickListener {
            val i = Intent(this, TileCalculatorActivity::class.java)
            i.putExtra("areaSqFeet", areaSqFeet)
            startActivity(i)
        }
        
        // "Done" button - finish and return Home
        binding.btnDone.setOnClickListener {
            finish()
        }
    }
    
}
