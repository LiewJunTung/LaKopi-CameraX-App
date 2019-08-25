package com.liewjuntung.lakopicamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
private val analyzerThread = HandlerThread("FirebaseAnalysis").apply { start() }


class MainActivity : AppCompatActivity() {

    private lateinit var labeler: FirebaseVisionImageLabeler
    private val lensFacing = CameraX.LensFacing.BACK
    private var lastAnalyzedTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        labeler = FirebaseVision.getInstance().onDeviceImageLabeler

        if (allPermissionsGranted()) {
            view_finder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private val previewListener = Preview.OnPreviewOutputUpdateListener {
        Log.d("MA", "surface init")
        val parent = view_finder.parent as ViewGroup
        parent.removeView(view_finder)
        parent.addView(view_finder, 0)
        view_finder.surfaceTexture = it.surfaceTexture
    }
    private val imageAnalyser =
        ImageAnalysis.Analyzer { imageProxy: ImageProxy, _: Int ->
            imageProxy.image?.apply image@{
                val currentTimestamp = System.currentTimeMillis()
                if (currentTimestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
                    labeler.processImage(
                        FirebaseVisionImage.fromMediaImage(
                            this@image,
                            FirebaseVisionImageMetadata.ROTATION_90
                        )
                    ).apply {
                        addOnSuccessListener { labels ->
                            if (labels.size > 0) {
                                appbar.title = labels.first().text
                                textView.text = labels.joinToString("\n", limit = 3) {
                                    "${it.text}: ${it.confidence}%"
                                }
                            }
                        }
                        addOnFailureListener { e ->
                            Log.e("imageAnalysis", e.message, e)
                        }
                    }
                    lastAnalyzedTimestamp = currentTimestamp
                }
            }
        }

    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(16, 9))
            setTargetResolution(Size(1280, 720))
            setLensFacing(CameraX.LensFacing.BACK)
        }.build()
        val preview = Preview(previewConfig).apply {
            onPreviewOutputUpdateListener = previewListener
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetResolution(Size(620, 480))
            setLensFacing(lensFacing)
            setCallbackHandler(Handler(analyzerThread.looper))
        }.build()
        val imageAnalysis = ImageAnalysis(analyzerConfig).apply {
            analyzer = imageAnalyser
        }

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                view_finder.post { startCamera() }
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
