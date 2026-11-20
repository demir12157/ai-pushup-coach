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
    private var lastUseRight = false

    // Stability & debounce trackers
    private var unstableUntilMs = 0L
    private var downHoldFrames = 0
    private var upHoldFrames = 0
    private var idleResetFrames = 0
    private var lowVisFrames = 0
    private var headDropFrames = 0

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
        lastUseRight = false
        unstableUntilMs = 0L
        downHoldFrames = 0
        upHoldFrames = 0
        idleResetFrames = 0
        lowVisFrames = 0
        headDropFrames = 0
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
        nowMs: Long = System.currentTimeMillis()
    ): AnalysisOutput {
        val imgW = imageWidth.coerceAtLeast(1)
        val imgH = imageHeight.coerceAtLeast(1)

        // 1. Hardware IMU shake detection (real gyroscope/accelerometer)
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
                    landmarks = landmarks,
                    activeSideRight = lastUseRight,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
            )
        }

        if (landmarks.size < 29) {
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
                    landmarks = landmarks,
                    activeSideRight = lastUseRight,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
            )
        }

        val leftIdx = listOf(11, 13, 15, 23)
        val rightIdx = listOf(12, 14, 16, 24)
        val leftVis = leftIdx.map { landmarks[it].visibility }.average().toFloat()
        val rightVis = rightIdx.map { landmarks[it].visibility }.average().toFloat()

        // Hysteresis so left/right side doesn't flicker
        val useRight = if (lastUseRight) {
            rightVis + 0.08f >= leftVis
        } else {
            rightVis > leftVis + 0.08f
        }
        lastUseRight = useRight

        val chosenIdx = if (useRight) rightIdx else leftIdx
        val avgKeyVis = chosenIdx.map { landmarks[it].visibility }.average().toFloat()
        val minKeyVis = chosenIdx.minOf { landmarks[it].visibility }

        if (avgKeyVis < 0.32f || minKeyVis < 0.15f) {
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
                        landmarks = landmarks,
                        activeSideRight = useRight,
                        imageWidth = imgW,
                        imageHeight = imgH
                    )
                )
            }
        } else {
            lowVisFrames = 0
        }

        val shoulder = landmarks[if (useRight) 12 else 11]
        val elbow = landmarks[if (useRight) 14 else 13]
        val wrist = landmarks[if (useRight) 16 else 15]
        val hip = landmarks[if (useRight) 24 else 23]
        val knee = landmarks[if (useRight) 26 else 25]
        val ankle = landmarks[if (useRight) 28 else 27]
        val ear = landmarks[if (useRight) 8 else 7]

        val lower = if (variation.thresholds.useKneeLine || ankle.visibility < 0.35f) knee else ankle

        // Aspect-ratio corrected angle calculation (true pixel coordinates!)
        val rawElbow = calcAnglePx(shoulder, elbow, wrist, imgW, imgH)
        val rawBody = calcAnglePx(shoulder, hip, lower, imgW, imgH)

        val alpha = 0.32f
        val elbowAngle = smoothedElbow?.let { it * (1f - alpha) + rawElbow * alpha } ?: rawElbow
        val bodyAngle = smoothedBody?.let { it * (1f - alpha) + rawBody * alpha } ?: rawBody
        smoothedElbow = elbowAngle
        smoothedBody = bodyAngle

        // 2. Pushup posture verification in true pixel geometry
        // Allows steep torso angle for Knee/Incline & diagonal camera views,
        // and allows shoulders to drop all the way to wrist/floor level at bottom of a deep pushup!
        val dxPx = abs(shoulder.x - hip.x) * imgW
        val dyPx = abs(shoulder.y - hip.y) * imgH
        val maxVertRatio = when (variation.id) {
            PushupVariationId.KNEE, PushupVariationId.INCLINE -> 2.6f
            else -> 1.85f
        }
        val handsNotAboveHead = (wrist.y - shoulder.y) > -0.08f
        val isPushupPosture = (dxPx > imgW * 0.045f) &&
            (dyPx / (dxPx + 0.001f) < maxVertRatio) &&
            handsNotAboveHead

        if (!isPushupPosture) {
            idleResetFrames += 1
            downHoldFrames = 0
            upHoldFrames = 0
            if (idleResetFrames >= 12) {
                phase = "IDLE"
                attemptedDescent = false
                repMinElbow = 180f
                repIssues.clear()
            }
            return AnalysisOutput(
                LivePoseState(
                    phase = phase,
                    reps = reps,
                    elbowAngle = elbowAngle.roundToInt(),
                    bodyAngle = bodyAngle.roundToInt(),
                    depthPercent = 0f,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Şınav pozisyonuna geç — yan profilden tüm gövden görünsün",
                    currentScore = getAverageScore(),
                    landmarks = landmarks,
                    activeSideRight = useRight,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
            )
        } else {
            idleResetFrames = 0
        }

        val t = variation.thresholds
        val activeIssues = mutableListOf<FormIssue>()

        // Hip sag vs hip pike
        if (bodyAngle < t.minBodyAngle) {
            val spanX = lower.x - shoulder.x
            if (abs(spanX) > 0.05f) {
                val ratio = ((hip.x - shoulder.x) / spanX).coerceIn(0f, 1f)
                val expectedY = shoulder.y + ratio * (lower.y - shoulder.y)
                if (hip.y > expectedY + 0.015f) {
                    activeIssues.add(FormIssue.HIP_SAG)
                } else if (hip.y < expectedY - 0.015f) {
                    activeIssues.add(FormIssue.HIP_PIKE)
                }
            }
        }

        // Head drop (only flag if head drops clearly below shoulder for 6+ consecutive frames)
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
            if (shoulder.y > maxShoulderY) maxShoulderY = shoulder.y
            repIssues.addAll(activeIssues)
            if (FormIssue.HIP_SAG in activeIssues || FormIssue.HIP_PIKE in activeIssues) {
                repBrokenFormFrames += 1
            }
        }

        var completedRep: RepRecord? = null
        var voiceCue: String? = null

        // 3. Debounced state machine
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
                        topShoulderY = shoulder.y
                        maxShoulderY = shoulder.y
                        repIssues.clear()
                    }
                } else {
                    upHoldFrames = 0
                }
            }
            "UP" -> {
                if (elbowAngle >= t.upAngle - 8f && !attemptedDescent) {
                    topShoulderY = topShoulderY * 0.8f + shoulder.y * 0.2f
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

                        val formBroken = repBrokenFormFrames >= 4 ||
                            FormIssue.HIP_SAG in activeIssues ||
                            FormIssue.HIP_PIKE in activeIssues

                        if (durationMs >= 500L && verticalTravel >= 0.025f) {
                            if (formBroken) {
                                // Do NOT count rep when form is RED (hip sag / hip pike)!
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
                        topShoulderY = shoulder.y
                        maxShoulderY = shoulder.y
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
                landmarks = landmarks,
                activeSideRight = useRight,
                imageWidth = imgW,
                imageHeight = imgH
            ),
            repCompleted = completedRep,
            voiceCue = voiceCue
        )
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
