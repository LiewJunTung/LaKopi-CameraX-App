package com.liewjuntung.lakopicamera

import android.graphics.ImageFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Rational
import android.util.Size
import androidx.camera.core.*
import androidx.lifecycle.LifecycleOwner

fun imageCaptureConfig(block: ImageCaptureConfig.Builder.() -> Unit): ImageCaptureConfig {
    val builder = ImageCaptureConfig.Builder()
    builder.block()
    return builder.build()
}

@DslMarker
annotation class CameraUtils

fun cameraX(lifecycleOwner: LifecycleOwner, block: CameraXBuilder.() -> Unit): CameraXWrapper {
    return CameraXBuilder(lifecycleOwner).apply(block).build()
}

data class CameraXWrapper(val preview: Preview? = null, val imageAnalysis: ImageAnalysis? = null, val imageCapture: ImageCapture? = null, val videoCapture: VideoCapture? = null)

@CameraUtils
class CameraXBuilder(private var lifecycleOwner: LifecycleOwner) {
    private lateinit var cameraXWrapper: CameraXWrapper
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    var facing: CameraX.LensFacing = CameraX.LensFacing.FRONT


    fun imageAnalysis(block: ImageAnalysisBuilder.() -> Unit) {
        imageAnalysis = ImageAnalysisBuilder(facing).apply(block).build()
    }

    fun imageCapture(block: ImageCaptureBuilder.() -> Unit) {
        imageCapture = ImageCaptureBuilder(facing).apply(block).build()
    }

    fun preview(block: PreviewBuilder.() -> Unit) {
        preview = PreviewBuilder(facing).apply(block).build()
    }

    fun build(): CameraXWrapper {
        cameraXWrapper = CameraXWrapper(preview, imageAnalysis, imageCapture)

        val useCases = ArrayList<UseCase>()
        cameraXWrapper.preview?.apply { useCases.add(this) }
        cameraXWrapper.imageAnalysis?.apply { useCases.add(this) }
        cameraXWrapper.imageCapture?.apply { useCases.add(this) }
        CameraX.bindToLifecycle(lifecycleOwner, *useCases.toTypedArray())

        return cameraXWrapper
    }
}

@CameraUtils
class PreviewBuilder(val facing: CameraX.LensFacing) {
    var width: Int = 640
    var height: Int = 480
    var aspectRatio: ASPECT_RATIO = ASPECT_RATIO.RATIO_4_3
    var onPreviewOutputUpdateListener: Preview.OnPreviewOutputUpdateListener? = null

    fun build(): Preview {
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(aspectRatio.numerator, aspectRatio.denominator))
            setTargetResolution(Size(width, height))
            setLensFacing(facing)
        }.build()

        return Preview(previewConfig).apply {
            onPreviewOutputUpdateListener = this@PreviewBuilder.onPreviewOutputUpdateListener
        }
    }
}

@CameraUtils
class ImageCaptureBuilder(val facing: CameraX.LensFacing) {
    var width: Int = 640
    var height: Int = 480
    var imageFormat: Int = ImageFormat.YUV_420_888
    var aspectRatio: ASPECT_RATIO = ASPECT_RATIO.RATIO_4_3


    fun build(): ImageCapture {
        val imageCaptureConfig = ImageCaptureConfig.Builder()
                .setBufferFormat(imageFormat)
                .setLensFacing(facing)
                .setTargetAspectRatio(Rational(aspectRatio.numerator, aspectRatio.denominator))
                .setTargetResolution(Size(width, height))
                .build()
        return ImageCapture(imageCaptureConfig)
    }
}

@CameraUtils
class ImageAnalysisBuilder(val facing: CameraX.LensFacing) {
    var width: Int = 640
    var height: Int = 480
    var analyzer: ImageAnalysis.Analyzer? = null
    var aspectRatio: ASPECT_RATIO = ASPECT_RATIO.RATIO_4_3

    fun build(): ImageAnalysis {
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(facing)
            setTargetAspectRatio(Rational(aspectRatio.numerator, aspectRatio.denominator))
            setTargetResolution(Size(width, height))
            val analyzerThread = HandlerThread("CameraAnalyser").apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))
        }.build()
        return ImageAnalysis(analyzerConfig).apply {
            analyzer = this@ImageAnalysisBuilder.analyzer
        }
    }
}

enum class ASPECT_RATIO(val numerator: Int, val denominator: Int) {
    RATIO_16_9(16, 9),
    RATIO_4_3(4, 3)
}