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

    // Per-rep trackers
    private var repStartMs = 0L
    private var repMinElbow = 180f
    private var topShoulderY = 0f
    private var maxShoulderY = 0f
    private val repIssues = mutableSetOf<FormIssue>()
    private var attemptedDescent = false
    private var lastShallowAlertMs = 0L

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
        repStartMs = 0L
        repMinElbow = 180f
        topShoulderY = 0f
        maxShoulderY = 0f
        repIssues.clear()
        attemptedDescent = false
        lastShallowAlertMs = 0L
    }

    fun getReps(): Int = reps
    fun getIssueCounts(): Map<FormIssue, Int> = issueCounts.toMap()
    fun getAverageScore(): Int =
        if (repRecords.isEmpty()) 100 else (repRecords.sumOf { it.score } / repRecords.size)

    fun analyze(
        landmarks: List<PosePoint>,
        isPhoneShaking: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): AnalysisOutput {
        // 1. Hardware IMU shake detection (real gyroscope/accelerometer — zero false positives when stationary!)
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
                    activeSideRight = lastUseRight
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
                    activeSideRight = lastUseRight
                )
            )
        }

        val leftIdx = listOf(11, 13, 15, 23)
        val rightIdx = listOf(12, 14, 16, 24)
        val leftVis = leftIdx.map { landmarks[it].visibility }.average().toFloat()
        val rightVis = rightIdx.map { landmarks[it].visibility }.average().toFloat()

        // Hysteresis so left/right doesn't flip-flop rapidly
        val useRight = if (lastUseRight) {
            rightVis + 0.08f >= leftVis
        } else {
            rightVis > leftVis + 0.08f
        }
        lastUseRight = useRight

        val chosenIdx = if (useRight) rightIdx else leftIdx
        val minKeyVis = chosenIdx.minOf { landmarks[it].visibility }

        if (minKeyVis < 0.35f) {
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
                    activeSideRight = useRight
                )
            )
        }

        val shoulder = landmarks[if (useRight) 12 else 11]
        val elbow = landmarks[if (useRight) 14 else 13]
        val wrist = landmarks[if (useRight) 16 else 15]
        val hip = landmarks[if (useRight) 24 else 23]
        val knee = landmarks[if (useRight) 26 else 25]
        val ankle = landmarks[if (useRight) 28 else 27]
        val ear = landmarks[if (useRight) 8 else 7]

        val lower = if (variation.thresholds.useKneeLine || ankle.visibility < 0.35f) knee else ankle

        val rawElbow = calcAngle(shoulder, elbow, wrist)
        val rawBody = calcAngle(shoulder, hip, lower)

        val alpha = 0.32f
        val elbowAngle = smoothedElbow?.let { it * (1f - alpha) + rawElbow * alpha } ?: rawElbow
        val bodyAngle = smoothedBody?.let { it * (1f - alpha) + rawBody * alpha } ?: rawBody
        smoothedElbow = elbowAngle
        smoothedBody = bodyAngle

        // 2. Continuous pushup posture check (plank orientation + hands below shoulders)
        val dx = abs(shoulder.x - hip.x)
        val dy = abs(shoulder.y - hip.y)
        val maxVertRatio = if (variation.id == PushupVariationId.INCLINE) 2.2f else 1.45f
        val handsBelowShoulders = wrist.y > shoulder.y + 0.02f
        val isPushupPosture = dx > 0.06f && (dy / (dx + 0.001f)) < maxVertRatio && handsBelowShoulders

        if (!isPushupPosture) {
            idleResetFrames += 1
            downHoldFrames = 0
            upHoldFrames = 0
            if (idleResetFrames >= 6) {
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
                    feedbackText = "Şınav (plank) pozisyonuna geç — eller yerde, vücut yatay",
                    currentScore = getAverageScore(),
                    landmarks = landmarks,
                    activeSideRight = useRight
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

        // Head drop
        if (ear.visibility > 0.45f) {
            val torsoLen = hypot((shoulder.x - hip.x).toDouble(), (shoulder.y - hip.y).toDouble()).toFloat()
            if (torsoLen > 0.08f && ear.y > shoulder.y + torsoLen * 0.28f) {
                activeIssues.add(FormIssue.HEAD_DROP)
            }
        }

        val depthPercent = (((t.upAngle - elbowAngle) / (t.upAngle - t.downAngle)) * 100f).coerceIn(0f, 100f)

        if (phase == "UP" || phase == "DOWN") {
            if (elbowAngle < repMinElbow) repMinElbow = elbowAngle
            if (shoulder.y > maxShoulderY) maxShoulderY = shoulder.y
            repIssues.addAll(activeIssues)
        }

        var completedRep: RepRecord? = null
        var voiceCue: String? = null

        // 3. Debounced state machine (3 consecutive frames + vertical shoulder travel)
        when (phase) {
            "IDLE" -> {
                if (elbowAngle >= t.upAngle - 12f) {
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

                        if (durationMs >= 500L && verticalTravel >= 0.025f) {
                            reps += 1
                            if (durationMs < 800L) {
                                repIssues.add(FormIssue.TOO_FAST)
                            }

                            val issues = repIssues.toList()
                            issues.forEach { issue ->
                                issueCounts[issue] = (issueCounts[issue] ?: 0) + 1
                            }

                            var score = 100
                            if (FormIssue.HIP_SAG in repIssues) score -= 20
                            if (FormIssue.HIP_PIKE in repIssues) score -= 16
                            if (FormIssue.HEAD_DROP in repIssues) score -= 10
                            if (FormIssue.TOO_FAST in repIssues) score -= 12
                            score = score.coerceIn(40, 100)

                            completedRep = RepRecord(
                                repNumber = reps,
                                minElbowAngle = repMinElbow.roundToInt(),
                                durationMs = durationMs,
                                score = score,
                                issues = issues
                            )
                            repRecords.add(completedRep)
                        }

                        phase = "UP"
                        upHoldFrames = 0
                        downHoldFrames = 0
                        repStartMs = nowMs
                        repMinElbow = 180f
                        topShoulderY = shoulder.y
                        maxShoulderY = shoulder.y
                        repIssues.clear()
                        attemptedDescent = false
                    }
                } else {
                    upHoldFrames = 0
                }
            }
        }

        val (severity, feedback) = if (activeIssues.isNotEmpty()) {
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
                activeSideRight = useRight
            ),
            repCompleted = completedRep,
            voiceCue = voiceCue
        )
    }

    private fun calcAngle(a: PosePoint, b: PosePoint, c: PosePoint): Float {
        val rad = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
            atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
        var deg = abs(Math.toDegrees(rad)).toFloat()
        if (deg > 180f) deg = 360f - deg
        return deg
    }
}
