package com.sinavkoc.app.pose

import com.sinavkoc.app.model.FormSeverity
import com.sinavkoc.app.model.PosePoint
import com.sinavkoc.app.model.PushupCatalog
import com.sinavkoc.app.model.PushupVariationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushupAnalyzerTest {

    private fun makePose(elbowBend: Float, hipSag: Float = 0f): List<PosePoint> {
        val list = MutableList(33) { PosePoint(0.5f, 0.5f, 0.1f) }
        list[7] = PosePoint(0.24f, 0.42f + elbowBend * 0.14f, 0.99f)
        list[11] = PosePoint(0.34f, 0.45f + elbowBend * 0.16f, 0.99f)
        list[13] = PosePoint(0.33f - elbowBend * 0.12f, 0.58f + elbowBend * 0.05f, 0.99f)
        list[15] = PosePoint(0.35f, 0.72f, 0.99f)
        list[23] = PosePoint(0.58f, 0.54f + elbowBend * 0.09f + hipSag, 0.99f)
        list[25] = PosePoint(0.74f, 0.62f + elbowBend * 0.04f, 0.99f)
        list[27] = PosePoint(0.88f, 0.70f, 0.99f)
        return list
    }

    @Test
    fun countsValidPushupWithRealisticTiming() {
        val analyzer = PushupAnalyzer(PushupCatalog.get(PushupVariationId.STANDARD))
        var t = 1000L
        repeat(8) {
            analyzer.analyze(makePose(0f), 480, 640, false, t)
            t += 50L
        }
        repeat(10) {
            analyzer.analyze(makePose(1f), 480, 640, false, t)
            t += 60L
        }
        repeat(10) {
            analyzer.analyze(makePose(0f), 480, 640, false, t)
            t += 60L
        }
        assertEquals(1, analyzer.getReps())
    }

    @Test
    fun blocksCountingWhilePhoneIsShaking() {
        val analyzer = PushupAnalyzer(PushupCatalog.get(PushupVariationId.STANDARD))
        var t = 1000L
        repeat(10) {
            analyzer.analyze(makePose(if (it % 2 == 0) 0f else 1f), 480, 640, isPhoneShaking = true, nowMs = t)
            t += 200L
        }
        assertEquals(0, analyzer.getReps())
    }

    @Test
    fun countsDeepKneePushupWhenShouldersReachWristLevel() {
        val analyzer = PushupAnalyzer(PushupCatalog.get(PushupVariationId.KNEE))
        fun kneePose(bend: Float): List<PosePoint> {
            val list = MutableList(33) { PosePoint(0.5f, 0.5f, 0.1f) }
            // Realistic steeper knee-pushup torso where shoulder drops to wrist height at bottom
            list[7] = PosePoint(0.28f, 0.44f + bend * 0.24f, 0.95f)
            list[11] = PosePoint(0.35f, 0.48f + bend * 0.24f, 0.95f) // bottom y = 0.72 == wrist y!
            list[13] = PosePoint(0.33f - bend * 0.14f, 0.60f + bend * 0.08f, 0.95f)
            list[15] = PosePoint(0.35f, 0.72f, 0.95f)
            list[23] = PosePoint(0.50f, 0.61f + bend * 0.09f, 0.95f)
            list[25] = PosePoint(0.65f, 0.73f, 0.95f)
            list[27] = PosePoint(0.75f, 0.69f, 0.30f)
            return list
        }

        var t = 1000L
        repeat(6) {
            analyzer.analyze(kneePose(0f), 480, 640, false, t)
            t += 60L
        }
        repeat(8) {
            analyzer.analyze(kneePose(1f), 480, 640, false, t)
            t += 80L
        }
        repeat(8) {
            analyzer.analyze(kneePose(0f), 480, 640, false, t)
            t += 80L
        }
        assertEquals(1, analyzer.getReps())
    }

    @Test
    fun detectsHipSagFormError() {
        val analyzer = PushupAnalyzer(PushupCatalog.get(PushupVariationId.STANDARD))
        var sawError = false
        var t = 1000L
        repeat(10) {
            val out = analyzer.analyze(makePose(0.2f, hipSag = 0.14f), 480, 640, false, t)
            if (out.state.severity == FormSeverity.ERROR) sawError = true
            t += 50L
        }
        assertTrue(sawError)
    }
}
