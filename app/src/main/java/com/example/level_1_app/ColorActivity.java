////package com.example.level_1_app;
////
////import android.Manifest;
////import android.annotation.SuppressLint;
////import android.content.Intent;
////import android.content.pm.PackageManager;
////import android.graphics.Color;
////import android.os.Bundle;
////import android.os.Handler;
////import android.os.Looper;
////import android.util.Log;
////import android.view.SurfaceView;
////import android.view.View;
////import android.widget.Button;
////import android.widget.LinearLayout;
////import android.widget.TextView;
////import android.widget.Toast;
////import android.view.MotionEvent;
////
////import androidx.annotation.NonNull;
////import androidx.appcompat.app.AppCompatActivity;
////import androidx.core.app.ActivityCompat;
////import androidx.core.content.ContextCompat;
////
////import org.opencv.android.CameraBridgeViewBase;
////import org.opencv.android.JavaCameraView;
////import org.opencv.android.OpenCVLoader;
////import org.opencv.core.Core;
////import org.opencv.core.Mat;
////import org.opencv.core.MatOfPoint;
////import org.opencv.core.Rect;
////import org.opencv.core.Scalar;
////import org.opencv.core.Size;
////import org.opencv.imgproc.Imgproc;
////import org.opencv.core.Point;
////
////import java.util.ArrayList;
////import java.util.List;
////
////public class ColorActivity extends AppCompatActivity
////        implements CameraBridgeViewBase.CvCameraViewListener2 {
////
////    private static final String TAG = "ColorFollowing";
////    private static final int CAMERA_PERMISSION_CODE = 10;
////
////    private JavaCameraView cameraView;
////    private String lastCommand = "S";
////    private BluetoothConnectionManager bluetoothConnection;
////    private ColorPicker colorPicker;
////    private LinearLayout lightingControls;
////    private TextView txtStatus, txtFunc;
////    private ColorPicker.LightingMode lightingMode = null;
////
////    // volatile: written by camera thread, read by UI touch thread
////    private volatile Mat currentHSV;
////
////    private Handler uiHandler;
////    private Toast currentToast;
////    private Rect referenceBox;
////    private double rgbaArea = 0;
////    private boolean isTrackingBlack = false;
////
////    private static final int MIN_CONTOUR_AREA = 800;
////    private static final long LOST_OBJECT_DELAY = 2000;
////
////    private static final float out_X = 0.35f;
////    private static final float out_Y = 0.15f;
////    private static final float TOO_CLOSE_AREA = 0.4f;
////
////    private long lastPromptTime = 0;
////    private static final long PROMPT_INTERVAL = 5000;
////
////    // Anti-jitter: command must repeat N consecutive frames before it's sent
////    private static final int JITTER_FRAMES = 3;
////    private String pendingCommand = "S";
////    private int pendingCount = 0;
////
////    // Tracks when the object was last lost — reset alongside other state in onResetClick()
////    private long lastLostTime = 0;
////
////    @Override
////    protected void onCreate(Bundle savedInstanceState) {
////        super.onCreate(savedInstanceState);
////        setContentView(R.layout.activity_color_following);
////
////        colorPicker = new ColorPicker();
////        uiHandler = new Handler(Looper.getMainLooper());
////
////        if (!OpenCVLoader.initDebug()) {
////            Log.e(TAG, "OpenCV init failed");
////        } else {
////            Log.d(TAG, "OpenCV initialized successfully");
////        }
////
////        cameraView = findViewById(R.id.camera_view);
////        cameraView.setVisibility(SurfaceView.VISIBLE);
////        cameraView.setCvCameraViewListener(this);
////
////        lightingControls = findViewById(R.id.lighting_controls);
////        txtStatus = findViewById(R.id.txtStatus);
////        txtFunc = findViewById(R.id.txtFunc);
////
////        Button btnReconnect = findViewById(R.id.btnReconnect);
////        Button btnBack = findViewById(R.id.btnBack);
////
////        bluetoothConnection = BluetoothConnectionManager.getInstance();
////        updateBluetoothUI();
////
////        btnReconnect.setOnClickListener(v -> reconnectBluetooth());
////        btnBack.setOnClickListener(v -> {
////            startActivity(new Intent(this, MenuActivity.class));
////            finish();
////        });
////
////        // Touch listener registered once here — NOT in onResume
////        setupTouchPicking();
////
////        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
////                == PackageManager.PERMISSION_GRANTED) {
////            startCamera();
////        } else {
////            ActivityCompat.requestPermissions(
////                    this,
////                    new String[]{Manifest.permission.CAMERA},
////                    CAMERA_PERMISSION_CODE
////            );
////        }
////    }
////
////    @SuppressLint("ClickableViewAccessibility")
////    private void setupTouchPicking() {
////        cameraView.setOnTouchListener((v, event) -> {
////            if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
////
////            Mat hsv = currentHSV; // local snapshot avoids race with camera thread
////            if (hsv == null || hsv.empty()) return true;
////
////            // Block re-picking while a color is already tracked
////            if (colorPicker.isColorPicked()) {
////                showToast("Press RESET to pick a new color");
////                return true;
////            }
////
////            if (lightingMode == null) {
////                showToast("Select lighting mode first");
////                lightingControls.setVisibility(View.VISIBLE);
////                return true;
////            }
////
////            int frameW = hsv.cols();
////            int frameH = hsv.rows();
////            int viewW  = cameraView.getWidth();
////            int viewH  = cameraView.getHeight();
////
////            int x = (int) (event.getX() * frameW / viewW);
////            int y = (int) (event.getY() * frameH / viewH);
////
////            if (x < 0 || y < 0 || x >= frameW || y >= frameH) return true;
////
////            if (colorPicker.pickColor(hsv, x, y)) {
////                isTrackingBlack = colorPicker.isBlackColor();
////                lightingControls.setVisibility(View.GONE);
////
////                // Diagnostic: log picked pixel so problem colors can be debugged
////                List<Scalar[]> ranges = colorPicker.getColorRanges();
////                double[] hsvPixel = hsv.get(y, x);
////                Log.d(TAG, "===== COLOR PICKED =====");
////                Log.d(TAG, String.format("Touched pixel HSV → H:%.1f  S:%.1f  V:%.1f",
////                        hsvPixel[0], hsvPixel[1], hsvPixel[2]));
////                Log.d(TAG, "isTrackingBlack: " + isTrackingBlack);
////                Log.d(TAG, "lightingMode: " + lightingMode);
////                Log.d(TAG, "Total HSV ranges generated: " + ranges.size());
////                for (int i = 0; i < ranges.size(); i++) {
////                    Log.d(TAG, String.format("  Range[%d] → Lower: %s  |  Upper: %s",
////                            i, ranges.get(i)[0], ranges.get(i)[1]));
////                }
////                Log.d(TAG, "========================");
////
////                if (isTrackingBlack) {
////                    showToast("Black selected. Tracking started");
////                } else if (colorPicker.isWhiteColor()) {
////                    showToast("White selected. Tracking started");
////                } else {
////                    showToast("Color locked. Tracking started");
////                }
////            }
////            return true;
////        });
////    }
////
////    private void setupReferenceBox(int frameWidth, int frameHeight) {
////        int marginX = (int) (frameWidth  * out_X);
////        int marginY = (int) (frameHeight * out_Y);
////        referenceBox = new Rect(marginX, marginY,
////                frameWidth  - 2 * marginX,
////                frameHeight - 2 * marginY);
////        Log.d(TAG, "Ref box (FRAME): " + referenceBox);
////    }
////
////    @Override
////    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
////        Mat rgba = inputFrame.rgba();
////        rgbaArea = rgba.total();
////
////        if (currentHSV == null) currentHSV = new Mat();
////
////        Mat rgb = new Mat();
////        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
////        Imgproc.cvtColor(rgb, currentHSV, Imgproc.COLOR_RGB2HSV);
////        Imgproc.GaussianBlur(currentHSV, currentHSV, new Size(5, 5), 0);
////        rgb.release();
////
////        if (lightingMode == null) {
////            showPromptOnce("Click RESET COLOR.");
////            return rgba;
////        }
////
////        if (!colorPicker.isColorPicked()) {
////            showPromptOnce("Touch any color on screen");
////            return rgba;
////        }
////
////        String raw    = processTracking(rgba, currentHSV);
////        String stable = applyJitterFilter(raw);
////        sendIfChanged(stable);
////        return rgba;
////    }
////
////    /**
////     * Jitter filter: only commit a new command after it repeats JITTER_FRAMES
////     * times in a row. Returns the last stable command until the threshold is met.
////     */
////    private String applyJitterFilter(String newCmd) {
////        if (newCmd.equals(pendingCommand)) {
////            pendingCount++;
////        } else {
////            pendingCommand = newCmd;
////            pendingCount   = 1;
////        }
////        return (pendingCount >= JITTER_FRAMES) ? newCmd : lastCommand;
////    }
////
////    private String processTracking(Mat rgba, Mat hsv) {
////        List<Scalar[]> allRanges = colorPicker.getColorRanges();
////        Mat mask = new Mat();
////
////        // Build combined mask for all color ranges
////        for (Scalar[] range : allRanges) {
////            Mat tempMask = new Mat();
////            Core.inRange(hsv, range[0], range[1], tempMask);
////            if (mask.empty()) tempMask.copyTo(mask);
////            else Core.bitwise_or(mask, tempMask, mask);
////            tempMask.release();
////        }
////
////        // Morphology: remove noise (open) then fill gaps (dilate)
////        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
////        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,   kernel);
////        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_DILATE, kernel);
////
////        // Display: real color on black bg; white mask for black objects; black mask for white objects
////        Mat displayMat = Mat.zeros(rgba.size(), rgba.type());
////
////        if (isTrackingBlack) {
////            Mat whiteOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
////            whiteOverlay.copyTo(displayMat, mask);
////            whiteOverlay.release();
////        } else if (colorPicker.isWhiteColor()) {
////            Mat whiteCanvas = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
////            whiteCanvas.copyTo(displayMat);
////            Mat blackOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(0, 0, 0, 255));
////            blackOverlay.copyTo(displayMat, mask);
////            blackOverlay.release();
////            whiteCanvas.release();
////        } else {
////            rgba.copyTo(displayMat, mask);
////        }
////
////        displayMat.copyTo(rgba);
////        displayMat.release();
////
////        List<MatOfPoint> contours  = new ArrayList<>();
////        Mat              hierarchy = new Mat();
////        Imgproc.findContours(mask, contours, hierarchy,
////                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
////
////        String command = "S";
////
////        if (!contours.isEmpty()) {
////            contours.sort((a, b) ->
////                    Double.compare(Imgproc.contourArea(b), Imgproc.contourArea(a)));
////            MatOfPoint largest = contours.get(0);
////            double area = Imgproc.contourArea(largest);
////
////            if (area > MIN_CONTOUR_AREA) {
////                Rect  rect      = Imgproc.boundingRect(largest);
////                Point objCenter = new Point(
////                        rect.x + rect.width  / 2.0,
////                        rect.y + rect.height / 2.0);
////
////                Imgproc.circle(rgba, objCenter, 6, new Scalar(0, 255, 0), -1);
////
////                command = (area > rgbaArea * TOO_CLOSE_AREA) ? "S" : getPositionCommand(rect);
////
////                Imgproc.rectangle(rgba, rect.tl(), rect.br(), new Scalar(0, 255, 0), 3);
////            }
////        } else {
////            long now = System.currentTimeMillis();
////            if (now - lastLostTime > LOST_OBJECT_DELAY) {
////                lastLostTime = now;
////                showPromptOnce("Object lost");
////            }
////        }
////
////        mask.release();
////        kernel.release();
////        hierarchy.release();
////        for (MatOfPoint c : contours) c.release();
////
////        return command;
////    }
////
////    private void sendIfChanged(String newCmd) {
////        if (!newCmd.equals(lastCommand)) {
////            lastCommand = newCmd;
////            Log.d(TAG, "CMD: " + newCmd + "*");
////            if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
////                bluetoothConnection.sendData(newCmd + "*");
////            }
////            uiHandler.post(() -> txtFunc.setText(newCmd + "*"));
////        }
////    }
////
////    private String getPositionCommand(Rect objRect) {
////        Point objCenter = new Point(
////                objRect.x + objRect.width  / 2.0,
////                objRect.y + objRect.height / 2.0);
////
////        if (referenceBox == null) return "S";
////
////        if (referenceBox.contains(objCenter))                   return "F"; // inside box  → forward
////        if (objCenter.x < referenceBox.x)                      return "L"; // left of box → turn left
////        if (objCenter.x > referenceBox.x + referenceBox.width) return "R"; // right       → turn right
////        if (objCenter.y < referenceBox.y)                      return "B"; // above box   → back
////
////        return "S";
////    }
////
////    private void showPromptOnce(String msg) {
////        long now = System.currentTimeMillis();
////        if (now - lastPromptTime > PROMPT_INTERVAL) {
////            lastPromptTime = now;
////            showToast(msg);
////        }
////    }
////
////    public void onResetClick(View v) {
////        colorPicker.reset();
////
////        lightingMode    = null;
////        isTrackingBlack = false;
////        lastCommand     = "S";
////        lastLostTime    = 0; // reset so "Object lost" doesn't fire immediately after re-pick
////
////        // Reset jitter filter
////        pendingCommand = "S";
////        pendingCount   = 0;
////
////        lightingControls.setVisibility(View.VISIBLE);
////        txtFunc.setText("S*");
////        if (currentHSV != null) {
////            currentHSV.release();
////            currentHSV = null;
////        }
////        showToast("Reset complete. Select lighting mode");
////    }
////
////    private void showToast(String msg) {
////        uiHandler.post(() -> {
////            if (currentToast != null) currentToast.cancel();
////            currentToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
////            currentToast.show();
////        });
////    }
////
////    private void reconnectBluetooth() {
////        Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show();
////        bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
////            @Override
////            public void onReconnectSuccess() {
////                updateBluetoothUI();
////                Toast.makeText(ColorActivity.this, "Reconnected", Toast.LENGTH_SHORT).show();
////            }
////
////            @Override
////            public void onReconnectFailed(String error) {
////                updateBluetoothUI();
////                Toast.makeText(ColorActivity.this, error, Toast.LENGTH_SHORT).show();
////            }
////        });
////    }
////
////    private void startCamera() {
////        cameraView.setCameraPermissionGranted();
////        cameraView.enableView();
////    }
////
////    private void updateBluetoothUI() {
////        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
////            txtStatus.setText("CONNECTED");
////            txtStatus.setTextColor(Color.GREEN);
////        } else {
////            txtStatus.setText("DISCONNECTED");
////            txtStatus.setTextColor(Color.GRAY);
////        }
////    }
////
////    public void onGoodLightingClick(View v) {
////        if (colorPicker.isColorPicked()) {
////            showToast("Reset to change lighting");
////            return;
////        }
////        lightingMode = ColorPicker.LightingMode.GOOD_LIGHTING;
////        colorPicker.setLightingMode(lightingMode);
////        showToast("Good lighting selected");
////        lightingControls.setVisibility(View.GONE);
////    }
////
////    public void onBadLightingClick(View v) {
////        if (colorPicker.isColorPicked()) {
////            showToast("Reset to change lighting");
////            return;
////        }
////        lightingMode = ColorPicker.LightingMode.BAD_LIGHTING;
////        colorPicker.setLightingMode(lightingMode);
////        showToast("Bad lighting selected");
////        lightingControls.setVisibility(View.GONE);
////    }
////
////    @Override
////    public void onRequestPermissionsResult(
////            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
////        super.onRequestPermissionsResult(requestCode, permissions, results);
////        if (requestCode == CAMERA_PERMISSION_CODE
////                && results.length > 0
////                && results[0] == PackageManager.PERMISSION_GRANTED) {
////            startCamera();
////        } else {
////            showToast("Camera permission required");
////        }
////    }
////
////    @Override
////    protected void onPause() {
////        super.onPause();
////        if (cameraView != null) cameraView.disableView();
////    }
////
////    @Override
////    protected void onDestroy() {
////        super.onDestroy();
////        if (cameraView != null) cameraView.disableView();
////        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
////            bluetoothConnection.sendData("S*");
////        }
////    }
////
////    @Override
////    public void onCameraViewStarted(int width, int height) {
////        Log.d(TAG, "Camera started: " + width + "x" + height);
////        if (width > 0 && height > 0) {
////            setupReferenceBox(width, height);
////        }
////    }
////
////    @Override
////    public void onCameraViewStopped() {
////        Log.d(TAG, "Camera stopped");
////        if (currentHSV != null) {
////            currentHSV.release();
////            currentHSV = null;
////        }
////    }
////
////    @Override
////    protected void onResume() {
////        super.onResume();
////        // setupTouchPicking() is NOT called here — listener is registered once in onCreate
////        if (cameraView != null
////                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
////                == PackageManager.PERMISSION_GRANTED) {
////            startCamera();
////        }
////        updateBluetoothUI();
////    }
////}
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
//public class ColorActivity extends AppCompatActivity
//        implements CameraBridgeViewBase.CvCameraViewListener2 {
//
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
//
//    // FIX #2: replaced bare volatile with a lock + plain field to eliminate the race condition.
//    // volatile guarantees visibility but NOT atomicity — the camera thread can be mid-write
//    // into the Mat while the touch thread reads it. A synchronized hand-off is required.
//    private final Object hsvLock = new Object();
//    private Mat currentHSV;
//
//    private Handler uiHandler;
//    private Toast currentToast;
//    private Rect referenceBox;
//
//    // FIX #3: rgbaArea starts at -1 so we can detect "not yet set" and skip bad frames.
//    private double rgbaArea = -1;
//
//    private boolean isTrackingBlack = false;
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
//    // FIX #13: separate timer for "object lost" so it is never suppressed by
//    // the setup-prompt throttle timer right after a fresh color pick.
//    private long lastLostPromptTime = 0;
//
//    // Anti-jitter: command must repeat N consecutive frames before it's sent.
//    private static final int JITTER_FRAMES = 3;
//    private String pendingCommand = "S";
//    private int pendingCount = 0;
//
//    private long lastLostTime = 0;
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
//        // Touch listener registered once in onCreate — NOT in onResume.
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
//
//            // FIX #2: take a thread-safe snapshot of the Mat reference under the lock.
//            Mat hsv;
//            synchronized (hsvLock) {
//                hsv = currentHSV;
//            }
//            if (hsv == null || hsv.empty()) return true;
//
//            if (colorPicker.isColorPicked()) {
//                showToast("Press RESET to pick a new color");
//                return true;
//            }
//
//            if (lightingMode == null) {
//                showToast("Select lighting mode first");
//                lightingControls.setVisibility(View.VISIBLE);
//                return true;
//            }
//
//            int frameW = hsv.cols();
//            int frameH = hsv.rows();
//            int viewW  = cameraView.getWidth();
//            int viewH  = cameraView.getHeight();
//
//            int x = (int) (event.getX() * frameW / viewW);
//            int y = (int) (event.getY() * frameH / viewH);
//
//            if (x < 0 || y < 0 || x >= frameW || y >= frameH) return true;
//
//            if (colorPicker.pickColor(hsv, x, y)) {
//                isTrackingBlack = colorPicker.isBlackColor();
//                lightingControls.setVisibility(View.GONE);
//
//                List<Scalar[]> ranges = colorPicker.getColorRanges();
//                double[] hsvPixel = hsv.get(y, x);
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
//
//                if (isTrackingBlack) {
//                    showToast("Black selected. Tracking started");
//                } else if (colorPicker.isWhiteColor()) {
//                    showToast("White selected. Tracking started");
//                } else {
//                    showToast("Color locked. Tracking started");
//                }
//            }
//            return true;
//        });
//    }
//
//    private void setupReferenceBox(int frameWidth, int frameHeight) {
//        int marginX = (int) (frameWidth  * out_X);
//        int marginY = (int) (frameHeight * out_Y);
//        referenceBox = new Rect(marginX, marginY,
//                frameWidth  - 2 * marginX,
//                frameHeight - 2 * marginY);
//        Log.d(TAG, "Ref box (FRAME): " + referenceBox);
//    }
//
//    @Override
//    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
//        Mat rgba = inputFrame.rgba();
//
//        // FIX #3: guard against bad/empty frames before using rgbaArea anywhere.
//        long total = rgba.total();
//        if (total == 0) {
//            Log.w(TAG, "Empty frame received — skipping");
//            return rgba;
//        }
//        rgbaArea = total;
//
//        // FIX #2: write to currentHSV under the lock so the touch thread never
//        // reads a partially-written Mat.
//        synchronized (hsvLock) {
//            if (currentHSV == null) currentHSV = new Mat();
//
//            Mat rgb = new Mat();
//            try {
//                Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
//                Imgproc.cvtColor(rgb, currentHSV, Imgproc.COLOR_RGB2HSV);
//                Imgproc.GaussianBlur(currentHSV, currentHSV, new Size(5, 5), 0);
//            } finally {
//                rgb.release();
//            }
//        }
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
//        // FIX #2: read currentHSV under the lock for processTracking as well.
//        Mat hsvSnapshot;
//        synchronized (hsvLock) {
//            hsvSnapshot = currentHSV;
//        }
//
//        String raw    = processTracking(rgba, hsvSnapshot);
//        String stable = applyJitterFilter(raw);
//        sendIfChanged(stable);
//        return rgba;
//    }
//
//    /**
//     * FIX #5 (jitter filter): returns "S" (safe stop) while waiting for the threshold
//     * instead of lastCommand. Returning lastCommand caused the robot to keep executing a
//     * stale command (e.g. keep turning) for up to JITTER_FRAMES frames after the object
//     * moved to a new position.
//     */
//    private String applyJitterFilter(String newCmd) {
//        if (newCmd.equals(pendingCommand)) {
//            pendingCount++;
//        } else {
//            pendingCommand = newCmd;
//            pendingCount   = 1;
//        }
//        return (pendingCount >= JITTER_FRAMES) ? newCmd : "S";
//    }
//
//    private String processTracking(Mat rgba, Mat hsv) {
//        List<Scalar[]> allRanges = colorPicker.getColorRanges();
//        Mat mask = new Mat();
//        String command = "S";
//
//        try {
//            // Build combined mask — FIX #10: each tempMask is released in its own finally.
//            for (Scalar[] range : allRanges) {
//                Mat tempMask = new Mat();
//                try {
//                    Core.inRange(hsv, range[0], range[1], tempMask);
//                    if (mask.empty()) tempMask.copyTo(mask);
//                    else Core.bitwise_or(mask, tempMask, mask);
//                } finally {
//                    tempMask.release();
//                }
//            }
//
//            // Morphology: remove noise (open) then fill gaps (dilate).
//            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
//            try {
//                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,   kernel);
//                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_DILATE, kernel);
//            } finally {
//                kernel.release();
//            }
//
//            // FIX #9: wrap displayMat in try/finally so it is released even if an
//            // exception fires mid-way through the display-copy block.
//            Mat displayMat = Mat.zeros(rgba.size(), rgba.type());
//            try {
//                if (isTrackingBlack) {
//                    Mat whiteOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
//                    try {
//                        whiteOverlay.copyTo(displayMat, mask);
//                    } finally {
//                        whiteOverlay.release();
//                    }
//                } else if (colorPicker.isWhiteColor()) {
//                    Mat whiteCanvas = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
//                    Mat blackOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(0, 0, 0, 255));
//                    try {
//                        whiteCanvas.copyTo(displayMat);
//                        blackOverlay.copyTo(displayMat, mask);
//                    } finally {
//                        whiteCanvas.release();
//                        blackOverlay.release();
//                    }
//                } else {
//                    rgba.copyTo(displayMat, mask);
//                }
//                displayMat.copyTo(rgba);
//            } finally {
//                displayMat.release();
//            }
//
//            List<MatOfPoint> contours  = new ArrayList<>();
//            // FIX #8: hierarchy released in finally — it was leaking every frame (30+ fps).
//            Mat hierarchy = new Mat();
//            try {
//                Imgproc.findContours(mask, contours, hierarchy,
//                        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//
//                if (!contours.isEmpty()) {
//                    contours.sort((a, b) ->
//                            Double.compare(Imgproc.contourArea(b), Imgproc.contourArea(a)));
//                    MatOfPoint largest = contours.get(0);
//                    double area = Imgproc.contourArea(largest);
//
//                    if (area > MIN_CONTOUR_AREA) {
//                        Rect  rect      = Imgproc.boundingRect(largest);
//                        Point objCenter = new Point(
//                                rect.x + rect.width  / 2.0,
//                                rect.y + rect.height / 2.0);
//
//                        Imgproc.circle(rgba, objCenter, 6, new Scalar(0, 255, 0), -1);
//
//                        // FIX #14: also treat a clipped bounding rect as "too close".
//                        // If the object is partially off-screen its visible area is
//                        // smaller than reality, so the area threshold fires too late.
//                        boolean clipped = rect.x <= 2 || rect.y <= 2
//                                || (rect.x + rect.width)  >= rgba.cols() - 2
//                                || (rect.y + rect.height) >= rgba.rows() - 2;
//                        boolean tooClose = (area > rgbaArea * TOO_CLOSE_AREA) || clipped;
//                        command = tooClose ? "S" : getPositionCommand(rect, rgba);
//
//                        Imgproc.rectangle(rgba, rect.tl(), rect.br(), new Scalar(0, 255, 0), 3);
//                    }
//                } else {
//                    long now = System.currentTimeMillis();
//                    if (now - lastLostTime > LOST_OBJECT_DELAY) {
//                        lastLostTime = now;
//                        // FIX #13: use the separate lost-prompt timer.
//                        showObjectLostPrompt();
//                    }
//                }
//            } finally {
//                hierarchy.release();
//                for (MatOfPoint c : contours) c.release();
//            }
//
//        } finally {
//            mask.release();
//        }
//
//        return command;
//    }
//
//    private void sendIfChanged(String newCmd) {
//        if (!newCmd.equals(lastCommand)) {
//            lastCommand = newCmd;
//            Log.d(TAG, "CMD: " + newCmd + "*");
//            if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
//                bluetoothConnection.sendData(newCmd + "*");
//            }
//            uiHandler.post(() -> txtFunc.setText(newCmd + "*"));
//        }
//    }
//
//    /**
//     * FIX #4 (Y-axis logic): camera Y increases downward.
//     *  - Object above the box (y < box.y)   → it is far away  → move FORWARD (was wrongly "B")
//     *  - Object below the box               → it is very close → move BACK
//     *
//     * Also accepts rgba so we can use its dimensions for bounds checking.
//     */
//    private String getPositionCommand(Rect objRect, Mat rgba) {
//        Point objCenter = new Point(
//                objRect.x + objRect.width  / 2.0,
//                objRect.y + objRect.height / 2.0);
//
//        if (referenceBox == null) return "S";
//
//        if (referenceBox.contains(objCenter))                          return "F"; // inside box → forward
//        if (objCenter.x < referenceBox.x)                             return "L"; // left of box → turn left
//        if (objCenter.x > referenceBox.x + referenceBox.width)        return "R"; // right → turn right
//        if (objCenter.y < referenceBox.y)                             return "F"; // above box = far → forward (fixed)
//        if (objCenter.y > referenceBox.y + referenceBox.height)       return "B"; // below box = close → back
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
//    // FIX #13: dedicated "object lost" prompt with its own 2-second timer,
//    // completely independent of the setup-prompt throttle.
//    private void showObjectLostPrompt() {
//        long now = System.currentTimeMillis();
//        if (now - lastLostPromptTime > 2000) {
//            lastLostPromptTime = now;
//            showToast("Object lost");
//        }
//    }
//
//    public void onResetClick(View v) {
//        colorPicker.reset();
//
//        lightingMode    = null;
//        isTrackingBlack = false;
//        lastCommand     = "S";
//        lastLostTime    = 0;
//        lastLostPromptTime = 0; // FIX #13: reset so "Object lost" isn't suppressed after re-pick
//
//        // Reset jitter filter
//        pendingCommand = "S";
//        pendingCount   = 0;
//
//        // FIX #1: release the native Mat so it doesn't linger on the native heap.
//        synchronized (hsvLock) {
//            if (currentHSV != null) {
//                currentHSV.release();
//                currentHSV = null;
//            }
//        }
//
//        lightingControls.setVisibility(View.VISIBLE);
//        txtFunc.setText("S*");
//
//        showToast("Reset complete. Select lighting mode");
//    }
//
//    // FIX #15: use getApplicationContext() so the Toast does not hold a reference
//    // to this Activity and cause a context leak across orientation changes.
//    private void showToast(String msg) {
//        uiHandler.post(() -> {
//            if (currentToast != null) currentToast.cancel();
//            currentToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
//            currentToast.show();
//        });
//    }
//
//    // FIX #16: null-guard at the top of reconnectBluetooth so we never NPE if
//    // the singleton was never initialised (e.g. app restored from background).
//    private void reconnectBluetooth() {
//        if (bluetoothConnection == null) {
//            Toast.makeText(getApplicationContext(),
//                    "Bluetooth not initialised", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        Toast.makeText(getApplicationContext(), "Reconnecting...", Toast.LENGTH_SHORT).show();
//        bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
//            @Override
//            public void onReconnectSuccess() {
//                updateBluetoothUI();
//                Toast.makeText(getApplicationContext(), "Reconnected", Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onReconnectFailed(String error) {
//                updateBluetoothUI();
//                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_SHORT).show();
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
//    @Override
//    public void onRequestPermissionsResult(
//            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
//        super.onRequestPermissionsResult(requestCode, permissions, results);
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
//        // FIX #15: cancel and null-out the Toast to release the context reference.
//        if (currentToast != null) {
//            currentToast.cancel();
//            currentToast = null;
//        }
//        // Release the HSV Mat on destroy as well.
//        synchronized (hsvLock) {
//            if (currentHSV != null) {
//                currentHSV.release();
//                currentHSV = null;
//            }
//        }
//    }
//
//    // FIX #4 (camera restart): setupReferenceBox is called here, which fires on every
//    // enableView() — including after onPause/onResume and after rotation — so the box
//    // always reflects the actual current frame dimensions.
//    // Jitter filter is also reset so stale pending state from before the restart is cleared.
//    @Override
//    public void onCameraViewStarted(int width, int height) {
//        Log.d(TAG, "Camera started: " + width + "x" + height);
//        if (width > 0 && height > 0) {
//            setupReferenceBox(width, height);
//        }
//        // Reset jitter — dimensions may have changed (e.g. rotation).
//        pendingCommand = "S";
//        pendingCount   = 0;
//    }
//
//    @Override
//    public void onCameraViewStopped() {
//        Log.d(TAG, "Camera stopped");
//        synchronized (hsvLock) {
//            if (currentHSV != null) {
//                currentHSV.release();
//                currentHSV = null;
//            }
//        }
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (cameraView != null
//                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                == PackageManager.PERMISSION_GRANTED) {
//            startCamera();
//        }
//        updateBluetoothUI();
//    }
//}
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
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "ColorFollowing";
    private static final int CAMERA_PERMISSION_CODE = 10;

    private JavaCameraView cameraView;
    private String lastCommand = "S";
    private BluetoothConnectionManager bluetoothConnection;
    private ColorPicker colorPicker;
    private LinearLayout lightingControls;
    private TextView txtStatus, txtFunc;
    private ColorPicker.LightingMode lightingMode = null;

    // hsvLock guards currentHSV. Always clone() inside the lock before using
    // the Mat outside it — releasing currentHSV on another thread while a caller
    // holds a bare reference to it crashes the native heap.
    private final Object hsvLock = new Object();
    private Mat currentHSV;

    private Handler uiHandler;
    private Toast currentToast;
    private Rect referenceBox;
    private double rgbaArea = -1; // -1 = "not yet set"; guards against bad early frames
    private boolean isTrackingBlack = false;

    private static final int MIN_CONTOUR_AREA = 800;
    private static final long LOST_OBJECT_DELAY = 2000;

    private static final float out_X = 0.35f;
    private static final float out_Y = 0.15f;
    private static final float TOO_CLOSE_AREA = 0.4f;

    private long lastPromptTime = 0;
    private static final long PROMPT_INTERVAL = 5000;

    // Separate timer for "object lost" so it is never suppressed by the
    // setup-prompt throttle timer right after a fresh color pick.
    private long lastLostPromptTime = 0;

    // Anti-jitter: command must repeat JITTER_FRAMES consecutive frames before sending.
    private static final int JITTER_FRAMES = 3;
    private String pendingCommand = "S";
    private int pendingCount = 0;

    private long lastLostTime = 0;

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

        // Touch listener registered once in onCreate — NOT in onResume.
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

            // Clone the Mat under the lock so we own a private copy that cannot
            // be released by the camera or reset threads while we use it.
            Mat hsv;
            synchronized (hsvLock) {
                if (currentHSV == null || currentHSV.empty()) return true;
                hsv = currentHSV.clone(); // <-- KEY FIX: own copy, safe outside the lock
            }

            try {
                if (colorPicker.isColorPicked()) {
                    showToast("Press RESET to pick a new color");
                    return true;
                }

                if (lightingMode == null) {
                    showToast("Select lighting mode first");
                    lightingControls.setVisibility(View.VISIBLE);
                    return true;
                }

                int frameW = hsv.cols();
                int frameH = hsv.rows();
                int viewW  = cameraView.getWidth();
                int viewH  = cameraView.getHeight();

                int x = (int) (event.getX() * frameW / viewW);
                int y = (int) (event.getY() * frameH / viewH);

                if (x < 0 || y < 0 || x >= frameW || y >= frameH) return true;

                if (colorPicker.pickColor(hsv, x, y)) {
                    isTrackingBlack = colorPicker.isBlackColor();
                    lightingControls.setVisibility(View.GONE);

                    List<Scalar[]> ranges = colorPicker.getColorRanges();
                    double[] hsvPixel = hsv.get(y, x);
                    Log.d(TAG, "===== COLOR PICKED =====");
                    Log.d(TAG, String.format("Touched pixel HSV → H:%.1f  S:%.1f  V:%.1f",
                            hsvPixel[0], hsvPixel[1], hsvPixel[2]));
                    Log.d(TAG, "isTrackingBlack: " + isTrackingBlack);
                    Log.d(TAG, "lightingMode: " + lightingMode);
                    Log.d(TAG, "Total HSV ranges generated: " + ranges.size());
                    for (int i = 0; i < ranges.size(); i++) {
                        Log.d(TAG, String.format("  Range[%d] → Lower: %s  |  Upper: %s",
                                i, ranges.get(i)[0], ranges.get(i)[1]));
                    }
                    Log.d(TAG, "========================");

                    if (isTrackingBlack) {
                        showToast("Black selected. Tracking started");
                    } else if (colorPicker.isWhiteColor()) {
                        showToast("White selected. Tracking started");
                    } else {
                        showToast("Color locked. Tracking started");
                    }
                }
            } finally {
                hsv.release(); // always release our cloned copy
            }
            return true;
        });
    }

    private void setupReferenceBox(int frameWidth, int frameHeight) {
        int marginX = (int) (frameWidth  * out_X);
        int marginY = (int) (frameHeight * out_Y);
        referenceBox = new Rect(marginX, marginY,
                frameWidth  - 2 * marginX,
                frameHeight - 2 * marginY);
        Log.d(TAG, "Ref box (FRAME): " + referenceBox);
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();

        long total = rgba.total();
        if (total == 0) {
            Log.w(TAG, "Empty frame received — skipping");
            return rgba;
        }
        rgbaArea = total;

        // Write currentHSV under the lock. clone() in callers means this release
        // is safe even if a touch event is mid-processing with its own copy.
        synchronized (hsvLock) {
            if (currentHSV == null) currentHSV = new Mat();

            Mat rgb = new Mat();
            try {
                Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
                Imgproc.cvtColor(rgb, currentHSV, Imgproc.COLOR_RGB2HSV);
                Imgproc.GaussianBlur(currentHSV, currentHSV, new Size(5, 5), 0);
            } finally {
                rgb.release();
            }
        }

        if (lightingMode == null) {
            showPromptOnce("Click RESET COLOR.");
            return rgba;
        }

        if (!colorPicker.isColorPicked()) {
            showPromptOnce("Touch any color on screen");
            return rgba;
        }

        // Clone under lock — processTracking runs outside the lock and may take
        // several milliseconds; we must not hold hsvLock that long.
        Mat hsvSnapshot;
        synchronized (hsvLock) {
            if (currentHSV == null || currentHSV.empty()) return rgba;
            hsvSnapshot = currentHSV.clone(); // <-- KEY FIX
        }

        try {
            String raw    = processTracking(rgba, hsvSnapshot);
            String stable = applyJitterFilter(raw);
            sendIfChanged(stable);
        } finally {
            hsvSnapshot.release(); // always release our cloned copy
        }

        return rgba;
    }

    /**
     * Returns "S" while waiting for the jitter threshold so the robot doesn't
     * keep executing a stale command between command transitions.
     */
    private String applyJitterFilter(String newCmd) {
        if (newCmd.equals(pendingCommand)) {
            pendingCount++;
        } else {
            pendingCommand = newCmd;
            pendingCount   = 1;
        }
        return (pendingCount >= JITTER_FRAMES) ? newCmd : "S";
    }

    private String processTracking(Mat rgba, Mat hsv) {
        List<Scalar[]> allRanges = colorPicker.getColorRanges();
        Mat mask = new Mat();
        String command = "S";

        try {
            for (Scalar[] range : allRanges) {
                Mat tempMask = new Mat();
                try {
                    Core.inRange(hsv, range[0], range[1], tempMask);
                    if (mask.empty()) tempMask.copyTo(mask);
                    else Core.bitwise_or(mask, tempMask, mask);
                } finally {
                    tempMask.release();
                }
            }

            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
            try {
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,   kernel);
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_DILATE, kernel);
            } finally {
                kernel.release();
            }

            Mat displayMat = Mat.zeros(rgba.size(), rgba.type());
            try {
                if (isTrackingBlack) {
                    Mat whiteOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
                    try {
                        whiteOverlay.copyTo(displayMat, mask);
                    } finally {
                        whiteOverlay.release();
                    }
                } else if (colorPicker.isWhiteColor()) {
                    Mat whiteCanvas  = new Mat(rgba.size(), rgba.type(), new Scalar(255, 255, 255, 255));
                    Mat blackOverlay = new Mat(rgba.size(), rgba.type(), new Scalar(0, 0, 0, 255));
                    try {
                        whiteCanvas.copyTo(displayMat);
                        blackOverlay.copyTo(displayMat, mask);
                    } finally {
                        whiteCanvas.release();
                        blackOverlay.release();
                    }
                } else {
                    rgba.copyTo(displayMat, mask);
                }
                displayMat.copyTo(rgba);
            } finally {
                displayMat.release();
            }

            List<MatOfPoint> contours  = new ArrayList<>();
            Mat              hierarchy = new Mat();
            try {
                Imgproc.findContours(mask, contours, hierarchy,
                        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

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

                        // Treat clipped bounding rect as "too close" — partially
                        // off-screen objects report smaller area than reality.
                        boolean clipped = rect.x <= 2 || rect.y <= 2
                                || (rect.x + rect.width)  >= rgba.cols() - 2
                                || (rect.y + rect.height) >= rgba.rows() - 2;
                        boolean tooClose = (rgbaArea > 0 && area > rgbaArea * TOO_CLOSE_AREA) || clipped;
                        command = tooClose ? "S" : getPositionCommand(rect);

                        Imgproc.rectangle(rgba, rect.tl(), rect.br(), new Scalar(0, 255, 0), 3);
                    }
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastLostTime > LOST_OBJECT_DELAY) {
                        lastLostTime = now;
                        showObjectLostPrompt();
                    }
                }
            } finally {
                hierarchy.release();
                for (MatOfPoint c : contours) c.release();
            }

        } finally {
            mask.release();
        }

        return command;
    }

    private void sendIfChanged(String newCmd) {
        if (!newCmd.equals(lastCommand)) {
            lastCommand = newCmd;
            Log.d(TAG, "CMD: " + newCmd + "*");
            if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
                bluetoothConnection.sendData(newCmd + "*");
            }
            uiHandler.post(() -> txtFunc.setText(newCmd + "*"));
        }
    }

    /**
     * Camera Y increases downward:
     *   object above box (y < box.y)  → far away  → FORWARD
     *   object below box              → too close  → BACK
     */
    private String getPositionCommand(Rect objRect) {
        Point objCenter = new Point(
                objRect.x + objRect.width  / 2.0,
                objRect.y + objRect.height / 2.0);

        if (referenceBox == null) return "S";

        if (referenceBox.contains(objCenter))                          return "F";
        if (objCenter.x < referenceBox.x)                             return "L";
        if (objCenter.x > referenceBox.x + referenceBox.width)        return "R";
        if (objCenter.y < referenceBox.y)                             return "F"; // far away
        if (objCenter.y > referenceBox.y + referenceBox.height)       return "B"; // too close

        return "S";
    }

    private void showPromptOnce(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastPromptTime > PROMPT_INTERVAL) {
            lastPromptTime = now;
            showToast(msg);
        }
    }

    // Dedicated "object lost" prompt with its own timer, independent of the
    // setup-prompt throttle so it is never accidentally suppressed.
    private void showObjectLostPrompt() {
        long now = System.currentTimeMillis();
        if (now - lastLostPromptTime > 2000) {
            lastLostPromptTime = now;
            showToast("Object lost");
        }
    }

    public void onResetClick(View v) {
        colorPicker.reset();

        lightingMode       = null;
        isTrackingBlack    = false;
        lastCommand        = "S";
        lastLostTime       = 0;
        lastLostPromptTime = 0;

        pendingCommand = "S";
        pendingCount   = 0;

        synchronized (hsvLock) {
            if (currentHSV != null) {
                currentHSV.release();
                currentHSV = null;
            }
        }

        lightingControls.setVisibility(View.VISIBLE);
        txtFunc.setText("S*");
        showToast("Reset complete. Select lighting mode");
    }

    // Use getApplicationContext() to avoid leaking the Activity reference across
    // orientation changes.
    private void showToast(String msg) {
        uiHandler.post(() -> {
            if (currentToast != null) currentToast.cancel();
            currentToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
            currentToast.show();
        });
    }

    private void reconnectBluetooth() {
        if (bluetoothConnection == null) {
            Toast.makeText(getApplicationContext(),
                    "Bluetooth not initialised", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getApplicationContext(), "Reconnecting...", Toast.LENGTH_SHORT).show();
        bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
            @Override
            public void onReconnectSuccess() {
                updateBluetoothUI();
                Toast.makeText(getApplicationContext(), "Reconnected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onReconnectFailed(String error) {
                updateBluetoothUI();
                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_SHORT).show();
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

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
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
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
        synchronized (hsvLock) {
            if (currentHSV != null) {
                currentHSV.release();
                currentHSV = null;
            }
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "Camera started: " + width + "x" + height);
        if (width > 0 && height > 0) {
            setupReferenceBox(width, height);
        }
        // Reset jitter — dimensions may have changed (e.g. rotation).
        pendingCommand = "S";
        pendingCount   = 0;
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "Camera stopped");
        synchronized (hsvLock) {
            if (currentHSV != null) {
                currentHSV.release();
                currentHSV = null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraView != null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        updateBluetoothUI();
    }
}