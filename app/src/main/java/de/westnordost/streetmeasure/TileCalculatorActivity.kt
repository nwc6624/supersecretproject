package de.westnordost.streetmeasure

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class TileCalculatorActivity : AppCompatActivity() {
    
    // Views
    private lateinit var measuredAreaText: TextView
    private lateinit var manualAreaInput: EditText
    private lateinit var tileWidthInput: EditText
    private lateinit var tileHeightInput: EditText
    private lateinit var wasteInput: EditText
    private lateinit var boxCoverageInput: EditText
    private lateinit var calcButton: Button
    private lateinit var tilesNeededText: TextView
    private lateinit var boxesNeededText: TextView
    
    private var incomingArea: Float = 0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_calculator)
        
        // Grab views
        measuredAreaText = findViewById(R.id.measuredAreaText)
        manualAreaInput = findViewById(R.id.manualAreaInput)
        tileWidthInput = findViewById(R.id.tileWidthInput)
        tileHeightInput = findViewById(R.id.tileHeightInput)
        wasteInput = findViewById(R.id.wasteInput)
        boxCoverageInput = findViewById(R.id.boxCoverageInput)
        calcButton = findViewById(R.id.calcButton)
        tilesNeededText = findViewById(R.id.tilesNeededText)
        boxesNeededText = findViewById(R.id.boxesNeededText)
        
        // Read incoming area
        incomingArea = intent.getFloatExtra("areaSqFeet", 0f)
        
        // Set up area display
        if (incomingArea > 0) {
            measuredAreaText.text = String.format("%.1f ft²", incomingArea)
            manualAreaInput.setText("")  // leave manual empty, incoming wins
        } else {
            measuredAreaText.text = "—"
            manualAreaInput.setText("")  // user must type
        }
        
        // Highlight required fields
        highlightRequiredFields()
        
        // Set click listener
        calcButton.setOnClickListener {
            calculateTiles()
        }
    }
    
    private fun calculateTiles() {
        // Clear previous highlights
        clearHighlights()
        
        // 1. Determine areaFt2
        val areaFt2 = if (incomingArea > 0) {
            incomingArea
        } else {
            try {
                val manualArea = manualAreaInput.text.toString().toFloat()
                if (manualArea <= 0) {
                    highlightField(manualAreaInput, "Enter area in ft² first")
                    return
                }
                manualArea
            } catch (e: NumberFormatException) {
                highlightField(manualAreaInput, "Enter area in ft² first")
                return
            }
        }
        
        // 2. Read tile dimensions
        val tileWidthIn = try {
            tileWidthInput.text.toString().toFloat()
        } catch (e: NumberFormatException) {
            0f
        }
        
        val tileHeightIn = try {
            tileHeightInput.text.toString().toFloat()
        } catch (e: NumberFormatException) {
            0f
        }
        
        if (tileWidthIn <= 0 || tileHeightIn <= 0) {
            if (tileWidthIn <= 0) highlightField(tileWidthInput, "Enter tile width")
            if (tileHeightIn <= 0) highlightField(tileHeightInput, "Enter tile height")
            return
        }
        
        // 3. Read waste percent
        val wastePercent = try {
            wasteInput.text.toString().toFloat()
        } catch (e: NumberFormatException) {
            10f
        }
        
        // 4. Read box coverage
        val boxCoverageFt2 = try {
            boxCoverageInput.text.toString().toFloat()
        } catch (e: NumberFormatException) {
            0f
        }
        
        // 5. Compute
        val tileAreaFt2 = (tileWidthIn / 12f) * (tileHeightIn / 12f)
        
        val rawTileCount = areaFt2 / tileAreaFt2
        
        val withWasteTileCount = rawTileCount * (1f + wastePercent / 100f)
        
        val tilesNeededRoundedUp = ceil(withWasteTileCount).toInt()
        
        val boxesNeededRoundedUp = if (boxCoverageFt2 > 0f) {
            val boxesRaw = (areaFt2 * (1f + wastePercent / 100f)) / boxCoverageFt2
            ceil(boxesRaw).toInt()
        } else {
            0
        }
        
        // 6. Update
        tilesNeededText.text = "Tiles Needed: $tilesNeededRoundedUp"
        boxesNeededText.text = "Boxes Needed: $boxesNeededRoundedUp"
    }
    
    private fun ceil(x: Float): Float {
        return kotlin.math.ceil(x.toDouble()).toFloat()
    }
    
    private fun highlightRequiredFields() {
        // Highlight fields that need user input based on current state
        if (incomingArea <= 0) {
            highlightField(manualAreaInput, "Enter area manually")
        }
        highlightField(tileWidthInput, "Enter tile width")
        highlightField(tileHeightInput, "Enter tile height")
    }
    
    private fun highlightField(editText: EditText, message: String) {
        // Set red border to indicate required field
        editText.setBackgroundColor(Color.parseColor("#FFEBEE")) // Light red background
        editText.setHintTextColor(Color.parseColor("#D32F2F")) // Dark red hint text
        editText.hint = message
        
        // Request focus to draw attention
        editText.requestFocus()
    }
    
    private fun clearHighlights() {
        // Reset all fields to normal appearance
        val normalBackground = ContextCompat.getColor(this, android.R.color.white)
        val normalHintColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        
        manualAreaInput.setBackgroundColor(normalBackground)
        manualAreaInput.setHintTextColor(normalHintColor)
        manualAreaInput.hint = "Or enter area manually (ft²)"
        
        tileWidthInput.setBackgroundColor(normalBackground)
        tileWidthInput.setHintTextColor(normalHintColor)
        tileWidthInput.hint = "Width (in)"
        
        tileHeightInput.setBackgroundColor(normalBackground)
        tileHeightInput.setHintTextColor(normalHintColor)
        tileHeightInput.hint = "Height (in)"
    }
}
