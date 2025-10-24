package de.westnordost.streetmeasure

import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import kotlin.math.abs

class PolygonMeasurementState {
    // plane all points must lie on
    var plane: Plane? = null

    // ordered vertices in tap order
    val anchors = mutableListOf<Anchor>()

    fun clear() {
        anchors.forEach { it.detach() }
        anchors.clear()
        plane = null
    }

    fun addAnchor(newAnchor: Anchor, hitPlane: Plane): Boolean {
        // first point: lock plane
        if (plane == null) {
            plane = hitPlane
        }
        // reject if user taps a different plane
        if (plane != hitPlane) {
            return false
        }
        anchors.add(newAnchor)
        return true
    }

    fun vertexWorldPoses(): List<Pose> {
        return anchors.map { it.pose }
    }

    /**
     * Compute polygon area in square meters using shoelace formula.
     * Steps:
     * 1. Build a 2D coordinate frame on the locked plane.
     * 2. Project each vertex world pose into that 2D frame.
     * 3. Run shoelace.
     */
    fun areaSqMeters(): Float {
        if (anchors.size < 3 || plane == null) return 0f

        val p = plane ?: return 0f

        // Plane pose gives us origin + orientation
        val planePose = p.centerPose

        // Build basis vectors (u,v) lying in plane from planePose rotation.
        // We'll treat X and Z of planePose as spanning the plane. For a horizontal plane,
        // X = right, Z = forward-ish.
        val basisX = planePose.getXAxis()
        val basisZ = planePose.getZAxis()

        // Project each world pose into 2D coords (u along X, v along Z)
        val poly2d = mutableListOf<Pair<Float, Float>>()
        anchors.forEach { anchor ->
            val wp = anchor.pose.translation
            // vector from plane origin to this point
            val vx = wp[0] - planePose.tx()
            val vy = wp[1] - planePose.ty()
            val vz = wp[2] - planePose.tz()
            // dot with basis
            val u = vx * basisX[0] + vy * basisX[1] + vz * basisX[2]
            val v = vx * basisZ[0] + vy * basisZ[1] + vz * basisZ[2]
            poly2d.add(u to v)
        }

        // Shoelace
        var sum = 0f
        for (i in poly2d.indices) {
            val (x1, y1) = poly2d[i]
            val (x2, y2) = poly2d[(i + 1) % poly2d.size]
            sum += (x1 * y2 - x2 * y1)
        }
        return abs(sum) * 0.5f
    }

    fun areaSqFeet(): Float {
        return areaSqMeters() * 10.7639f
    }
}
