package com.sinavkoc.app.pose

import com.sinavkoc.app.model.*
import kotlin.math.*

data class AnalysisOutput(
    val state: LivePoseState,
    val repCompleted: RepRecord? = null,
    val voiceCue: String? = null
)

class PushupAnalyzer(private var variation: PushupVariation) {
    private var phase = "IDLE"
    private var reps = 0
    private val repRecords = mutableListOf<RepRecord>()
    private val issueCounts = mutableMapOf<FormIssue, Int>()

    private var smoothedElbow: Float? = null
    private var smoothedBody: Float? = null
    private var smoothedLandmarks: List<PosePoint> = emptyList()
    private var lastUseRight = false

    // Stability & persistence trackers
    private var unstableUntilMs = 0L
    private var downHoldFrames = 0
    private var upHoldFrames = 0
    private var idleResetFrames = 0
    private var lowVisFrames = 0
    private var headDropFrames = 0
    private var hipSagFrames = 0
    private var hipPikeFrames = 0

    // Per-rep trackers
    private var repStartMs = 0L
    private var repMinElbow = 180f
    private var topShoulderY = 0f
    private var maxShoulderY = 0f
    private val repIssues = mutableSetOf<FormIssue>()
    private var repBrokenFormFrames = 0
    private var attemptedDescent = false
    private var lastShallowAlertMs = 0L
    private var rejectedUntilMs = 0L
    private var rejectedFeedback = ""

    @Synchronized
    fun reset(newVariation: PushupVariation = variation) {
        variation = newVariation
        phase = "IDLE"
        reps = 0
        repRecords.clear()
        issueCounts.clear()
        smoothedElbow = null
        smoothedBody = null
        smoothedLandmarks = emptyList()
        lastUseRight = false
        unstableUntilMs = 0L
        downHoldFrames = 0
        upHoldFrames = 0
        idleResetFrames = 0
        lowVisFrames = 0
        headDropFrames = 0
        hipSagFrames = 0
        hipPikeFrames = 0
        repStartMs = 0L
        repMinElbow = 180f
        topShoulderY = 0f
        maxShoulderY = 0f
        repIssues.clear()
        repBrokenFormFrames = 0
        attemptedDescent = false
        lastShallowAlertMs = 0L
        rejectedUntilMs = 0L
        rejectedFeedback = ""
    }

    @Synchronized
    fun onCameraFlipped() {
        smoothedLandmarks = emptyList()
        downHoldFrames = 0
        upHoldFrames = 0
    }

    @Synchronized
    fun getReps(): Int = reps

    @Synchronized
    fun getIssueCounts(): Map<FormIssue, Int> = issueCounts.toMap()

    @Synchronized
    fun getAverageScore(): Int =
        if (repRecords.isEmpty()) 100 else (repRecords.sumOf { it.score } / repRecords.size)

    @Synchronized
    fun analyze(
        landmarks: List<PosePoint>,
        imageWidth: Int = 480,
        imageHeight: Int = 640,
        isPhoneShaking: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        worldLandmarks: List<PosePoint> = emptyList()
    ): AnalysisOutput {
        val imgW = imageWidth.coerceAtLeast(1)
        val imgH = imageHeight.coerceAtLeast(1)

        // Adaptive velocity-aware smoothing on 2D screen landmarks so skeleton is silky smooth
        val displayLandmarks = smoothScreenLandmarks(landmarks)

        // 1. Hardware IMU shake detection
        if (isPhoneShaking) {
            unstableUntilMs = nowMs + 850L
            downHoldFrames = 0
            upHoldFrames = 0
        }

        if (nowMs < unstableUntilMs) {
            return AnalysisOutput(
                LivePoseState(
                    phase = phase,
                    reps = reps,
                    elbowAngle = smoothedElbow?.roundToInt(),
                    bodyAngle = smoothedBody?.roundToInt(),
                    depthPercent = 0f,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Telefon hareket ediyor! Sabit bir yere yasla",
                    currentScore = getAverageScore(),
                    landmarks = displayLandmarks,
                    activeSideRight = lastUseRight,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
            )
        }

        if (displayLandmarks.size < 29) {
            downHoldFrames = 0
            upHoldFrames = 0
            return AnalysisOutput(
                LivePoseState(
                    phase = phase,
                    reps = reps,
                    elbowAngle = smoothedElbow?.roundToInt(),
                    bodyAngle = smoothedBody?.roundToInt(),
                    depthPercent = 0f,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Yan profilden tüm vücudun görünecek şekilde kadraja gir",
                    currentScore = getAverageScore(),
                    landmarks = displayLandmarks,
                    activeSideRight = lastUseRight,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
            )
        }

        val leftIdx = listOf(11, 13, 15, 23)
        val rightIdx = listOf(12, 14, 16, 24)
        val leftVis = leftIdx.map { displayLandmarks[it].visibility }.average().toFloat()
        val rightVis = rightIdx.map { displayLandmarks[it].visibility }.average().toFloat()

        val useRight = if (lastUseRight) {
            rightVis + 0.08f >= leftVis
        } else {
            rightVis > leftVis + 0.08f
        }
        lastUseRight = useRight

        // Auto-detect FRONT/DIAGONAL view vs SIDE profile view
        val lShoulder = displayLandmarks[11]
        val rShoulder = displayLandmarks[12]
        val lElbow = displayLandmarks[13]
        val rElbow = displayLandmarks[14]
        val lWrist = displayLandmarks[15]
        val rWrist = displayLandmarks[16]
        val lHip = displayLandmarks[23]
        val rHip = displayLandmarks[24]

        val bothArmsVisible = lShoulder.visibility > 0.35f && rShoulder.visibility > 0.35f &&
            lElbow.visibility > 0.30f && rElbow.visibility > 0.30f &&
            lWrist.visibility > 0.25f && rWrist.visibility > 0.25f

        val shoulderSpanPx = abs(lShoulder.x - rShoulder.x) * imgW
        val shoulderMidX = (lShoulder.x + rShoulder.x) * 0.5f
        val shoulderMidY = (lShoulder.y + rShoulder.y) * 0.5f
        val hipMidX = (lHip.x + rHip.x) * 0.5f
        val hipMidY = (lHip.y + rHip.y) * 0.5f
        val torsoSpanPx = abs(shoulderMidX - hipMidX) * imgW

        val isFrontView = bothArmsVisible && shoulderSpanPx > imgW * 0.14f && shoulderSpanPx > torsoSpanPx * 0.85f

        val chosenIdx = if (useRight) rightIdx else leftIdx
        val avgKeyVis = chosenIdx.map { displayLandmarks[it].visibility }.average().toFloat()
        val minKeyVis = chosenIdx.minOf { displayLandmarks[it].visibility }

        if (!isFrontView && (avgKeyVis < 0.32f || minKeyVis < 0.15f)) {
            lowVisFrames += 1
            if (lowVisFrames >= 5) {
                downHoldFrames = 0
                upHoldFrames = 0
                return AnalysisOutput(
                    LivePoseState(
                        phase = phase,
                        reps = reps,
                        severity = FormSeverity.WARNING,
                        feedbackText = "Tüm vücudun net görünmüyor, kameradan biraz uzaklaş",
                        currentScore = getAverageScore(),
                        landmarks = displayLandmarks,
                        activeSideRight = useRight,
                        isFrontView = false,
                        imageWidth = imgW,
                        imageHeight = imgH
                    )
                )
            }
        } else {
            lowVisFrames = 0
        }

        val sIdx = if (useRight) 12 else 11
        val eIdx = if (useRight) 14 else 13
        val wIdx = if (useRight) 16 else 15
        val hIdx = if (useRight) 24 else 23
        val kIdx = if (useRight) 26 else 25
        val aIdx = if (useRight) 28 else 27
        val earIdx = if (useRight) 8 else 7

        val shoulder = displayLandmarks[sIdx]
        val elbow = displayLandmarks[eIdx]
        val wrist = displayLandmarks[wIdx]
        val hip = displayLandmarks[hIdx]
        val knee = displayLandmarks[kIdx]
        val ankle = displayLandmarks[aIdx]
        val ear = displayLandmarks[earIdx]

        val useKnee = variation.thresholds.useKneeLine || ankle.visibility < 0.35f
        val lowerIdx = if (useKnee) kIdx else aIdx
        val lower = displayLandmarks[lowerIdx]

        // Prefer true 3D World Landmarks; in Front View average BOTH arms for rock-solid accuracy!
        val hasWorld = worldLandmarks.size >= 29
        val rawElbow = if (isFrontView) {
            val leftAng = if (hasWorld) {
                calcAngle3D(worldLandmarks[11], worldLandmarks[13], worldLandmarks[15])
            } else {
                calcAnglePx(lShoulder, lElbow, lWrist, imgW, imgH)
            }
            val rightAng = if (hasWorld) {
                calcAngle3D(worldLandmarks[12], worldLandmarks[14], worldLandmarks[16])
            } else {
                calcAnglePx(rShoulder, rElbow, rWrist, imgW, imgH)
            }
            (leftAng + rightAng) * 0.5f
        } else if (hasWorld) {
            calcAngle3D(worldLandmarks[sIdx], worldLandmarks[eIdx], worldLandmarks[wIdx])
        } else {
            calcAnglePx(shoulder, elbow, wrist, imgW, imgH)
        }

        val rawBody = if (hasWorld) {
            calcAngle3D(worldLandmarks[sIdx], worldLandmarks[hIdx], worldLandmarks[lowerIdx])
        } else {
            calcAnglePx(shoulder, hip, lower, imgW, imgH)
        }

        val alpha = 0.38f
        val elbowAngle = smoothedElbow?.let { it * (1f - alpha) + rawElbow * alpha } ?: rawElbow
        val bodyAngle = smoothedBody?.let { it * (1f - alpha) + rawBody * alpha } ?: rawBody
        smoothedElbow = elbowAngle
        smoothedBody = bodyAngle

        // 2. Pushup posture verification (supports BOTH Front/Diagonal View AND Side View!)
        val isPushupPosture = if (isFrontView) {
            val wristMidY = (lWrist.y + rWrist.y) * 0.5f
            val torsoVertPx = abs(hipMidY - shoulderMidY) * imgH
            val handsNotAboveHead = (wristMidY - shoulderMidY) > -0.10f
            // Standing upright has tall vertical torso (> 1.45x shoulder width); front pushup is foreshortened
            val notStandingTall = torsoVertPx < shoulderSpanPx * 1.45f || (lHip.visibility < 0.35f && rHip.visibility < 0.35f)
            handsNotAboveHead && notStandingTall
        } else {
            val dxPx = abs(shoulder.x - hip.x) * imgW
            val dyPx = abs(shoulder.y - hip.y) * imgH
            val maxVertRatio = when (variation.id) {
                PushupVariationId.KNEE, PushupVariationId.INCLINE -> 2.6f
                else -> 1.85f
            }
            val handsNotAboveHead = (wrist.y - shoulder.y) > -0.08f
            (dxPx > imgW * 0.045f) &&
                (dyPx / (dxPx + 0.001f) < maxVertRatio) &&
                handsNotAboveHead
        }

        val activeShoulderY = if (isFrontView) shoulderMidY else shoulder.y

        if (!isPushupPosture) {
            idleResetFrames += 1
            downHoldFrames = 0
            upHoldFrames = 0
            if (idleResetFrames >= 12) {
                phase = "IDLE"
                attemptedDescent = false
                repMinElbow = 180f
                repIssues.clear()
                repBrokenFormFrames = 0
            }
            return AnalysisOutput(
                LivePoseState(
                    phase = phase,
                    reps = reps,
                    elbowAngle = elbowAngle.roundToInt(),
                    bodyAngle = bodyAngle.roundToInt(),
                    depthPercent = 0f,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Şınav pozisyonuna geç (ön veya yan açıdan çalışır)",
                    currentScore = getAverageScore(),
                    landmarks = displayLandmarks,
                    activeSideRight = useRight,
                    isFrontView = isFrontView,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
            )
        } else {
            idleResetFrames = 0
        }

        val t = variation.thresholds
        val activeIssues = mutableListOf<FormIssue>()

        // 3. Sustained hip sag / hip pike check (only in side view or when hips & legs are clearly visible in 3D)
        val canCheckBodyLine = !isFrontView || (hasWorld && hip.visibility > 0.55f && lower.visibility > 0.55f)
        if (canCheckBodyLine && bodyAngle < t.minBodyAngle) {
            val isSag = if (hasWorld) {
                // In MediaPipe 3D world coords, +y points down toward gravity
                val wS = worldLandmarks[sIdx]
                val wH = worldLandmarks[hIdx]
                val wL = worldLandmarks[lowerIdx]
                val expectedY = (wS.y + wL.y) * 0.5f
                wH.y > expectedY + 0.03f
            } else {
                val spanX = lower.x - shoulder.x
                if (abs(spanX) > 0.05f) {
                    val ratio = ((hip.x - shoulder.x) / spanX).coerceIn(0f, 1f)
                    val expectedY = shoulder.y + ratio * (lower.y - shoulder.y)
                    hip.y > expectedY + 0.02f
                } else false
            }

            val isPike = if (hasWorld) {
                val wS = worldLandmarks[sIdx]
                val wH = worldLandmarks[hIdx]
                val wL = worldLandmarks[lowerIdx]
                val expectedY = (wS.y + wL.y) * 0.5f
                wH.y < expectedY - 0.03f
            } else {
                val spanX = lower.x - shoulder.x
                if (abs(spanX) > 0.05f) {
                    val ratio = ((hip.x - shoulder.x) / spanX).coerceIn(0f, 1f)
                    val expectedY = shoulder.y + ratio * (lower.y - shoulder.y)
                    hip.y < expectedY - 0.02f
                } else false
            }

            if (isSag) {
                hipSagFrames += 1
                hipPikeFrames = 0
                if (hipSagFrames >= 5) activeIssues.add(FormIssue.HIP_SAG)
            } else if (isPike) {
                hipPikeFrames += 1
                hipSagFrames = 0
                if (hipPikeFrames >= 5) activeIssues.add(FormIssue.HIP_PIKE)
            } else {
                hipSagFrames = 0
                hipPikeFrames = 0
            }
        } else {
            hipSagFrames = 0
            hipPikeFrames = 0
        }

        // Head drop (6 consecutive frames)
        if (ear.visibility > 0.55f) {
            val torsoLenPx = hypot(((shoulder.x - hip.x) * imgW).toDouble(), ((shoulder.y - hip.y) * imgH).toDouble()).toFloat()
            val earDropPx = (ear.y - shoulder.y) * imgH
            if (torsoLenPx > imgW * 0.10f && earDropPx > torsoLenPx * 0.44f) {
                headDropFrames += 1
                if (headDropFrames >= 6) {
                    activeIssues.add(FormIssue.HEAD_DROP)
                }
            } else {
                headDropFrames = 0
            }
        } else {
            headDropFrames = 0
        }

        val depthPercent = (((t.upAngle - elbowAngle) / (t.upAngle - t.downAngle)) * 100f).coerceIn(0f, 100f)

        if (phase == "UP" || phase == "DOWN") {
            if (elbowAngle < repMinElbow) repMinElbow = elbowAngle
            if (activeShoulderY > maxShoulderY) maxShoulderY = activeShoulderY
            repIssues.addAll(activeIssues)
            if (FormIssue.HIP_SAG in activeIssues || FormIssue.HIP_PIKE in activeIssues) {
                repBrokenFormFrames += 1
            }
        }

        var completedRep: RepRecord? = null
        var voiceCue: String? = null

        when (phase) {
            "IDLE" -> {
                if (elbowAngle >= t.upAngle - 16f) {
                    upHoldFrames += 1
                    if (upHoldFrames >= 3) {
                        phase = "UP"
                        upHoldFrames = 0
                        downHoldFrames = 0
                        repStartMs = nowMs
                        repMinElbow = elbowAngle
                        topShoulderY = activeShoulderY
                        maxShoulderY = activeShoulderY
                        repIssues.clear()
                        repBrokenFormFrames = 0
                    }
                } else {
                    upHoldFrames = 0
                }
            }
            "UP" -> {
                if (elbowAngle >= t.upAngle - 8f && !attemptedDescent) {
                    topShoulderY = topShoulderY * 0.8f + activeShoulderY * 0.2f
                    maxShoulderY = topShoulderY
                }
                if (elbowAngle < t.upAngle - 22f && !attemptedDescent) {
                    attemptedDescent = true
                    repStartMs = nowMs
                }
                if (elbowAngle <= t.downAngle) {
                    downHoldFrames += 1
                    if (downHoldFrames >= 2) {
                        phase = "DOWN"
                        downHoldFrames = 0
                        upHoldFrames = 0
                    }
                } else {
                    downHoldFrames = 0
                    if (attemptedDescent &&
                        repMinElbow < t.upAngle - 25f &&
                        repMinElbow > t.downAngle + 8f &&
                        elbowAngle >= t.upAngle - 4f
                    ) {
                        attemptedDescent = false
                        repMinElbow = 180f
                        if (nowMs - lastShallowAlertMs > 3000L) {
                            lastShallowAlertMs = nowMs
                            issueCounts[FormIssue.SHALLOW_DEPTH] = (issueCounts[FormIssue.SHALLOW_DEPTH] ?: 0) + 1
                            activeIssues.add(FormIssue.SHALLOW_DEPTH)
                            voiceCue = FormIssue.SHALLOW_DEPTH.voiceCue
                        }
                    }
                }
            }
            "DOWN" -> {
                if (elbowAngle >= t.upAngle) {
                    upHoldFrames += 1
                    if (upHoldFrames >= 2) {
                        val durationMs = if (repStartMs > 0L) nowMs - repStartMs else 1500L
                        val verticalTravel = maxShoulderY - topShoulderY

                        val formBroken = repBrokenFormFrames >= 6 ||
                            FormIssue.HIP_SAG in activeIssues ||
                            FormIssue.HIP_PIKE in activeIssues

                        if (durationMs >= 500L && verticalTravel >= 0.025f) {
                            if (formBroken) {
                                val brokenIssue = when {
                                    FormIssue.HIP_SAG in repIssues -> FormIssue.HIP_SAG
                                    FormIssue.HIP_PIKE in repIssues -> FormIssue.HIP_PIKE
                                    else -> FormIssue.HIP_SAG
                                }
                                issueCounts[brokenIssue] = (issueCounts[brokenIssue] ?: 0) + 1
                                rejectedUntilMs = nowMs + 2200L
                                rejectedFeedback = "Tekrar sayılmadı! ${brokenIssue.liveCue}"
                                voiceCue = "Sayılmadı! ${brokenIssue.voiceCue}"
                            } else {
                                reps += 1
                                if (durationMs < 800L) {
                                    repIssues.add(FormIssue.TOO_FAST)
                                }

                                val issues = repIssues.toList()
                                issues.forEach { issue ->
                                    issueCounts[issue] = (issueCounts[issue] ?: 0) + 1
                                }

                                var score = 100
                                if (FormIssue.HEAD_DROP in repIssues) score -= 10
                                if (FormIssue.TOO_FAST in repIssues) score -= 12
                                score = score.coerceIn(50, 100)

                                completedRep = RepRecord(
                                    repNumber = reps,
                                    minElbowAngle = repMinElbow.roundToInt(),
                                    durationMs = durationMs,
                                    score = score,
                                    issues = issues
                                )
                                repRecords.add(completedRep)
                            }
                        }

                        phase = "UP"
                        upHoldFrames = 0
                        downHoldFrames = 0
                        repStartMs = nowMs
                        repMinElbow = 180f
                        topShoulderY = activeShoulderY
                        maxShoulderY = activeShoulderY
                        repIssues.clear()
                        repBrokenFormFrames = 0
                        attemptedDescent = false
                    }
                } else {
                    upHoldFrames = 0
                }
            }
        }

        val (severity, feedback) = if (nowMs < rejectedUntilMs) {
            FormSeverity.ERROR to rejectedFeedback
        } else if (activeIssues.isNotEmpty()) {
            val primary = activeIssues.first()
            if (voiceCue == null) voiceCue = primary.voiceCue
            val sev = if (primary == FormIssue.HIP_SAG || primary == FormIssue.HIP_PIKE) {
                FormSeverity.ERROR
            } else {
                FormSeverity.WARNING
            }
            sev to primary.liveCue
        } else if (phase == "DOWN") {
            FormSeverity.GOOD to "Süper derinlik! Şimdi güçlü it"
        } else if (phase == "UP" && reps > 0) {
            FormSeverity.GOOD to "Harika form! Tempoyu koru"
        } else {
            FormSeverity.GOOD to "Hazır! Kontrollü şekilde aşağı in"
        }

        return AnalysisOutput(
            state = LivePoseState(
                phase = phase,
                reps = reps,
                elbowAngle = elbowAngle.roundToInt(),
                bodyAngle = bodyAngle.roundToInt(),
                depthPercent = depthPercent,
                severity = severity,
                feedbackText = feedback,
                currentScore = getAverageScore(),
                landmarks = displayLandmarks,
                activeSideRight = useRight,
                isFrontView = isFrontView,
                imageWidth = imgW,
                imageHeight = imgH
            ),
            repCompleted = completedRep,
            voiceCue = voiceCue
        )
    }

    private fun smoothScreenLandmarks(raw: List<PosePoint>): List<PosePoint> {
        if (raw.size < 29) {
            smoothedLandmarks = emptyList()
            return raw
        }
        if (smoothedLandmarks.size != raw.size) {
            smoothedLandmarks = raw
            return raw
        }
        val next = raw.indices.map { i ->
            val prev = smoothedLandmarks[i]
            val curr = raw[i]
            val dist = hypot((curr.x - prev.x).toDouble(), (curr.y - prev.y).toDouble()).toFloat()
            // Adaptive alpha: steady when still (0.25), responsive when moving fast (0.60)
            val a = (0.25f + dist * 6f).coerceIn(0.25f, 0.65f)
            PosePoint(
                x = prev.x + (curr.x - prev.x) * a,
                y = prev.y + (curr.y - prev.y) * a,
                visibility = prev.visibility * 0.7f + curr.visibility * 0.3f,
                z = prev.z + (curr.z - prev.z) * a
            )
        }
        smoothedLandmarks = next
        return next
    }

    private fun calcAngle3D(a: PosePoint, b: PosePoint, c: PosePoint): Float {
        val bax = (a.x - b.x).toDouble()
        val bay = (a.y - b.y).toDouble()
        val baz = (a.z - b.z).toDouble()
        val bcx = (c.x - b.x).toDouble()
        val bcy = (c.y - b.y).toDouble()
        val bcz = (c.z - b.z).toDouble()

        val dot = bax * bcx + bay * bcy + baz * bcz
        val magBA = sqrt(bax * bax + bay * bay + baz * baz)
        val magBC = sqrt(bcx * bcx + bcy * bcy + bcz * bcz)
        if (magBA < 1e-6 || magBC < 1e-6) return 180f

        val cosTheta = (dot / (magBA * magBC)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosTheta)).toFloat()
    }

    private fun calcAnglePx(a: PosePoint, b: PosePoint, c: PosePoint, imgW: Int, imgH: Int): Float {
        val ax = (a.x * imgW).toDouble()
        val ay = (a.y * imgH).toDouble()
        val bx = (b.x * imgW).toDouble()
        val by = (b.y * imgH).toDouble()
        val cx = (c.x * imgW).toDouble()
        val cy = (c.y * imgH).toDouble()

        val rad = atan2(cy - by, cx - bx) - atan2(ay - by, ax - bx)
        var deg = abs(Math.toDegrees(rad)).toFloat()
        if (deg > 180f) deg = 360f - deg
        return deg
    }
}
