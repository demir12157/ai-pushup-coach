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

    private var repStartMs = 0L
    private var repMinElbow = 180f
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
        repStartMs = 0L
        repMinElbow = 180f
        repIssues.clear()
        attemptedDescent = false
        lastShallowAlertMs = 0L
    }

    fun getReps(): Int = reps
    fun getIssueCounts(): Map<FormIssue, Int> = issueCounts.toMap()
    fun getAverageScore(): Int =
        if (repRecords.isEmpty()) 100 else (repRecords.sumOf { it.score } / repRecords.size)

    fun analyze(landmarks: List<PosePoint>, nowMs: Long = System.currentTimeMillis()): AnalysisOutput {
        if (landmarks.size < 29) {
            return AnalysisOutput(
                LivePoseState(
                    phase = phase,
                    reps = reps,
                    elbowAngle = smoothedElbow?.roundToInt(),
                    bodyAngle = smoothedBody?.roundToInt(),
                    depthPercent = 0f,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Kameraya yan profilden tüm vücudun görünecek şekilde geç",
                    currentScore = getAverageScore(),
                    landmarks = landmarks
                )
            )
        }

        val leftVis = listOf(11, 13, 15, 23).map { landmarks[it].visibility }.average().toFloat()
        val rightVis = listOf(12, 14, 16, 24).map { landmarks[it].visibility }.average().toFloat()
        val useRight = rightVis > leftVis
        val bestVis = max(leftVis, rightVis)

        if (bestVis < 0.35f) {
            return AnalysisOutput(
                LivePoseState(
                    phase = phase,
                    reps = reps,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Vücudun net görünmüyor, kameradan biraz uzaklaş",
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

        val alpha = 0.35f
        val elbowAngle = smoothedElbow?.let { it * (1f - alpha) + rawElbow * alpha } ?: rawElbow
        val bodyAngle = smoothedBody?.let { it * (1f - alpha) + rawBody * alpha } ?: rawBody
        smoothedElbow = elbowAngle
        smoothedBody = bodyAngle

        val dx = abs(shoulder.x - hip.x)
        val dy = abs(shoulder.y - hip.y)
        val maxVertRatio = if (variation.id == PushupVariationId.INCLINE) 2.4f else 1.6f
        val isPushupPosture = dx > 0.04f && (dy / (dx + 0.001f)) < maxVertRatio

        if (!isPushupPosture && phase == "IDLE") {
            return AnalysisOutput(
                LivePoseState(
                    phase = "IDLE",
                    reps = reps,
                    elbowAngle = elbowAngle.roundToInt(),
                    bodyAngle = bodyAngle.roundToInt(),
                    depthPercent = 0f,
                    severity = FormSeverity.WARNING,
                    feedbackText = "Şınav pozisyonuna geç (plank duruşu)",
                    currentScore = getAverageScore(),
                    landmarks = landmarks,
                    activeSideRight = useRight
                )
            )
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
        if (ear.visibility > 0.4f) {
            val torsoLen = hypot((shoulder.x - hip.x).toDouble(), (shoulder.y - hip.y).toDouble()).toFloat()
            if (torsoLen > 0.08f && ear.y > shoulder.y + torsoLen * 0.28f) {
                activeIssues.add(FormIssue.HEAD_DROP)
            }
        }

        val depthPercent = (((t.upAngle - elbowAngle) / (t.upAngle - t.downAngle)) * 100f).coerceIn(0f, 100f)

        if (phase == "UP" || phase == "DOWN") {
            if (elbowAngle < repMinElbow) repMinElbow = elbowAngle
            repIssues.addAll(activeIssues)
        }

        var completedRep: RepRecord? = null
        var voiceCue: String? = null

        when (phase) {
            "IDLE" -> {
                if (elbowAngle >= t.upAngle - 12f && isPushupPosture) {
                    phase = "UP"
                    repStartMs = nowMs
                    repMinElbow = elbowAngle
                    repIssues.clear()
                }
            }
            "UP" -> {
                if (elbowAngle < t.upAngle - 22f && !attemptedDescent) {
                    attemptedDescent = true
                    repStartMs = nowMs
                }
                if (elbowAngle <= t.downAngle) {
                    phase = "DOWN"
                } else if (attemptedDescent &&
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
            "DOWN" -> {
                if (elbowAngle >= t.upAngle) {
                    reps += 1
                    val durationMs = if (repStartMs > 0L) nowMs - repStartMs else 1500L
                    if (durationMs < 750L) {
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

                    phase = "UP"
                    repStartMs = nowMs
                    repMinElbow = 180f
                    repIssues.clear()
                    attemptedDescent = false
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
