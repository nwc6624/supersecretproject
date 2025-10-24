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
        
        // Get area data from intent extras
        areaSqFeet = intent.getFloatExtra(EXTRA_AREA_SQ_FEET, 0f)
        areaSqMeters = intent.getFloatExtra(EXTRA_AREA_SQ_METERS, 0f)
        
        // Display the values
        displayAreaValues()
        
        // Save measurement to store
        saveMeasurement()
        
        // Setup click listeners
        setupClickListeners()
    }
    
    private fun displayAreaValues() {
        binding.tvAreaSqFeet.text = "${String.format("%.1f", areaSqFeet)} ft²"
        binding.tvAreaSqMeters.text = "(${String.format("%.1f", areaSqMeters)} m²)"
    }
    
    private fun saveMeasurement() {
        val measurementRecord = MeasurementRecord(
            areaSqFt = areaSqFeet,
            timestampMillis = System.currentTimeMillis()
        )
        MeasurementStore.add(measurementRecord)
    }
    
    private fun setupClickListeners() {
        findViewById<Button>(R.id.goToCalculatorButton).setOnClickListener {
            val i = Intent(this, TileCalculatorActivity::class.java)
            i.putExtra("areaSqFeet", areaSqFeet)
            startActivity(i)
        }
        
        binding.btnDone.setOnClickListener {
            finish()
        }
    }
    
}
