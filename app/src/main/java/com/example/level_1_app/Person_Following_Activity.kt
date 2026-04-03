package com.example.level_1_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.level_1_app.BluetoothConnectionManager.ReconnectCallback
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Person_Following_Activity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var spinner: Spinner
    private lateinit var overlayView: OverlayView
    private lateinit var btnBack: Button
    private lateinit var btnReconnect: Button
    private lateinit var txtStatus: TextView
    private lateinit var objectDetector: ObjectDetector
    private lateinit var bluetoothConnection: BluetoothConnectionManager
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_following)

        viewFinder = findViewById(R.id.viewFinder)
        spinner = findViewById(R.id.spinner)
        overlayView = findViewById(R.id.overlayView)
        btnBack = findViewById(R.id.btnBack)
        btnReconnect = findViewById(R.id.btnReconnect)
        txtStatus = findViewById(R.id.txtStatus)
        btnBack = findViewById(R.id.btnBack)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val objects = arrayOf("Person", "Bottle", "Laptop", "Chair")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, objects)

        objectDetector = ObjectDetector(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
        btnBack.setOnClickListener { startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }
        bluetoothConnection = BluetoothConnectionManager.getInstance()
        updateBluetoothUI()
        btnReconnect.setOnClickListener { reconnectBluetooth() }


    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // image-analysis processes frames in the background without stopping the UI.
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val bitmap = imageProxy.toBitmap()
                processDetection(bitmap)
                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e("Tracking", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processDetection(bitmap: Bitmap) {
        val detections = objectDetector.detect(bitmap)

        val scaleX = viewFinder.width.toFloat() / bitmap.width
        val scaleY = viewFinder.height.toFloat() / bitmap.height

        val targetLabel = spinner.selectedItem.toString()
        val target = detections.firstOrNull { it.label.equals(targetLabel) }

        runOnUiThread {
            if (target != null) {
                overlayView.setResults(listOf(target), scaleX, scaleY)
                calculateMovement(target.box, bitmap.width, bitmap.height)
            } else {
                overlayView.setResults(emptyList(), 1f, 1f)
                sendCommand("S*")
            }
        }
    }

    private fun calculateMovement(box: RectF, imgWidth: Int, imgHeight: Int) {
        val boxCenter = box.centerX()
        val screenCenter = imgWidth / 2f
        val centerMargin = imgWidth * 0.15f

        val boxWidthPercent = box.width() / imgWidth
        val boxHeightPercent = box.height() / imgHeight

        val command = when {
            boxWidthPercent > 0.50f && boxHeightPercent > 0.70f -> "S*"
            boxCenter < screenCenter - centerMargin -> "L*"
            boxCenter > screenCenter + centerMargin -> "R*"
            else -> "F*"
        }
        sendCommand(command)

    }

    private fun sendCommand(command: String) {
        if (bluetoothConnection.isConnected) {
            bluetoothConnection.sendData(command)
            Log.d("Tracking", "Sent: $command")
        }
    }
    private fun reconnectBluetooth() {
        Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show()

        bluetoothConnection.reconnectToLastDevice(this, object : ReconnectCallback {
            override fun onReconnectSuccess() {
                updateBluetoothUI()
                Toast.makeText(
                    this@Person_Following_Activity,
                    "Reconnected", Toast.LENGTH_SHORT
                ).show()
            }

            override fun onReconnectFailed(error: String) {
                updateBluetoothUI()
                Toast.makeText(
                    this@Person_Following_Activity,
                    error, Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
    private fun updateBluetoothUI() {
        if (bluetoothConnection.isConnected) {
            txtStatus.text = "CONNECTED"
            txtStatus.setTextColor(Color.GREEN)
        } else {
            txtStatus.text = "DISCONNECTED"
            txtStatus.setTextColor(Color.GRAY)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        bluetoothConnection.sendData("S*")
    }
}