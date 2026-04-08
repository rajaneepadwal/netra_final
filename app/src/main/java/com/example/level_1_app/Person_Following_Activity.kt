package com.example.level_1_app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Person_Following_Activity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var spinner: Spinner
    private lateinit var overlayView: OverlayView
    private lateinit var objectDetector: ObjectDetector
    private lateinit var bluetoothConnection: BluetoothConnectionManager
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile private var isProcessing = false
    private var lastCommand = "S*"
    private var missedFrames = 0
    private val MAX_ALLOWED_MISSES = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_following)

        viewFinder = findViewById(R.id.viewFinder)
        spinner = findViewById(R.id.spinner)
        overlayView = findViewById(R.id.overlayView)

        val objects = arrayOf("Person", "Bottle", "Laptop", "Chair")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, objects)

        objectDetector = ObjectDetector(this)
        bluetoothConnection = BluetoothConnectionManager.getInstance()

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 320))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (isProcessing) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                isProcessing = true

                try {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val bitmap = imageProxy.toBitmap()
                    val detections = objectDetector.detect(bitmap)

                    val selectedItem = spinner.selectedItem
                    if (selectedItem != null) {
                        val targetLabel = selectedItem.toString()
                        val target = detections.filter { it.label.equals(targetLabel, true) }.maxByOrNull { it.score }

                        // 🔥 Rotation-aware coordinate mapping
                        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                        val previewWidth = if (isRotated) bitmap.height else bitmap.width
                        val previewHeight = if (isRotated) bitmap.width else bitmap.height

                        val sX = viewFinder.width.toFloat() / previewWidth
                        val sY = viewFinder.height.toFloat() / previewHeight

                        runOnUiThread {
                            if (target != null) {
                                missedFrames = 0
                                overlayView.setResults(listOf(target), sX, sY, rotationDegrees)
                                processMovement(target.box, bitmap.width)
                            } else {
                                handleLostTarget()
                            }
                        }
                    }
                } finally {
                    imageProxy.close()
                    isProcessing = false
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processMovement(box: RectF, imgWidth: Int) {
        val centerX = box.centerX()
        val screenCenter = imgWidth / 2f
        val margin = imgWidth * 0.12f
        val widthPercent = box.width() / imgWidth

        val newCommand = when {
            widthPercent > 0.65f -> "S*"
            centerX < screenCenter - margin -> "L*"
            centerX > screenCenter + margin -> "R*"
            else -> "F*"
        }

        // Only send if the command changes to prevent Bluetooth flooding
        if (newCommand != lastCommand) {
            sendCommand(newCommand)
            lastCommand = newCommand
        }
    }

    private fun handleLostTarget() {
        overlayView.setResults(emptyList(), 1f, 1f, 0)
        missedFrames++

        // Anti-Stutter: Maintain last command for 5 frames if target is lost
        if (missedFrames > MAX_ALLOWED_MISSES && lastCommand != "S*") {
            sendCommand("S*")
            lastCommand = "S*"
        }
    }

    private fun sendCommand(command: String) {
        if (bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData(command)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        sendCommand("S*")
    }
}