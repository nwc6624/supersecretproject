package de.westnordost.streetmeasure

object MeasurementStore {
    private val measurements = mutableListOf<MeasurementRecord>()
    
    fun add(record: MeasurementRecord) {
        measurements.add(record)
    }
    
    fun getAll(): List<MeasurementRecord> {
        return measurements.toList()
    }
}
