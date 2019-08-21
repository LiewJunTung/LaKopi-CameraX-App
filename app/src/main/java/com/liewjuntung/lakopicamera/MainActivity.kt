package com.liewjuntung.lakopicamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.util.Size
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

    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(16, 9))
            setTargetResolution(Size(1280, 720))
            setLensFacing(CameraX.LensFacing.BACK)
        }.build()

        val preview = Preview(previewConfig).apply {
            onPreviewOutputUpdateListener = Preview.OnPreviewOutputUpdateListener {
                view_finder.surfaceTexture = it.surfaceTexture
            }
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setLensFacing(lensFacing)
        }.build()

        val imageAnalysis = ImageAnalysis(analyzerConfig).apply {
            analyzer = ImageAnalysis.Analyzer { imageProxy: ImageProxy, rotationDegree: Int ->
                imageProxy.image?.apply image@{
                    val currentTimestamp = System.currentTimeMillis()
                    // Calculate the average luma no more often than every second
                    if (currentTimestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
                        labeler.processImage(
                            FirebaseVisionImage.fromMediaImage(
                                this@image,
                                FirebaseVisionImageMetadata.ROTATION_90
                            )
                        ).apply {
                            addOnSuccessListener { labels ->
                                if (labels.size > 0){
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

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
