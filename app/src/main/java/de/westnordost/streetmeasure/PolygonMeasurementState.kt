package de.westnordost.streetmeasure

import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose

class PolygonMeasurementState {
    var plane: Plane? = null
    val anchors = mutableListOf<Anchor>()

    fun clear() {
        anchors.forEach { it.detach() }
        anchors.clear()
        plane = null
    }

    // Call this on each tap result.
    // hitPlane is the Plane from the HitResult.
    // Returns true if anchor is accepted, false if rejected (wrong plane).
    fun addAnchor(newAnchor: Anchor, hitPlane: Plane): Boolean {
        if (plane == null) {
            plane = hitPlane
        }
        if (plane != hitPlane) {
            return false
        }
        anchors.add(newAnchor)
        return true
    }

    fun vertexWorldPoses(): List<Pose> {
        return anchors.map { it.pose }
    }

    // Compute polygon area in square meters using shoelace formula.
    // Steps:
    // 1. Build a local 2D coordinate system from the plane pose.
    // 2. Project each world anchor onto that 2D plane.
    // 3. Run shoelace on the resulting 2D polygon.
    fun areaSqMeters(): Float {
        if (anchors.size < 3 || plane == null) return 0f
        val p = plane ?: return 0f

        val planePose = p.centerPose

        // Basis vectors in plane: X and Z from the plane pose.
        val xAxis = planePose.getXAxis()
        val zAxis = planePose.getZAxis()

        val originX = planePose.tx()
        val originY = planePose.ty()
        val originZ = planePose.tz()

        val poly2d = mutableListOf<Pair<Float, Float>>()

        for (anchor in anchors) {
            val wp = anchor.pose.translation
            val vx = wp[0] - originX
            val vy = wp[1] - originY
            val vz = wp[2] - originZ

            val u = vx * xAxis[0] + vy * xAxis[1] + vz * xAxis[2]
            val v = vx * zAxis[0] + vy * zAxis[1] + vz * zAxis[2]

            poly2d.add(u to v)
        }

        var sum = 0f
        for (i in poly2d.indices) {
            val (x1, y1) = poly2d[i]
            val (x2, y2) = poly2d[(i + 1) % poly2d.size]
            sum += (x1 * y2 - x2 * y1)
        }

        return kotlin.math.abs(sum) * 0.5f
    }

    fun areaSqFeet(): Float {
        return areaSqMeters() * 10.7639f
    }
}
