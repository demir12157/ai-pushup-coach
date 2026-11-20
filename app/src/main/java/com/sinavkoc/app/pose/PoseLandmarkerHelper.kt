package com.sinavkoc.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.sinavkoc.app.model.PosePoint

class PoseLandmarkerHelper(
    context: Context,
    private val onResult: (points: List<PosePoint>, imageWidth: Int, imageHeight: Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private var poseLandmarker: PoseLandmarker? = null
    private var reusableBuffer: Bitmap? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result: PoseLandmarkerResult, input ->
                    val imgW = input.width
                    val imgH = input.height
                    val firstPose = result.landmarks().firstOrNull()
                    if (firstPose != null) {
                        val points = firstPose.map { lm ->
                            PosePoint(
                                x = lm.x(),
                                y = lm.y(),
                                visibility = lm.visibility().orElse(0.5f)
                            )
                        }
                        onResult(points, imgW, imgH)
                    } else {
                        onResult(emptyList(), imgW, imgH)
                    }
                }
                .setErrorListener { e ->
                    onError(e.message ?: "Pose takip hatası")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            onError("AI modeli başlatılamadı: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val frameTime = SystemClock.uptimeMillis()
            val w = imageProxy.width
            val h = imageProxy.height

            val buffer = reusableBuffer?.takeIf { it.width == w && it.height == h }
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    reusableBuffer?.recycle()
                    reusableBuffer = it
                }

            imageProxy.planes[0].buffer.rewind()
            buffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
            val matrix = Matrix().apply {
                if (rotation != 0f) {
                    postRotate(rotation)
                }
                if (isFrontCamera) {
                    // Mirror horizontally AFTER rotation so front camera matches PreviewView
                    postScale(-1f, 1f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(buffer, 0, 0, w, h, matrix, false)
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            landmarker.detectAsync(mpImage, frameTime)
        } catch (_: Exception) {
            // Skip corrupted frame
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        try {
            poseLandmarker?.close()
        } catch (_: Exception) {
        }
        poseLandmarker = null
        reusableBuffer?.recycle()
        reusableBuffer = null
    }
}
