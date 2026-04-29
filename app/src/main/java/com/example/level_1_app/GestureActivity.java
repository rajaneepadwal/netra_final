package com.example.level_1_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;


import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GestureActivity extends AppCompatActivity {

    private static final String TAG = "GestureActivity";
    private static final int CAMERA_PERMISSION_CODE = 200;

    private PreviewView previewView;
    private HandOverlayView handOverlay;
    private TextView tvGestureLabel;

    private TextView tvStatus;

    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private BluetoothConnectionManager bluetoothConnection;

    private String lastSentCommand   = "S*";
    private String candidateCommand  = "S*";
    private int    holdCount         = 0;
    private static final int HOLD_THRESHOLD = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        previewView   = findViewById(R.id.previewView);
        handOverlay   = findViewById(R.id.handOverlay);
        tvGestureLabel = findViewById(R.id.tvGestureLabel);
        tvStatus      = findViewById(R.id.tvStatus);

        bluetoothConnection = BluetoothConnectionManager.getInstance();
        cameraExecutor = Executors.newSingleThreadExecutor();

        updateConnectionStatus();

        if (hasCameraPermission()) {
            setupHandLandmarker();
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (handLandmarker != null) {
            handLandmarker.close();
            handLandmarker = null;
        }

        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdownNow(); // stronger than shutdown()
        }

        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData("S*");
        }
    }
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupHandLandmarker();
            startCamera();
        }
    }
    private void setupHandLandmarker() {
        HandLandmarkerOptions options = HandLandmarkerOptions.builder()
                .setBaseOptions(
                        BaseOptions.builder()
                                .setModelAssetPath("hand_landmarker.task")
                                .build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .build();

        handLandmarker = HandLandmarker.createFromOptions(this, options);
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview, analysis);

            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void analyzeFrame(ImageProxy imageProxy) {
        if (handLandmarker == null) {
            imageProxy.close();
            return;
        }

        Bitmap bitmap = imageProxy.toBitmap();
        imageProxy.close();
        if (bitmap == null) return;

        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
        long timestamp = SystemClock.uptimeMillis();

        HandLandmarkerResult result = handLandmarker.detectForVideo(mpImage, timestamp);

        if (result.landmarks().isEmpty()) {
            processCandidate("S*", "✊ No hand");
            runOnUiThread(() -> handOverlay.clear());
            return;
        }

        List<NormalizedLandmark> hand = result.landmarks().get(0);

        String gesture = GestureClassifier.classify(hand);
        String label = gestureLabel(gesture);

        runOnUiThread(() -> handOverlay.setLandmarks(hand));
        processCandidate(gesture, label);
    }
    private String gestureLabel(String command) {
        switch (command) {
            case "F*": return "☝️ FORWARD";
            case "B*": return "✌️ BACK";
            case "L*": return "🖐️ LEFT";
            case "R*": return "🤙 RIGHT";
            default:   return "✊ STOP";
        }
    }
    private void processCandidate(String detected, String label) {
        if (detected.equals(candidateCommand)) {
            holdCount++;
        } else {
            candidateCommand = detected;
            holdCount = 0;
        }

        if (holdCount >= HOLD_THRESHOLD) {
            if (!detected.equals(lastSentCommand)) {
                lastSentCommand = detected;
                sendCommand(detected);
            }
        }

        runOnUiThread(() -> {
            tvGestureLabel.setText(label);
        });
    }
    private void sendCommand(String command) {
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData(command);
            Log.d(TAG, "Sent: " + command);
        } else {
            runOnUiThread(() ->
                    Toast.makeText(this, "Not Connected!", Toast.LENGTH_SHORT).show());
        }
        runOnUiThread(this::updateConnectionStatus);
    }

    private void updateConnectionStatus() {
        if (tvStatus == null) return;
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            tvStatus.setText("CONNECTED");
            tvStatus.setTextColor(Color.GREEN);
        } else {
            tvStatus.setText("DISCONNECTED");
            tvStatus.setTextColor(Color.RED);
        }
    }


}