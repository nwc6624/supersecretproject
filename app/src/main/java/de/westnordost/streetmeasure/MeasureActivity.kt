package de.westnordost.streetmeasure

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.HapticFeedbackConstants.VIRTUAL_KEY
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState.TRACKING
import com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import de.westnordost.streetmeasure.databinding.ActivityMeasureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** Activity to measure distances. Can be started as activity for result, which result in slightly
 *  different UX, too.  */
class MeasureActivity : AppCompatActivity(), Scene.OnUpdateListener {

    private val createArCoreSession = ArCoreSessionCreator(this)
    private var initSessionOnResume = true

    private lateinit var binding: ActivityMeasureBinding
    private var arSceneView: ArSceneView? = null

    private val prefs get() = getPreferences(Context.MODE_PRIVATE)

    private var cursorRenderable: Renderable? = null
    private var pointRenderable: Renderable? = null
    private var polygonLineRenderable: Renderable? = null
    private var polygonFillRenderable: Renderable? = null

    private var cursorNode: AnchorNode? = null

    // Polygon measurement nodes
    private val polygonNodes = mutableListOf<AnchorNode>()
    private val polygonLineNodes = mutableListOf<Node>()
    private var polygonFillNode: Node? = null

    private var requestResult: Boolean = false
    private var measuringTapeColor: Int = -1

    // Polygon measurement state for surface area mode
    private val polygonState = PolygonMeasurementState()
    private val measurementMode = MeasurementMode.SURFACE_AREA
    
    // Store polygon points for horizontal upward-facing planes only
    private val polygonPoints: MutableList<Vector3> = mutableListOf()

    // CHECKPOINT:
    // - User can tap 3+ points on the SAME plane.
    // - A flat outline draws around all taps, with a closing edge to the first tap.
    // - A translucent filled patch appears once there are 3+ points.
    // - Overlay text shows "Area: XX.X ft²".
    // - Accept button is enabled when >=3 points.
    // - Accept -> saves MeasurementRecord(...) -> launches ResultSummaryActivity with areaSqFeet/areaSqMeters -> finish()
    //
    // IMPORTANT: Do not reintroduce functionality that limits the app to 2 anchors, or that calculates only linear distance.
    // The ONLY AR workflow we support in TileVision is multi-point polygon surface area measurement for square footage, for tile planning.

    // Old measurement system code removed for surface area mode

    /* ---------------------------------------- Lifecycle --------------------------------------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemBarStyle = SystemBarStyle.dark(android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b))
        enableEdgeToEdge(systemBarStyle, systemBarStyle)
        super.onCreate(savedInstanceState)

        // no turning off screen automatically while measuring, also no colored navbar
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Initialize AppPrefs
        AppPrefs.init(this)
        
        readIntent()

        try {
            binding = ActivityMeasureBinding.inflate(layoutInflater)
        } catch (e: Exception) {
            /* layout inflation may fail for the ArSceneView for some old devices that don't support
               AR anyway. So we can just exit */
            finish()
            return
        }

        setContentView(binding.root)

        // Direction, unit, and flash buttons removed for surface area mode

        binding.backButton.setOnClickListener { finish() }
        binding.undoButton.setOnClickListener { undoLastPoint() }
        binding.confirmButton.setOnClickListener { confirmMeasurement() }

        // Initialize polygon measurement UI
        updatePolygonDisplay()
        updateButtonStates()

        if (savedInstanceState != null) {
            createArCoreSession.hasRequestedArCoreInstall = savedInstanceState.getBoolean(HAS_REQUESTED_AR_CORE_INSTALL)
        }
    }

    override fun onResume() {
        super.onResume()
        if (initSessionOnResume) {
            lifecycleScope.launch {
                initializeSession()
                initRenderables()
            }
        }
        if (arSceneView != null) {
            try {
                arSceneView?.resume()
                binding.handMotionView.isGone = false
                binding.trackingMessageTextView.isGone = true
            } catch (e: CameraNotAvailableException) {
                // without camera, we can't do anything, might as well quit
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        arSceneView?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putBoolean(HAS_REQUESTED_AR_CORE_INSTALL, createArCoreSession.hasRequestedArCoreInstall)
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView?.pause()
        arSceneView?.destroy()
        // closing can take several seconds, should be done one background thread that outlives this activity
        GlobalScope.launch(Dispatchers.Default) {
            arSceneView?.session?.close()
        }
    }

    private fun readIntent() {
        requestResult = intent.getBooleanExtra(PARAM_REQUEST_RESULT, false)

        val measuringTapeColorInt = intent.getIntExtra(PARAM_MEASURING_TAPE_COLOR, -1)
        measuringTapeColor = if (measuringTapeColorInt == -1) {
            android.graphics.Color.argb(255, 209, 64, 0)
        } else measuringTapeColorInt
    }

    /* ---------------------------------------- Buttons ----------------------------------------- */
    // Old button methods removed for surface area mode

    /* --------------------------------- Scene.OnUpdateListener --------------------------------- */

    override fun onUpdate(frameTime: FrameTime) {
        val frame = arSceneView?.arFrame ?: return

        if (frame.hasFoundPlane()) {
            binding.handMotionView.isGone = true
        }

        setTrackingMessage(frame.camera.trackingFailureReason.messageResId)

        if (frame.camera.trackingState == TRACKING) {
                hitPlaneAndUpdateCursor(frame)
                updateAreaBubblePosition()
        }
    }

    private fun hitPlaneAndUpdateCursor(frame: Frame) {
        val centerX = binding.arSceneViewContainer.width / 2f
        val centerY = binding.arSceneViewContainer.height / 2f
        val hitResults = frame.hitTest(centerX, centerY).filter {
            (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true
        }
        val hitResult = hitResults.firstOrNull()

        val cameraAngle = abs(normalizeRadians(frame.camera.displayOrientedPose.pitch.toDouble() + PI/2, -PI))
        /* Display warning if the camera angle is more than 55° from the ground:

           E.g. if the startpoint is placed 20cm above the ground (~kerb height) either because
           ARCore initially wrongly assumes a different plane height or by user error (starting
           to measure from sidewalk height but then to the lower end of the kerb on the other
           side) and the phone is held in a height of 140cm, that's already a measurement error
           of 20/140 / tan(90 - 55) = 20%
           (at most, i.e. the distance of the users feet to the arrow)
         */
        setTrackingMessage(if (cameraAngle > PI/2 * 55/90) R.string.ar_core_tracking_error_no_plane_hit else null)

        if (hitResult != null) {
            updateCursor(hitResult)

                setTrackingMessage(R.string.ar_core_tracking_hint_tap_to_measure)
        } else {
            /* when no plane can be found at the cursor position and the camera angle is
               shallow enough, display a hint that user should cross street
             */
            val cursorDistanceFromCamera = cursorNode?.worldPosition?.let {
                Vector3.subtract(frame.camera.pose.position, it).length()
            } ?: 0f

            if (cursorDistanceFromCamera > 3f) {
                setTrackingMessage(R.string.ar_core_tracking_error_no_plane_hit)
            }
        }
    }

    private fun setTrackingMessage(messageResId: Int?) {
        binding.trackingMessageTextView.isGone = messageResId == null
        messageResId?.let { binding.trackingMessageTextView.setText(messageResId) }
    }

    /* ------------------------------------------ Session --------------------------------------- */

    private suspend fun initializeSession() {
        initSessionOnResume = false
        val result = createArCoreSession()
        if (result is ArCoreSessionCreator.Success) {
            val session = result.session
            configureSession(session)
            addArSceneView(session)
        } else if (result is ArCoreSessionCreator.Failure) {
            val reason = result.reason
            if (reason == ArNotAvailableReason.AR_CORE_SDK_TOO_OLD) {
                Toast.makeText(this, R.string.ar_core_error_sdk_too_old, Toast.LENGTH_SHORT).show()
            } else if (reason == ArNotAvailableReason.NO_CAMERA_PERMISSION) {
                Toast.makeText(this, R.string.no_camera_permission_toast, Toast.LENGTH_SHORT).show()
            }
            // otherwise nothing we can do here...
            finish()
        } else {
            // and if it is null, we remember that we want to continue the session creation on
            // next onResume
            initSessionOnResume = true
        }
    }

    private fun configureSession(session: Session) {
        val config = Config(session)

        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE // necessary for Sceneform
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        // disabling unused features should make processing faster
        config.depthMode = Config.DepthMode.DISABLED
        config.cloudAnchorMode = Config.CloudAnchorMode.DISABLED
        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        config.flashMode = Config.FlashMode.OFF
        session.configure(config)

        session.cameraConfig.cameraId
    }

    // Flash methods removed for surface area mode

    private fun addArSceneView(session: Session) {
        val arSceneView = ArSceneView(this)
        arSceneView.planeRenderer.isEnabled = false
        binding.arSceneViewContainer.addView(arSceneView, MATCH_PARENT, MATCH_PARENT)
        arSceneView.setupSession(session)
        arSceneView.scene.addOnUpdateListener(this)
        arSceneView.setOnClickListener { onTapPlane() }
        this.arSceneView = arSceneView
    }

    /* ---------------------------------------- Measuring --------------------------------------- */

    private fun onTapPlane() {
        val frame = arSceneView?.arFrame ?: return
        val centerX = binding.arSceneViewContainer.width / 2f
        val centerY = binding.arSceneViewContainer.height / 2f
        
        // Perform hit test and filter for horizontal upward-facing planes only
        val hitResults = frame.hitTest(centerX, centerY).filter { hitResult ->
            val plane = hitResult.trackable as? Plane
            plane != null && 
            plane.type == HORIZONTAL_UPWARD_FACING && 
            plane.isPoseInPolygon(hitResult.hitPose)
        }
        
        val hitResult = hitResults.firstOrNull()
        if (hitResult == null) {
            Toast.makeText(this, "Tap on a floor or countertop surface", Toast.LENGTH_SHORT).show()
            return
        }
        
        val plane = hitResult.trackable as? Plane ?: return
        val anchor = hitResult.createAnchor()
        
        // Store the world position as Vector3 in polygonPoints
        val worldPosition = anchor.pose.translation
        val point = Vector3(worldPosition[0], worldPosition[1], worldPosition[2])
        polygonPoints.add(point)
        
        // Add anchor to polygon state
        val success = polygonState.addAnchor(anchor, plane)
        if (!success) {
            Toast.makeText(this, "Stay on the same surface", Toast.LENGTH_SHORT).show()
            anchor.detach()
            return
        }
        
        // Re-render the outline connecting all points in order
        renderPolygonOutline()
        
        // Calculate and display area if we have 3+ points
        if (polygonPoints.size >= 3) {
            calculateAndDisplayArea()
            renderPolygonFill()
        }
        
        // Update UI
        updatePolygonDisplay()
        updateButtonStates()
        updateAreaBubblePosition()
    }

    private fun renderPolygonOutline() {
        // Clear existing outline nodes
        clearPolygonLineNodes()
        clearPolygonPointMarkers()
        
        if (polygonPoints.size < 1) return
        
        // Draw point markers at each vertex
        for (point in polygonPoints) {
            val markerNode = createPointMarker(point)
            polygonNodes.add(markerNode)
        }
        
        if (polygonPoints.size < 2) return
        
        // Connect all points in order
        for (i in 0 until polygonPoints.size) {
            val currentPoint = polygonPoints[i]
            val nextPoint = polygonPoints[(i + 1) % polygonPoints.size]
            
            // Only draw closing line if we have 3+ points
            if (i == polygonPoints.size - 1 && polygonPoints.size < 3) continue
            
            val lineNode = drawEdge(currentPoint, nextPoint)
            polygonLineNodes.add(lineNode)
        }
    }
    
    private fun createPointMarker(point: Vector3): AnchorNode {
        val anchor = arSceneView?.session?.createAnchor(
            Pose.makeTranslation(point.x, point.y, point.z)
        ) ?: return AnchorNode()
        
        return AnchorNode(anchor).apply {
            renderable = pointRenderable
            setParent(arSceneView?.scene)
        }
    }
    
    private fun clearPolygonPointMarkers() {
        polygonNodes.forEach { node ->
            node.anchor?.detach()
            node.setParent(null)
        }
        polygonNodes.clear()
    }
    
    private fun renderPolygonFill() {
        if (polygonPoints.size < 3) return
        
        // Clear existing fill node
        polygonFillNode?.setParent(null)
        polygonFillNode = null
        
        // Get the plane from polygonState
        val plane = polygonState.plane ?: return
        val planePose = plane.centerPose
        
        // Project all points to 2D plane coordinates
        val points2D = mutableListOf<Pair<Float, Float>>()
        for (point in polygonPoints) {
            val worldPos = point
            val planePos = planePose.inverse().compose(Pose.makeTranslation(worldPos.x, worldPos.y, worldPos.z))
            points2D.add(Pair(planePos.tx(), planePos.tz()))
        }
        
        // Calculate centroid and bounding box
        val centroidX = points2D.map { it.first }.average().toFloat()
        val centroidZ = points2D.map { it.second }.average().toFloat()
        
        val minX = points2D.minOf { it.first }
        val maxX = points2D.maxOf { it.first }
        val minZ = points2D.minOf { it.second }
        val maxZ = points2D.maxOf { it.second }
        
        val scaleU = maxX - minX
        val scaleV = maxZ - minZ
        
        // Create fill node
        polygonFillNode = Node().apply {
            renderable = polygonFillRenderable
            setParent(arSceneView?.scene)
            
            // Position at centroid, slightly above the plane to avoid z-fighting
            val fillPosition = planePose.compose(Pose.makeTranslation(centroidX, 0.001f, centroidZ))
            worldPosition = Vector3(fillPosition.tx(), fillPosition.ty(), fillPosition.tz())
            localScale = Vector3(scaleU, 1f, scaleV)
        }
    }
    
    private fun drawEdge(pointA: Vector3, pointB: Vector3): Node {
        // Calculate midpoint and direction - lines sit at plane height (y coordinate)
        val midpoint = Vector3.add(pointA, pointB).scaled(0.5f)
        val direction = Vector3.subtract(pointB, pointA)
        val distance = direction.length()
        
        // Normalize direction
        val normalizedDirection = direction.normalized()
        
        // Create rotation quaternion to align the edge with the direction
        // The edge should lie flat on the plane (horizontal)
        val up = Vector3(0f, 1f, 0f)
        val right = Vector3.cross(normalizedDirection, up)
        val forward = Vector3.cross(right, normalizedDirection)
        
        val rotation = Quaternion.lookRotation(normalizedDirection, forward)
        
        return Node().apply {
            renderable = polygonLineRenderable
            setParent(arSceneView?.scene)
            worldPosition = midpoint
            worldRotation = rotation
            // Create a thin rectangular prism that sits on the floor like tape
            localScale = Vector3(1f, 1f, distance)
        }
    }

    private fun calculateAndDisplayArea() {
        if (polygonPoints.size < 3) return
        
        // Project polygonPoints into 2D plane coordinates (drop Y, use X and Z)
        val points2D = polygonPoints.map { point ->
            Pair(point.x, point.z)
        }
        
        // Compute area in square meters using Shoelace formula
        var sum = 0f
        for (i in points2D.indices) {
            val (x1, z1) = points2D[i]
            val (x2, z2) = points2D[(i + 1) % points2D.size]
            sum += (x1 * z2 - x2 * z1)
        }
        val areaM2 = kotlin.math.abs(sum) * 0.5f
        
        // Convert to square feet
        val areaSqFt = areaM2 * 10.7639f
        
        // Update area TextView using AppPrefs for formatting
        val units = AppPrefs.getUnits()
        val precision = AppPrefs.getPrecision()
        val unitSymbol = if (units == "imperial") "ft²" else "m²"
        val displayArea = if (units == "imperial") areaSqFt else areaM2
        binding.areaTextView.text = "${String.format("%.${precision}f", displayArea)} $unitSymbol"
        binding.areaBubbleContainer.visibility = android.view.View.VISIBLE
    }
    
    private fun updateAreaBubblePosition() {
        if (polygonPoints.size < 3) {
            binding.areaBubbleContainer.visibility = android.view.View.GONE
            return
        }
        
        // For now, position the bubble at the center of the screen
        // TODO: Implement proper 3D to screen coordinate projection
        binding.areaBubbleContainer.x = (binding.arSceneViewContainer.width - binding.areaBubbleContainer.width) / 2f
        binding.areaBubbleContainer.y = (binding.arSceneViewContainer.height - binding.areaBubbleContainer.height) / 2f
    }

    private fun updatePolygonDisplay() {
        when (polygonState.anchors.size) {
            0 -> {
                binding.instructionTextView.text = "Tap each corner to outline the area"
            }
            1 -> {
                binding.instructionTextView.text = "Tap the next corner"
            }
            2 -> {
                binding.instructionTextView.text = "Tap one more corner to complete the area"
            }
            else -> {
                binding.instructionTextView.text = "Tap Accept to save this measurement"
            }
        }
        
        // Render polygon points
        renderPolygonPoints()
    }
    
    private fun renderPolygonPoints() {
        // Clear existing polygon nodes
        clearPolygonNodes()
        
        val anchors = polygonState.anchors
        if (anchors.isEmpty()) return
        
        // 1. Create marker nodes for each vertex
        anchors.forEach { anchor ->
            val node = AnchorNode().apply {
                renderable = pointRenderable
                setParent(arSceneView?.scene)
                setAnchor(anchor)
            }
            polygonNodes.add(node)
        }
        
        // 2. Create line segments between consecutive vertices
        // IMPORTANT: Lines must connect anchor poses directly, not straight up in Y.
        // The old behavior that drew "vertical green lines" between taps is a bug and must not return.
        if (anchors.size >= 2) {
            for (i in 0 until anchors.size) {
                val currentAnchor = anchors[i]
                val nextAnchor = anchors[(i + 1) % anchors.size]
                
                // Only draw closing line if we have 3+ points
                if (i == anchors.size - 1 && anchors.size < 3) continue
                
                val lineNode = createLineBetweenAnchors(currentAnchor, nextAnchor)
                polygonLineNodes.add(lineNode)
            }
        }
        
        // 3. Create polygon fill if we have 3+ points
        if (anchors.size >= 3) {
            createPolygonFill()
        }
    }
    
    private fun createLineBetweenAnchors(anchorA: com.google.ar.core.Anchor, anchorB: com.google.ar.core.Anchor): Node {
        val poseA = anchorA.pose
        val poseB = anchorB.pose
        
        val posA = poseA.translation
        val posB = poseB.translation
        
        // Calculate midpoint and direction
        val midpoint = floatArrayOf(
            (posA[0] + posB[0]) / 2f,
            (posA[1] + posB[1]) / 2f,
            (posA[2] + posB[2]) / 2f
        )
        
        val direction = floatArrayOf(
            posB[0] - posA[0],
            posB[1] - posA[1],
            posB[2] - posA[2]
        )
        
        val distance = kotlin.math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2])
        
        // Normalize direction
        direction[0] /= distance
        direction[1] /= distance
        direction[2] /= distance
        
        // Create rotation quaternion to align cylinder with direction
        val up = floatArrayOf(0f, 1f, 0f)
        val right = floatArrayOf(
            direction[1] * up[2] - direction[2] * up[1],
            direction[2] * up[0] - direction[0] * up[2],
            direction[0] * up[1] - direction[1] * up[0]
        )
        
        // Normalize right vector
        val rightLength = kotlin.math.sqrt(right[0] * right[0] + right[1] * right[1] + right[2] * right[2])
        if (rightLength > 0.001f) {
            right[0] /= rightLength
            right[1] /= rightLength
            right[2] /= rightLength
        }
        
        val forward = floatArrayOf(
            right[1] * direction[2] - right[2] * direction[1],
            right[2] * direction[0] - right[0] * direction[2],
            right[0] * direction[1] - right[1] * direction[0]
        )
        
        // Create rotation matrix and convert to quaternion
        val rotation = Quaternion.lookRotation(
            Vector3(direction[0], direction[1], direction[2]),
            Vector3(forward[0], forward[1], forward[2])
        )
        
        return Node().apply {
            renderable = polygonLineRenderable
            setParent(arSceneView?.scene)
            worldPosition = Vector3(midpoint[0], midpoint[1], midpoint[2])
            worldRotation = rotation
            localScale = Vector3(1f, 1f, distance)
        }
    }
    
    private fun createPolygonFill() {
        val anchors = polygonState.anchors
        if (anchors.size < 3) return
        
        // Get the plane for projection
        val plane = polygonState.plane ?: return
        val planePose = plane.centerPose
        
        // Project all points to 2D plane coordinates
        val xAxis = planePose.getXAxis()
        val zAxis = planePose.getZAxis()
        val normal = planePose.getYAxis() // Plane normal
        
        val originX = planePose.tx()
        val originY = planePose.ty()
        val originZ = planePose.tz()
        
        val projectedPoints = anchors.map { anchor ->
            val wp = anchor.pose.translation
            val vx = wp[0] - originX
            val vy = wp[1] - originY
            val vz = wp[2] - originZ
            
            val u = vx * xAxis[0] + vy * xAxis[1] + vz * xAxis[2]
            val v = vx * zAxis[0] + vy * zAxis[1] + vz * zAxis[2]
            
            Pair(u, v)
        }
        
        // Calculate centroid
        val centroidU = projectedPoints.map { it.first }.average().toFloat()
        val centroidV = projectedPoints.map { it.second }.average().toFloat()
        
        // Convert back to 3D world position
        val centroidWorldPos = floatArrayOf(
            originX + centroidU * xAxis[0] + centroidV * zAxis[0],
            originY + centroidU * xAxis[1] + centroidV * zAxis[1],
            originZ + centroidU * xAxis[2] + centroidV * zAxis[2]
        )
        
        // Offset slightly above the plane to avoid z-fighting
        val offset = 0.002f // 2mm
        val fillPosition = floatArrayOf(
            centroidWorldPos[0] + normal[0] * offset,
            centroidWorldPos[1] + normal[1] * offset,
            centroidWorldPos[2] + normal[2] * offset
        )
        
        // Calculate bounding box for scaling
        val minU = projectedPoints.map { it.first }.minOrNull() ?: 0f
        val maxU = projectedPoints.map { it.first }.maxOrNull() ?: 0f
        val minV = projectedPoints.map { it.second }.minOrNull() ?: 0f
        val maxV = projectedPoints.map { it.second }.maxOrNull() ?: 0f
        
        val scaleU = maxU - minU
        val scaleV = maxV - minV
        
        polygonFillNode = Node().apply {
            renderable = polygonFillRenderable
            setParent(arSceneView?.scene)
            worldPosition = Vector3(fillPosition[0], fillPosition[1], fillPosition[2])
            localScale = Vector3(scaleU, 1f, scaleV)
        }
    }
    
    private fun clearPolygonLineNodes() {
        polygonLineNodes.forEach { node ->
            node.setParent(null)
        }
        polygonLineNodes.clear()
    }
    
    private fun clearPolygonNodes() {
        clearPolygonPointMarkers()
        clearPolygonLineNodes()
        
        polygonFillNode?.setParent(null)
        polygonFillNode = null
    }

    private fun undoLastPoint() {
        if (polygonPoints.isEmpty()) return
        
        // Remove last point
        polygonPoints.removeAt(polygonPoints.size - 1)
        
        // Remove last anchor from polygon state
        if (polygonState.anchors.isNotEmpty()) {
            val lastAnchor = polygonState.anchors.removeAt(polygonState.anchors.size - 1)
            lastAnchor.detach()
        }
        
        // Re-render everything
        renderPolygonOutline()
        
        if (polygonPoints.size >= 3) {
            calculateAndDisplayArea()
            renderPolygonFill()
        } else {
            binding.areaBubbleContainer.visibility = android.view.View.GONE
            polygonFillNode?.setParent(null)
            polygonFillNode = null
        }
        
        updatePolygonDisplay()
        updateButtonStates()
        updateAreaBubblePosition()
    }
    
    private fun confirmMeasurement() {
        if (polygonState.anchors.size < 3) return
        
        val areaFt2 = polygonState.areaSqFeet()
        val intent = saveMeasurement(areaFt2)
        startActivity(intent)
        finish()
    }
    
    private fun updateButtonStates() {
        val hasPoints = polygonState.anchors.size > 0
        val canUndo = polygonState.anchors.size > 0
        val canConfirm = polygonState.anchors.size >= 3
        
        binding.bottomControlsContainer.visibility = if (hasPoints) android.view.View.VISIBLE else android.view.View.GONE
        binding.backButton.visibility = if (hasPoints) android.view.View.VISIBLE else android.view.View.GONE
        binding.undoButton.visibility = if (canUndo) android.view.View.VISIBLE else android.view.View.GONE
        binding.confirmButton.visibility = if (canConfirm) android.view.View.VISIBLE else android.view.View.GONE
    }

    private suspend fun initRenderables() {
        // takes about half a second on a high-end device(!)
        val materialBlue = MaterialFactory.makeOpaqueWithColor(this, Color(measuringTapeColor)).await()
        val materialTeal = MaterialFactory.makeTransparentWithColor(this, Color(0.3f, 0.7f, 0.6f, 0.3f)).await() // Semi-transparent teal
        
        cursorRenderable = ViewRenderable.builder().setView(this, R.layout.view_ar_cursor).build().await()
        pointRenderable = ShapeFactory.makeCylinder(0.03f, 0.005f, Vector3.zero(), materialBlue)
        
        // Polygon-specific renderables - thin rectangular prism for edges that sit on the floor like tape
        polygonLineRenderable = ShapeFactory.makeCube(Vector3(0.02f, 0.005f, 1f), Vector3.zero(), materialBlue) // Thin rectangular prism for edges
        polygonFillRenderable = ShapeFactory.makeCube(Vector3(1f, 0.001f, 1f), Vector3.zero(), materialTeal) // Thin fill
        
        listOfNotNull(cursorRenderable, pointRenderable, polygonLineRenderable, polygonFillRenderable).forEach {
            it.isShadowCaster = false
            it.isShadowReceiver = false
        }
        // in case they have been initialized already, (re)set renderables...
        cursorNode?.renderable = cursorRenderable
    }

    // Old measurement methods removed for surface area mode

    private fun clearMeasuring() {
        binding.arSceneViewContainer.performHapticFeedback(VIRTUAL_KEY)
        
        // Clear polygon state
        polygonState.clear()
        polygonPoints.clear()
        clearPolygonNodes()
        
        // Clear old measurement nodes (legacy cleanup)
        cursorNode?.isEnabled = true
        
        // Reset UI
        binding.areaBubbleContainer.visibility = android.view.View.GONE
        updatePolygonDisplay()
        updateButtonStates()
    }

    private fun saveMeasurement(areaSqFt: Float): Intent {
        // Save measurement to local storage
        val measurementRecord = MeasurementRecord(
            areaSqFt = areaSqFt,
            timestampMillis = System.currentTimeMillis()
        )
        MeasurementStore.add(measurementRecord)
        
        // Create intent for Tile Calculator with pre-filled area
        val intent = Intent(this, TileCalculatorActivity::class.java).apply {
            putExtra("areaSqFeet", areaSqFt)
        }
        
        return intent
    }

    private fun returnMeasuringResult() {
        if (polygonState.anchors.size < 3) return
        
        val areaFt2 = polygonState.areaSqFeet()
        val areaM2 = polygonState.areaSqMeters()
        
        // Save measurement to store
        val measurementRecord = MeasurementRecord(
            areaSqFt = areaFt2,
            timestampMillis = System.currentTimeMillis()
        )
        MeasurementStore.add(measurementRecord)
        
        // Launch ResultSummaryActivity
        val intent = Intent(this, ResultSummaryActivity::class.java).apply {
            putExtra(ResultSummaryActivity.EXTRA_AREA_SQ_FEET, areaFt2)
            putExtra(ResultSummaryActivity.EXTRA_AREA_SQ_METERS, areaM2)
        }
        startActivity(intent)
        finish()
    }

    private fun updateCursor(hitResult: HitResult) {
        // release previous anchor only if it is not used by any other node
        val anchor = cursorNode?.anchor
        if (anchor != null) {
            anchor.detach()
        }

        try {
            val newAnchor = hitResult.createAnchor()
            val cursorNode = getCursorNode()
            cursorNode.anchor = newAnchor

            // Old measurement logic removed for surface area mode
        } catch (e: Exception) {
            Log.e("MeasureActivity", "Error", e)
        }
    }

    // Old measurement methods removed for surface area mode

    private fun getCursorNode(): AnchorNode {
        var node = cursorNode
        if (node == null) {
            node = AnchorNode().apply {
                renderable = cursorRenderable
                setParent(arSceneView!!.scene)
            }
            cursorNode = node
        }
        return node
    }

    /* ----------------------------------------- Intent ----------------------------------------- */

    companion object {
        private const val HAS_REQUESTED_AR_CORE_INSTALL = "has_requested_ar_core_install"

        /** Boolean. Whether this activity should return a result. If yes, the activity will return
         *  the measure result in RESULT_MEASURE_METERS or RESULT_MEASURE_FEET + RESULT_MEASURE_INCHES
         */
        const val PARAM_REQUEST_RESULT = "request_result"

        /** Int. Color value of the measuring tape. Default is orange.
         */
        const val PARAM_MEASURING_TAPE_COLOR = "measuring_tape_color"

        /* ----------------------------------- Intent Result ------------------------------------ */

        /** The action to identify a result */
        const val RESULT_ACTION = "de.westnordost.streetmeasure.RESULT_ACTION"

        /** The result as displayed to the user, set if display unit was meters. Double. */
        const val RESULT_METERS = "meters"

        /** The result as displayed to the user, set if display unit was feet+inches. Int. */
        const val RESULT_FEET = "feet"

        /** The result as displayed to the user, set if display unit was feet+inches. Int. */
        const val RESULT_INCHES = "inches"
    }
}
