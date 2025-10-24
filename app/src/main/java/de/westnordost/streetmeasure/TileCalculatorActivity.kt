package de.westnordost.streetmeasure

import android.app.AlertDialog
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
        
        // Initialize AppPrefs
        AppPrefs.init(this)
        
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
        
        // Load default tile sizes from preferences
        loadDefaultTileSizes()
        
        // Set up highlighting for required fields
        setupFieldHighlighting()
        
        // Set click listener
        calcButton.setOnClickListener {
            calculateTiles()
        }
    }
    
    private fun calculateTiles() {
        // 1. Determine areaFt2
        val areaFt2 = if (incomingArea > 0) {
            incomingArea
        } else {
            try {
                val manualArea = manualAreaInput.text.toString().toFloat()
                if (manualArea <= 0) {
                    Toast.makeText(this, "Enter area in ft² first", Toast.LENGTH_SHORT).show()
                    highlightField(manualAreaInput)
                    return
                }
                manualArea
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Enter area in ft² first", Toast.LENGTH_SHORT).show()
                highlightField(manualAreaInput)
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
            Toast.makeText(this, "Enter tile width/height", Toast.LENGTH_SHORT).show()
            if (tileWidthIn <= 0) highlightField(tileWidthInput)
            if (tileHeightIn <= 0) highlightField(tileHeightInput)
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
        
        // Apply grout gap if enabled
        val finalTileCount = if (AppPrefs.getIncludeGrout()) {
            withWasteTileCount * 1.05f // Add 5% for grout gaps
        } else {
            withWasteTileCount
        }
        
        val tilesNeededRoundedUp = if (AppPrefs.getForceRoundUp()) {
            ceil(finalTileCount).toInt()
        } else {
            kotlin.math.round(finalTileCount).toInt()
        }
        
        val boxesNeededRoundedUp = if (boxCoverageFt2 > 0f) {
            val boxesRaw = (areaFt2 * (1f + wastePercent / 100f)) / boxCoverageFt2
            ceil(boxesRaw).toInt()
        } else {
            0
        }
        
        // 6. Update
        tilesNeededText.text = "Tiles Needed: $tilesNeededRoundedUp"
        boxesNeededText.text = "Boxes Needed: $boxesNeededRoundedUp"
        
        // 7. Show results popup
        showResultsDialog(tilesNeededRoundedUp, boxesNeededRoundedUp, areaFt2, tileWidthIn, tileHeightIn, wastePercent)
    }
    
    private fun ceil(x: Float): Float {
        return kotlin.math.ceil(x.toDouble()).toFloat()
    }
    
    private fun loadDefaultTileSizes() {
        // Load default tile sizes from preferences if they exist
        val defaultLength = AppPrefs.getDefaultTileLength()
        val defaultWidth = AppPrefs.getDefaultTileWidth()
        
        if (defaultLength != null && tileWidthInput.text.isNullOrBlank()) {
            tileWidthInput.setText(defaultLength.toString())
        }
        
        if (defaultWidth != null && tileHeightInput.text.isNullOrBlank()) {
            tileHeightInput.setText(defaultWidth.toString())
        }
    }
    
    private fun setupFieldHighlighting() {
        // Highlight required fields that need user input
        if (incomingArea <= 0) {
            highlightField(manualAreaInput)
        }
        
        // Always highlight tile dimensions as they're always required
        highlightField(tileWidthInput)
        highlightField(tileHeightInput)
        
        // Add text change listeners to clear highlighting when fields are filled
        manualAreaInput.addTextChangedListener(createTextWatcher(manualAreaInput))
        tileWidthInput.addTextChangedListener(createTextWatcher(tileWidthInput))
        tileHeightInput.addTextChangedListener(createTextWatcher(tileHeightInput))
    }
    
    private fun highlightField(editText: EditText) {
        editText.setBackgroundColor(Color.parseColor("#FFF3CD")) // Light yellow highlight
        editText.setHintTextColor(Color.parseColor("#856404")) // Darker yellow for hint
    }
    
    private fun clearHighlight(editText: EditText) {
        editText.setBackgroundColor(Color.WHITE)
        editText.setHintTextColor(Color.parseColor("#757575")) // Default gray
    }
    
    private fun createTextWatcher(editText: EditText): android.text.TextWatcher {
        return object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrBlank()) {
                    clearHighlight(editText)
                }
            }
        }
    }
    
    private fun showResultsDialog(tilesNeeded: Int, boxesNeeded: Int, areaFt2: Float, tileWidth: Float, tileHeight: Float, wastePercent: Float) {
        val message = buildString {
            append("📐 **Calculation Results**\n\n")
            append("**Area to Cover:** ${String.format("%.1f", areaFt2)} ft²\n")
            append("**Tile Size:** ${String.format("%.1f", tileWidth)}\" × ${String.format("%.1f", tileHeight)}\"\n")
            append("**Waste Allowance:** ${String.format("%.0f", wastePercent)}%\n\n")
            append("**📊 Results:**\n")
            append("• **Tiles Needed:** $tilesNeeded\n")
            if (boxesNeeded > 0) {
                append("• **Boxes Needed:** $boxesNeeded\n")
            } else {
                append("• **Boxes Needed:** Not calculated (no coverage per box specified)\n")
            }
            append("\n💡 **Tip:** Always buy a few extra tiles for cutting and future repairs!")
        }
        
        AlertDialog.Builder(this)
            .setTitle("🎯 Tile Calculation Complete")
            .setMessage(message)
            .setPositiveButton("Got it!") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Recalculate") { dialog, _ ->
                dialog.dismiss()
                // Clear the results to encourage recalculation
                tilesNeededText.text = "Tiles Needed: --"
                boxesNeededText.text = "Boxes Needed: --"
            }
            .setCancelable(true)
            .show()
    }
}
