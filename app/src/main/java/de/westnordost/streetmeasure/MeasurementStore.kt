package de.westnordost.streetmeasure

object MeasurementStore {
    private val measurements = mutableListOf<MeasurementRecord>()
    
    fun add(measurement: MeasurementRecord) {
        measurements.add(measurement)
    }
    
    fun getAll(): List<MeasurementRecord> {
        return measurements.toList()
    }
    
    fun clear() {
        measurements.clear()
    }
}
