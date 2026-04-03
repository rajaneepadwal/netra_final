package com.example.level_1_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;


public class ColorActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2
{
    private static final String TAG = "ColorFollowing";
    private static final int CAMERA_PERMISSION_CODE = 10;

    private JavaCameraView cameraView;
    private String lastCommand = "S";
    private BluetoothConnectionManager bluetoothConnection;
    private ColorPicker colorPicker;
    private LinearLayout lightingControls;
    private TextView txtStatus, txtFunc;
    private ColorPicker.LightingMode lightingMode = null;
    private Mat currentHSV;
    private Handler uiHandler;
    private Toast currentToast;
    private long lastLostTime = 0;
    private Rect referenceBox;
    private double rgbaArea = 0;
    private boolean isTrackingBlack = false;
    private Point referenceCenter;

    private static final int MIN_CONTOUR_AREA = 800;
    private static final long LOST_OBJECT_DELAY = 2000;

    private static final float out_X = 0.35f;
    private static final float out_Y = 0.15f;
    private static final float TOO_CLOSE_AREA = 0.4f;

    private long lastPromptTime = 0;
    private static final long PROMPT_INTERVAL = 5000;

    // ── Anti-jitter: command must repeat N consecutive frames before it's sent ─
    private static final int JITTER_FRAMES = 3;
    private String pendingCommand = "S";
    private int pendingCount = 0;
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_following);

        colorPicker = new ColorPicker();
        uiHandler = new Handler(Looper.getMainLooper());

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV init failed");
        } else {
            Log.d(TAG, "OpenCV initialized successfully");
        }

        cameraView = findViewById(R.id.camera_view);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);

        lightingControls = findViewById(R.id.lighting_controls);
        txtStatus = findViewById(R.id.txtStatus);
        txtFunc = findViewById(R.id.txtFunc);

        Button btnReconnect = findViewById(R.id.btnReconnect);
        Button btnBack = findViewById(R.id.btnBack);

        bluetoothConnection = BluetoothConnectionManager.getInstance();
        updateBluetoothUI();

        btnReconnect.setOnClickListener(v -> reconnectBluetooth());
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, MenuActivity.class));
            finish();
        });

        // Touch listener set ONCE here — NOT in onResume
        setupTouchPicking();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchPicking() {
        cameraView.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
            if (currentHSV == null || currentHSV.empty()) return true;

            // ── LOCK: block re-picking while a color is already tracked ───────
            if (colorPicker.isColorPicked()) {
                showToast("Press RESET to pick a new color");
                return true;
            }
            // ──────────────────────────────────────────────────────────────────

            if (lightingMode == null) {
                showToast("Select lighting mode first");
                lightingControls.setVisibility(View.VISIBLE);
                return true;
            }

            int frameW = currentHSV.cols();
            int frameH = currentHSV.rows();
            int viewW  = cameraView.getWidth();
            int viewH  = cameraView.getHeight();

            int x = (int) (event.getX() * frameW / viewW);
            int y = (int) (event.getY() * frameH / viewH);

            if (x < 0 || y < 0 || x >= frameW || y >= frameH) return true;

            if (colorPicker.pickColor(currentHSV, x, y)) {
                isTrackingBlack = colorPicker.isBlackColor();
                lightingControls.setVisibility(View.GONE);

                // Distinct toast so user understands why they see a white mask
                if (isTrackingBlack) {
                    showToast("Black selected — showing white mask. Tracking started");
                } else {
                    showToast("Color locked. Tracking started");
                }
            }
            return true;
        });
    }

    private void setupReferenceBox(int frameWidth, int frameHeight) {
        int marginX = (int)(frameWidth  * out_X);
        int marginY = (int)(frameHeight * out_Y);
        referenceBox = new Rect(marginX, marginY,
                frameWidth  - 2 * marginX,
                frameHeight - 2 * marginY);
        referenceCenter = new Point(
                referenceBox.x + referenceBox.width  / 2.0,
                referenceBox.y + referenceBox.height / 2.0
        );
        Log.d(TAG, "Ref box (FRAME): " + referenceBox);
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();
        rgbaArea = rgba.total();

        if (currentHSV == null) currentHSV = new Mat();

        Mat rgb = new Mat();
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(rgb, currentHSV, Imgproc.COLOR_RGB2HSV);
        Imgproc.GaussianBlur(currentHSV, currentHSV, new Size(5, 5), 0);
        rgb.release();

        if (lightingMode == null) {
            showPromptOnce("Click RESET COLOR.");
            return rgba;
        }

        if (!colorPicker.isColorPicked()) {
            showPromptOnce("Touch any color on screen");
            return rgba;
        }

        String raw    = processTracking(rgba, currentHSV);
        String stable = applyJitterFilter(raw);
        sendIfChanged(stable);
        return rgba;
    }

    /**
     * Jitter filter: only commit a new command after it repeats JITTER_FRAMES times in a row.
     * Returns the current stable (last committed) command until the threshold is met.
     */
    private String applyJitterFilter(String newCmd) {
        if (newCmd.equals(pendingCommand)) {
            pendingCount++;
        } else {
            pendingCommand = newCmd;
            pendingCount   = 1;
        }
        return (pendingCount >= JITTER_FRAMES) ? newCmd : lastCommand;
    }

    private String processTracking(Mat rgba, Mat hsv) {
        List<Scalar[]> allRanges = colorPicker.getColorRanges();
        Mat mask = new Mat();

        // Build mask for all color ranges
        for (Scalar[] range : allRanges) {
            Mat tempMask = new Mat();
            Core.inRange(hsv, range[0], range[1], tempMask);
            if (mask.empty()) tempMask.copyTo(mask);
            else Core.bitwise_or(mask, tempMask, mask);
            tempMask.release();
        }

        // Morphology: remove noise (open) then fill gaps (dilate)
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,   kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_DILATE, kernel);

        // ── Display: real color on black bg, or white on black for black objects ──
        Mat displayMat = Mat.zeros(rgba.size(), rgba.type());

        if (isTrackingBlack) {
            Mat whiteOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
            whiteOverlay.copyTo(displayMat, mask);
            whiteOverlay.release();
        } else {
            rgba.copyTo(displayMat, mask);
        }

        displayMat.copyTo(rgba);
        // ──────────────────────────────────────────────────────────────────────────

        List<MatOfPoint> contours  = new ArrayList<>();
        Mat              hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        String command = "S";

        if (!contours.isEmpty()) {
            contours.sort((a, b) ->
                    Double.compare(Imgproc.contourArea(b), Imgproc.contourArea(a)));
            MatOfPoint largest = contours.get(0);
            double area = Imgproc.contourArea(largest);

            if (area > MIN_CONTOUR_AREA) {
                Rect  rect      = Imgproc.boundingRect(largest);
                Point objCenter = new Point(
                        rect.x + rect.width  / 2.0,
                        rect.y + rect.height / 2.0);

                Imgproc.circle(rgba, objCenter, 6, new Scalar(0, 255, 0), -1);

                if (area > rgbaArea * TOO_CLOSE_AREA) {
                    command = "S";
                } else {
                    command = getPositionCommand(rect);
                }

                Imgproc.rectangle(rgba, rect.tl(), rect.br(), new Scalar(0, 255, 0), 3);
            }
        } else {
            long now = System.currentTimeMillis();
            if (now - lastLostTime > LOST_OBJECT_DELAY) {
                lastLostTime = now;
                showPromptOnce("Object lost");
            }
        }

        // Cleanup
        mask.release();
        kernel.release();
        hierarchy.release();
        displayMat.release();
        for (MatOfPoint c : contours) c.release();

        return command;
    }

    private void sendIfChanged(String newCmd) {
        final String cmd = (newCmd == null) ? "S" : newCmd;
        if (!cmd.equals(lastCommand)) {
            lastCommand = cmd;
            Log.d(TAG, "CMD: " + cmd + "*");
            if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
                bluetoothConnection.sendData(cmd + "*");
            }
            uiHandler.post(() -> txtFunc.setText(cmd + "*"));
        }
    }

    private String getPositionCommand(Rect objRect) {
        Point objCenter = new Point(
                objRect.x + objRect.width  / 2.0,
                objRect.y + objRect.height / 2.0);

        if (referenceBox == null) return "S";

        if (referenceBox.contains(objCenter))                   return "F"; // inside box  → forward
        if (objCenter.x < referenceBox.x)                      return "L"; // left of box → turn left
        if (objCenter.x > referenceBox.x + referenceBox.width) return "R"; // right       → turn right
        if (objCenter.y < referenceBox.y)                      return "B"; // above box   → back

        return "S";
    }

    private void showPromptOnce(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastPromptTime > PROMPT_INTERVAL) {
            lastPromptTime = now;
            showToast(msg);
        }
    }

    public void onResetClick(View v) {
        colorPicker.reset();

        lightingMode    = null;
        isTrackingBlack = false;
        lastCommand     = "S";

        // Reset jitter filter
        pendingCommand = "S";
        pendingCount   = 0;

        lightingControls.setVisibility(View.VISIBLE);
        txtFunc.setText("S*");

        showToast("Reset complete. Select lighting mode");
    }

    private void showToast(String msg) {
        uiHandler.post(() -> {
            if (currentToast != null) currentToast.cancel();
            currentToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
            currentToast.show();
        });
    }

    private void reconnectBluetooth() {
        Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show();
        bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
            @Override
            public void onReconnectSuccess() {
                updateBluetoothUI();
                Toast.makeText(ColorActivity.this, "Reconnected", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onReconnectFailed(String error) {
                updateBluetoothUI();
                Toast.makeText(ColorActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startCamera() {
        cameraView.setCameraPermissionGranted();
        cameraView.enableView();
    }

    private void updateBluetoothUI() {
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            txtStatus.setText("CONNECTED");
            txtStatus.setTextColor(Color.GREEN);
        } else {
            txtStatus.setText("DISCONNECTED");
            txtStatus.setTextColor(Color.GRAY);
        }
    }

    public void onGoodLightingClick(View v) {
        if (colorPicker.isColorPicked()) {
            showToast("Reset to change lighting");
            return;
        }
        lightingMode = ColorPicker.LightingMode.GOOD_LIGHTING;
        colorPicker.setLightingMode(lightingMode);
        showToast("Good lighting selected");
        lightingControls.setVisibility(View.GONE);
    }

    public void onBadLightingClick(View v) {
        if (colorPicker.isColorPicked()) {
            showToast("Reset to change lighting");
            return;
        }
        lightingMode = ColorPicker.LightingMode.BAD_LIGHTING;
        colorPicker.setLightingMode(lightingMode);
        showToast("Bad lighting selected");
        lightingControls.setVisibility(View.GONE);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (requestCode == CAMERA_PERMISSION_CODE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            showToast("Camera permission required");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null) cameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) cameraView.disableView();
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData("S*");
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "Camera started: " + width + "x" + height);
        if (width > 0 && height > 0) {
            setupReferenceBox(width, height);
        }
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "Camera stopped");
    }

    public Mat onCameraViewDetached() {
        if (currentHSV != null) {
            currentHSV.release();
            currentHSV = null;
        }
        return new Mat();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // setupTouchPicking() is NOT called here — listener is registered once in onCreate
        if (cameraView != null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        updateBluetoothUI();
    }
}
//package com.example.level_1_app;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.view.SurfaceView;
//import android.view.View;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//import android.widget.Toast;
//import android.view.MotionEvent;
//
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import org.opencv.android.CameraBridgeViewBase;
//import org.opencv.android.JavaCameraView;
//import org.opencv.android.OpenCVLoader;
//import org.opencv.core.Core;
//import org.opencv.core.Mat;
//import org.opencv.core.MatOfPoint;
//import org.opencv.core.Rect;
//import org.opencv.core.Scalar;
//import org.opencv.core.Size;
//import org.opencv.imgproc.Imgproc;
//import org.opencv.core.Point;
//
//import java.util.ArrayList;
//import java.util.List;
//
//
//public class ColorActivity extends AppCompatActivity
//        implements CameraBridgeViewBase.CvCameraViewListener2
//{
//    private static final String TAG = "ColorFollowing";
//    private static final int CAMERA_PERMISSION_CODE = 10;
//
//    private JavaCameraView cameraView;
//    private String lastCommand = "S";
//    private BluetoothConnectionManager bluetoothConnection;
//    private ColorPicker colorPicker;
//    private LinearLayout lightingControls;
//    private TextView txtStatus, txtFunc;
//    private ColorPicker.LightingMode lightingMode = null;
//    private Mat currentHSV;
//    private Handler uiHandler;
//    private Toast currentToast;
//    private long lastLostTime = 0;
//    private Rect referenceBox;
//    private double rgbaArea = 0;
//    private boolean isTrackingBlack = false;
//    private Point referenceCenter;
//
//    private static final int MIN_CONTOUR_AREA = 800;
//    private static final long LOST_OBJECT_DELAY = 2000;
//
//    private static final float out_X = 0.35f;
//    private static final float out_Y = 0.15f;
//    private static final float TOO_CLOSE_AREA = 0.4f;
//
//    private long lastPromptTime = 0;
//    private static final long PROMPT_INTERVAL = 5000;
//
//    // ── Anti-jitter: command must repeat N consecutive frames before it's sent ─
//    private static final int JITTER_FRAMES = 3;
//    private String pendingCommand = "S";
//    private int pendingCount = 0;
//    // ──────────────────────────────────────────────────────────────────────────
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_color_following);
//
//        colorPicker = new ColorPicker();
//        uiHandler = new Handler(Looper.getMainLooper());
//
//        if (!OpenCVLoader.initDebug()) {
//            Log.e(TAG, "OpenCV init failed");
//        } else {
//            Log.d(TAG, "OpenCV initialized successfully");
//        }
//
//        cameraView = findViewById(R.id.camera_view);
//        cameraView.setVisibility(SurfaceView.VISIBLE);
//        cameraView.setCvCameraViewListener(this);
//
//        lightingControls = findViewById(R.id.lighting_controls);
//        txtStatus = findViewById(R.id.txtStatus);
//        txtFunc = findViewById(R.id.txtFunc);
//
//        Button btnReconnect = findViewById(R.id.btnReconnect);
//        Button btnBack = findViewById(R.id.btnBack);
//
//        bluetoothConnection = BluetoothConnectionManager.getInstance();
//        updateBluetoothUI();
//
//        btnReconnect.setOnClickListener(v -> reconnectBluetooth());
//        btnBack.setOnClickListener(v -> {
//            startActivity(new Intent(this, MenuActivity.class));
//            finish();
//        });
//
//        // Touch listener set ONCE here — NOT in onResume
//        setupTouchPicking();
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                == PackageManager.PERMISSION_GRANTED) {
//            startCamera();
//        } else {
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{Manifest.permission.CAMERA},
//                    CAMERA_PERMISSION_CODE
//            );
//        }
//    }
//
//    @SuppressLint("ClickableViewAccessibility")
//    private void setupTouchPicking() {
//        cameraView.setOnTouchListener((v, event) -> {
//            if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
//            if (currentHSV == null || currentHSV.empty()) return true;
//
//            // ── LOCK: block re-picking while a color is already tracked ───────
//            if (colorPicker.isColorPicked()) {
//                showToast("Press RESET to pick a new color");
//                return true;
//            }
//            // ──────────────────────────────────────────────────────────────────
//
//            if (lightingMode == null) {
//                showToast("Select lighting mode first");
//                lightingControls.setVisibility(View.VISIBLE);
//                return true;
//            }
//
//            int frameW = currentHSV.cols();
//            int frameH = currentHSV.rows();
//            int viewW  = cameraView.getWidth();
//            int viewH  = cameraView.getHeight();
//
//            int x = (int) (event.getX() * frameW / viewW);
//            int y = (int) (event.getY() * frameH / viewH);
//
//            if (x < 0 || y < 0 || x >= frameW || y >= frameH) return true;
//
////            if (colorPicker.pickColor(currentHSV, x, y)) {
////                isTrackingBlack = colorPicker.isBlackColor();
////                lightingControls.setVisibility(View.GONE);
////
////                // Distinct toast so user understands why they see a white mask
////                if (isTrackingBlack) {
////                    showToast("Black selected — showing white mask. Tracking started");
////                } else {
////                    showToast("Color locked. Tracking started");
////                }
////            }
//            if (colorPicker.pickColor(currentHSV, x, y)) {
//                isTrackingBlack = colorPicker.isBlackColor();
//                lightingControls.setVisibility(View.GONE);
//
//                // ── Diagnostic: log what was picked so you can debug problem colors ──
//                List<Scalar[]> ranges = colorPicker.getColorRanges();
//                double[] hsvPixel = currentHSV.get(y, x);
//                Log.d(TAG, "===== COLOR PICKED =====");
//                Log.d(TAG, String.format("Touched pixel HSV → H:%.1f  S:%.1f  V:%.1f",
//                        hsvPixel[0], hsvPixel[1], hsvPixel[2]));
//                Log.d(TAG, "isTrackingBlack: " + isTrackingBlack);
//                Log.d(TAG, "lightingMode: " + lightingMode);
//                Log.d(TAG, "Total HSV ranges generated: " + ranges.size());
//                for (int i = 0; i < ranges.size(); i++) {
//                    Log.d(TAG, String.format("  Range[%d] → Lower: %s  |  Upper: %s",
//                            i, ranges.get(i)[0], ranges.get(i)[1]));
//                }
//                Log.d(TAG, "========================");
//                // ─────────────────────────────────────────────────────────────────────
//
//                if (isTrackingBlack) {
//                    showToast("Black selected — showing white mask. Tracking started");
//                } else {
//                    showToast("Color locked. Tracking started");
//                }
//            }
//            return true;
//        });
//    }
//
//    private void setupReferenceBox(int frameWidth, int frameHeight) {
//        int marginX = (int)(frameWidth  * out_X);
//        int marginY = (int)(frameHeight * out_Y);
//        referenceBox = new Rect(marginX, marginY,
//                frameWidth  - 2 * marginX,
//                frameHeight - 2 * marginY);
//        referenceCenter = new Point(
//                referenceBox.x + referenceBox.width  / 2.0,
//                referenceBox.y + referenceBox.height / 2.0
//        );
//        Log.d(TAG, "Ref box (FRAME): " + referenceBox);
//    }
//
//    @Override
//    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
//        Mat rgba = inputFrame.rgba();
//        rgbaArea = rgba.total();
//
//        if (currentHSV == null) currentHSV = new Mat();
//
//        Mat rgb = new Mat();
//        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
//        Imgproc.cvtColor(rgb, currentHSV, Imgproc.COLOR_RGB2HSV);
//        Imgproc.GaussianBlur(currentHSV, currentHSV, new Size(5, 5), 0);
//        rgb.release();
//
//        if (lightingMode == null) {
//            showPromptOnce("Click RESET COLOR.");
//            return rgba;
//        }
//
//        if (!colorPicker.isColorPicked()) {
//            showPromptOnce("Touch any color on screen");
//            return rgba;
//        }
//
//        String raw    = processTracking(rgba, currentHSV);
//        String stable = applyJitterFilter(raw);
//        sendIfChanged(stable);
//        return rgba;
//    }
//
//    /**
//     * Jitter filter: only commit a new command after it repeats JITTER_FRAMES times in a row.
//     * Returns the current stable (last committed) command until the threshold is met.
//     */
//    private String applyJitterFilter(String newCmd) {
//        if (newCmd.equals(pendingCommand)) {
//            pendingCount++;
//        } else {
//            pendingCommand = newCmd;
//            pendingCount   = 1;
//        }
//        return (pendingCount >= JITTER_FRAMES) ? newCmd : lastCommand;
//    }
//
//    private String processTracking(Mat rgba, Mat hsv) {
//        List<Scalar[]> allRanges = colorPicker.getColorRanges();
//        Mat mask = new Mat();
//
//        // Build mask for all color ranges
//        for (Scalar[] range : allRanges) {
//            Mat tempMask = new Mat();
//            Core.inRange(hsv, range[0], range[1], tempMask);
//            if (mask.empty()) tempMask.copyTo(mask);
//            else Core.bitwise_or(mask, tempMask, mask);
//            tempMask.release();
//        }
//
//        // Morphology: remove noise (open) then fill gaps (dilate)
//        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
//        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,   kernel);
//        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_DILATE, kernel);
//
//        // ── Display: real color on black bg, or white on black for black objects ──
//        Mat displayMat = Mat.zeros(rgba.size(), rgba.type());
//
//        if (isTrackingBlack) {
//            Mat whiteOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
//            whiteOverlay.copyTo(displayMat, mask);
//            whiteOverlay.release();
//        } else {
//            rgba.copyTo(displayMat, mask);
//        }
//
//        displayMat.copyTo(rgba);
//        // ──────────────────────────────────────────────────────────────────────────
//
//        List<MatOfPoint> contours  = new ArrayList<>();
//        Mat              hierarchy = new Mat();
//        Imgproc.findContours(mask, contours, hierarchy,
//                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//
//        String command = "S";
//
//        if (!contours.isEmpty()) {
//            contours.sort((a, b) ->
//                    Double.compare(Imgproc.contourArea(b), Imgproc.contourArea(a)));
//            MatOfPoint largest = contours.get(0);
//            double area = Imgproc.contourArea(largest);
//
//            if (area > MIN_CONTOUR_AREA) {
//                Rect  rect      = Imgproc.boundingRect(largest);
//                Point objCenter = new Point(
//                        rect.x + rect.width  / 2.0,
//                        rect.y + rect.height / 2.0);
//
//                Imgproc.circle(rgba, objCenter, 6, new Scalar(0, 255, 0), -1);
//
//                if (area > rgbaArea * TOO_CLOSE_AREA) {
//                    command = "S";
//                } else {
//                    command = getPositionCommand(rect);
//                }
//
//                Imgproc.rectangle(rgba, rect.tl(), rect.br(), new Scalar(0, 255, 0), 3);
//            }
//        } else {
//            long now = System.currentTimeMillis();
//            if (now - lastLostTime > LOST_OBJECT_DELAY) {
//                lastLostTime = now;
//                showPromptOnce("Object lost");
//            }
//        }
//
//        // Cleanup
//        mask.release();
//        kernel.release();
//        hierarchy.release();
//        displayMat.release();
//        for (MatOfPoint c : contours) c.release();
//
//        return command;
//    }
//
//    private void sendIfChanged(String newCmd) {
//        final String cmd = (newCmd == null) ? "S" : newCmd;
//        if (!cmd.equals(lastCommand)) {
//            lastCommand = cmd;
//            Log.d(TAG, "CMD: " + cmd + "*");
//            if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
//                bluetoothConnection.sendData(cmd + "*");
//            }
//            uiHandler.post(() -> txtFunc.setText(cmd + "*"));
//        }
//    }
//
//    private String getPositionCommand(Rect objRect) {
//        Point objCenter = new Point(
//                objRect.x + objRect.width  / 2.0,
//                objRect.y + objRect.height / 2.0);
//
//        if (referenceBox == null) return "S";
//
//        if (referenceBox.contains(objCenter))                   return "F"; // inside box  → forward
//        if (objCenter.x < referenceBox.x)                      return "L"; // left of box → turn left
//        if (objCenter.x > referenceBox.x + referenceBox.width) return "R"; // right       → turn right
//        if (objCenter.y < referenceBox.y)                      return "B"; // above box   → back
//
//        return "S";
//    }
//
//    private void showPromptOnce(String msg) {
//        long now = System.currentTimeMillis();
//        if (now - lastPromptTime > PROMPT_INTERVAL) {
//            lastPromptTime = now;
//            showToast(msg);
//        }
//    }
//
//    public void onResetClick(View v) {
//        colorPicker.reset();
//
//        lightingMode    = null;
//        isTrackingBlack = false;
//        lastCommand     = "S";
//
//        // Reset jitter filter
//        pendingCommand = "S";
//        pendingCount   = 0;
//
//        lightingControls.setVisibility(View.VISIBLE);
//        txtFunc.setText("S*");
//
//        showToast("Reset complete. Select lighting mode");
//    }
//
//    private void showToast(String msg) {
//        uiHandler.post(() -> {
//            if (currentToast != null) currentToast.cancel();
//            currentToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
//            currentToast.show();
//        });
//    }
//
//    private void reconnectBluetooth() {
//        Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show();
//        bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
//            @Override
//            public void onReconnectSuccess() {
//                updateBluetoothUI();
//                Toast.makeText(ColorActivity.this, "Reconnected", Toast.LENGTH_SHORT).show();
//            }
//            @Override
//            public void onReconnectFailed(String error) {
//                updateBluetoothUI();
//                Toast.makeText(ColorActivity.this, error, Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//
//    private void startCamera() {
//        cameraView.setCameraPermissionGranted();
//        cameraView.enableView();
//    }
//
//    private void updateBluetoothUI() {
//        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
//            txtStatus.setText("CONNECTED");
//            txtStatus.setTextColor(Color.GREEN);
//        } else {
//            txtStatus.setText("DISCONNECTED");
//            txtStatus.setTextColor(Color.GRAY);
//        }
//    }
//
//    public void onGoodLightingClick(View v) {
//        if (colorPicker.isColorPicked()) {
//            showToast("Reset to change lighting");
//            return;
//        }
//        lightingMode = ColorPicker.LightingMode.GOOD_LIGHTING;
//        colorPicker.setLightingMode(lightingMode);
//        showToast("Good lighting selected");
//        lightingControls.setVisibility(View.GONE);
//    }
//
//    public void onBadLightingClick(View v) {
//        if (colorPicker.isColorPicked()) {
//            showToast("Reset to change lighting");
//            return;
//        }
//        lightingMode = ColorPicker.LightingMode.BAD_LIGHTING;
//        colorPicker.setLightingMode(lightingMode);
//        showToast("Bad lighting selected");
//        lightingControls.setVisibility(View.GONE);
//    }
//
//    @SuppressLint("MissingSuperCall")
//    @Override
//    public void onRequestPermissionsResult(
//            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
//        if (requestCode == CAMERA_PERMISSION_CODE
//                && results.length > 0
//                && results[0] == PackageManager.PERMISSION_GRANTED) {
//            startCamera();
//        } else {
//            showToast("Camera permission required");
//        }
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (cameraView != null) cameraView.disableView();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (cameraView != null) cameraView.disableView();
//        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
//            bluetoothConnection.sendData("S*");
//        }
//    }
//
//    @Override
//    public void onCameraViewStarted(int width, int height) {
//        Log.d(TAG, "Camera started: " + width + "x" + height);
//        if (width > 0 && height > 0) {
//            setupReferenceBox(width, height);
//        }
//    }
//
//    @Override
//    public void onCameraViewStopped() {
//        Log.d(TAG, "Camera stopped");
//        if (currentHSV != null) {
//            currentHSV.release();
//            currentHSV = null;
//        }
//    }
//
//    public Mat onCameraViewDetached() {
//        if (currentHSV != null) {
//            currentHSV.release();
//            currentHSV = null;
//        }
//        return new Mat();
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        // setupTouchPicking() is NOT called here — listener is registered once in onCreate
//        if (cameraView != null
//                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                == PackageManager.PERMISSION_GRANTED) {
//            startCamera();
//        }
//        updateBluetoothUI();
//    }
//}
