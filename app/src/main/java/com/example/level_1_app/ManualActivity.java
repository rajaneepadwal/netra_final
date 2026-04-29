//package com.example.level_1_app;
//import android.content.Intent;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.Window;
//import android.view.WindowManager;
//import android.widget.Button;
//import android.widget.TextView;
//import android.widget.Toast;
//import androidx.appcompat.app.AppCompatActivity;
//import com.example.joystick_lib.Joystick_Lib;
//import com.example.level_1_app.BluetoothConnectionManager;
//import com.example.level_1_app.BluetoothActivity;
//import android.hardware.Sensor;
//import android.hardware.SensorEvent;
//import android.hardware.SensorEventListener;
//import android.hardware.SensorManager;
//import android.widget.ToggleButton;
//
//public class ManualActivity extends AppCompatActivity {
//
//    private Joystick_Lib joystickLib;
//    private BluetoothConnectionManager bluetoothConnection;
//    private TextView txtFunc, tvStatus;
//
//    // Toast management
//    private Toast currentToast = null;
//    private long lastToastTime = 0;
//    private static final long TOAST_INTERVAL = 3000;
//    private String lastToastMessage = "";
//
//    // Sensor
//    private SensorManager sensorManager;
//    private Sensor accelerometer;
//
//    // Tilt state
//    private boolean isTiltEnabled = false;
//    private String lastCommand = "S*";
//
//    // Low-pass filter gravity buffer
//    private float[] gravity = {0f, 0f, 0f};
//
//    // Neutral calibration (captured when tilt is turned ON)
//    private float[] neutralGravity = {0f, 0f, 0f};
//    private boolean isCalibrated = false;
//
//    // Tuning constants — increase TILT_THRESHOLD if too sensitive, decrease if sluggish
//    private static final float ALPHA = 0.8f;
//    private static final float TILT_THRESHOLD = 2.5f;
//    private static final float DEAD_ZONE = 1.5f;
//    private static final float LOW_PASS_ALPHA = 0.8f;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//
//        Window window = getWindow();
//        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
//
//        setContentView(R.layout.activity_manual);
//
//        txtFunc = findViewById(R.id.txtFunc);
//        tvStatus = findViewById(R.id.txtStatus);
//
//        Button btnGrip      = findViewById(R.id.btnGrip);
//        Button btnUngrip    = findViewById(R.id.btnUngrip);
//        Button btnTriangle  = findViewById(R.id.btnTriangle);
//        Button btnSquare    = findViewById(R.id.btnSquare);
//        Button btnCircle    = findViewById(R.id.btnCircle);
//        Button btnCross     = findViewById(R.id.btnCross);
//        Button btnBack      = findViewById(R.id.btnBack);
//        Button btnReconnect = findViewById(R.id.btnReconnect);
//
//        joystickLib = findViewById(R.id.joystick_Lib);
//
//        ToggleButton toggleTilt = findViewById(R.id.toggleTilt);
//
//        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
//        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//
//        bluetoothConnection = BluetoothConnectionManager.getInstance();
//        updateConnectionStatus();
//
//        joystickLib.setOnMoveListener(new Joystick_Lib.OnMoveListener() {
//            @Override
//            public void onMove(int angle, int strength) {
//                if (strength == 0) {
//                    txtFunc.setText("S*");
//                } else if (angle >= 45 && angle <= 135) {
//                    txtFunc.setText("F*");
//                } else if (angle >= 225 && angle <= 315) {
//                    txtFunc.setText("B*");
//                } else if (angle > 135 && angle < 225) {
//                    txtFunc.setText("L*");
//                } else {
//                    txtFunc.setText("R*");
//                }
//                sendData(txtFunc.getText().toString());
//            }
//        });
//
//        // ── Action buttons ────────────────────────────────────────────────────
//        btnGrip.setOnClickListener(v     -> { txtFunc.setText("G*"); sendData("G*"); });
//        btnUngrip.setOnClickListener(v   -> { txtFunc.setText("U*"); sendData("U*"); });
//        btnTriangle.setOnClickListener(v -> { txtFunc.setText("W*"); sendData("W*"); });
//        btnSquare.setOnClickListener(v   -> { txtFunc.setText("A*"); sendData("A*"); });
//        btnCircle.setOnClickListener(v   -> { txtFunc.setText("D*"); sendData("D*"); });
//        btnCross.setOnClickListener(v    -> { txtFunc.setText("X*"); sendData("X*"); });
//
//        btnBack.setOnClickListener(v -> {
//            Intent intent = new Intent(ManualActivity.this, MenuActivity.class);
//            startActivity(intent);
//            finish();
//        });
//
//        // ── Tilt toggle ───────────────────────────────────────────────────────
//        toggleTilt.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            isTiltEnabled = isChecked;
//
//            if (isChecked) {
//                // Disable joystick while tilt is active
//                joystickLib.setEnabled(false);
//
//                // Send stop and reset state
//                txtFunc.setText("S*");
//                sendData("S*");
//                lastCommand = "S*";
//
//                // Reset calibration so it re-captures neutral from current hold position
//                gravity[0] = 0f; gravity[1] = 0f; gravity[2] = 0f;
//                neutralGravity[0] = 0f; neutralGravity[1] = 0f; neutralGravity[2] = 0f;
//                isCalibrated = false;
//
//                // SENSOR_DELAY_GAME gives ~50 Hz — much better for live control
//                sensorManager.registerListener(
//                        sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
//
//                showThrottledToast("Hold phone flat — calibrating...", false);
//
//            } else {
//                joystickLib.setEnabled(true);
//                sensorManager.unregisterListener(sensorListener);
//
//                // Stop robot when tilt is turned off
//                txtFunc.setText("S*");
//                sendData("S*");
//                lastCommand = "S*";
//
//                showThrottledToast("Tilt Control OFF", false);
//            }
//        });
//
//        btnReconnect.setOnClickListener(v -> {
//            showThrottledToast("Reconnecting...", false);
//            bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
//                @Override
//                public void onReconnectSuccess() {
//                    updateConnectionStatus();
//                    showThrottledToast("Reconnected successfully", false);
//                }
//                @Override
//                public void onReconnectFailed(String errorMessage) {
//                    updateConnectionStatus();
//                    showThrottledToast(errorMessage, false);
//                }
//            });
//        });
//    }
//    private final SensorEventListener sensorListener = new SensorEventListener() {
//        @Override
//        public void onSensorChanged(SensorEvent event) {
//            if (!isTiltEnabled) return;
//
//            // Low-pass filter — isolates gravity, smooths out vibration/noise
//            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
//            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
//            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];
//
//            if (!isCalibrated) {
//                neutralGravity[0] = gravity[0];
//                neutralGravity[1] = gravity[1];
//                neutralGravity[2] = gravity[2];
//                if (Math.abs(gravity[2]) > 5.0f) {
//                    isCalibrated = true;
//                    runOnUiThread(() -> showThrottledToast("Tilt Control ON — go!", false));
//                }
//                return;
//            }
//
//            // Deviation from the neutral "flat" position captured at calibration
//            float dx = gravity[0] - neutralGravity[0]; // left (+) / right (-)
//            float dy = gravity[1] - neutralGravity[1]; // back (+) / forward (-)
//
//            String command;
//
//            if (Math.abs(dx) < DEAD_ZONE && Math.abs(dy) < DEAD_ZONE) {
//                // Phone is flat — stop (Temple Run: running straight means no tilt)
//                command = "S*";
//
//            } else if (Math.abs(dy) >= Math.abs(dx)) {
//                // Forward / backward tilt dominates
//                // Tilt top away from you → dy goes negative → Forward
//                // Tilt top toward you  → dy goes positive → Backward
//                if (dy < -TILT_THRESHOLD) {
//                    command = "F*";
//                } else if (dy > TILT_THRESHOLD) {
//                    command = "B*";
//                } else {
//                    command = "S*";
//                }
//
//            } else {
//                // Left / right tilt dominates
//                // Tilt right side down → dx goes positive → Right
//                // Tilt left  side down → dx goes negative → Left
//                if (dx > TILT_THRESHOLD) {
//                    command = "R*";
//                } else if (dx < -TILT_THRESHOLD) {
//                    command = "L*";
//                } else {
//                    command = "S*";
//                }
//            }
//
//            // Only send when command changes — avoids flooding Bluetooth
//            if (!command.equals(lastCommand)) {
//                final String cmd = command;
//                runOnUiThread(() -> {
//                    txtFunc.setText(cmd);
//                    sendData(cmd);
//                });
//                lastCommand = cmd;
//            }
//        }
//
//        @Override
//        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
//    };
//
//    private void updateConnectionStatus() {
//        if (bluetoothConnection.isConnected()) {
//            tvStatus.setText("CONNECTED");
//            tvStatus.setTextColor(Color.GREEN);
//        } else {
//            tvStatus.setText("DISCONNECTED");
//            tvStatus.setTextColor(Color.RED);
//        }
//    }
//
//    private void showThrottledToast(String message, boolean throttle) {
//        long currentTime = System.currentTimeMillis();
//        if (throttle && message.equals(lastToastMessage)
//                && (currentTime - lastToastTime) < TOAST_INTERVAL) {
//            return;
//        }
//        if (currentToast != null) currentToast.cancel();
//        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
//        currentToast.show();
//        lastToastTime = currentTime;
//        lastToastMessage = message;
//    }
//
//    private void sendData(String data) {
//        if (bluetoothConnection.isConnected()) {
//            bluetoothConnection.sendData(data);
//        } else {
//            showThrottledToast("Not Connected!", true);
//            updateConnectionStatus();
//        }
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        sensorManager.unregisterListener(sensorListener);
//        if (currentToast != null) currentToast.cancel();
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        // Re-register sensor only if tilt is still toggled on
//        if (isTiltEnabled) {
//            gravity[0] = 0f; gravity[1] = 0f; gravity[2] = 0f;
//            isCalibrated = false;
//            sensorManager.registerListener(
//                    sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
//            bluetoothConnection.sendData("S*");
//        }
//        if (currentToast != null) currentToast.cancel();
//    }
//}
//
package com.example.level_1_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.joystick_lib.Joystick_Lib;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.ToggleButton;

public class ManualActivity extends AppCompatActivity {

    private Joystick_Lib joystickLib;
    private BluetoothConnectionManager bluetoothConnection;
    private TextView txtFunc, tvStatus;

    // Toast management
    private Toast currentToast = null;
    private long lastToastTime = 0;
    private static final long TOAST_INTERVAL = 3000;
    private String lastToastMessage = "";

    // Sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // Tilt state
    private boolean isTiltEnabled = false;
    private String lastCommand   = "F*";
    private String stableCommand = "F*";

    // Low-pass filter gravity buffer
    private float[] gravity = {0f, 0f, 0f};

    // Neutral calibration
    private float[] neutralGravity  = {0f, 0f, 0f};
    private boolean isCalibrated    = false;
    private int warmupFrames = 0;
    private static final int WARMUP_LIMIT = 10;
    // Calibration averaging
    private static final int CALIB_SAMPLES = 15;
    private int    calibSampleCount        = 0;
    private float[] calibAccum             = {0f, 0f, 0f};

    private static final float ALPHA          = 0.85f;
    private static final float TILT_THRESHOLD = 2.5f;
    private static final float TILT_RELEASE   = 1.4f;

    // BT rate limit — skip sends faster than this (ms)
    private static final long BT_SEND_INTERVAL = 90L;
    private long lastSentTime = 0L;

    // Joystick dedup
    private String lastJoystickCommand = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        setContentView(R.layout.activity_manual);

        txtFunc  = findViewById(R.id.txtFunc);
        tvStatus = findViewById(R.id.txtStatus);

        Button btnGrip      = findViewById(R.id.btnGrip);
        Button btnUngrip    = findViewById(R.id.btnUngrip);
        Button btnTriangle  = findViewById(R.id.btnTriangle);
        Button btnSquare    = findViewById(R.id.btnSquare);
        Button btnCircle    = findViewById(R.id.btnCircle);
        Button btnCross     = findViewById(R.id.btnCross);
        Button btnBack      = findViewById(R.id.btnBack);
        Button btnReconnect = findViewById(R.id.btnReconnect);

        joystickLib = findViewById(R.id.joystick_Lib);
        ToggleButton toggleTilt = findViewById(R.id.toggleTilt);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        bluetoothConnection = BluetoothConnectionManager.getInstance();
        updateConnectionStatus();

        // ── Joystick ──────────────────────────────────────────────────────────
        joystickLib.setOnMoveListener((angle, strength) -> {
            String cmd;
            if (strength == 0)                          cmd = "S*";
            else if (angle >= 45  && angle <= 135)      cmd = "F*";
            else if (angle >= 225 && angle <= 315)      cmd = "B*";
            else if (angle > 135  && angle < 225)       cmd = "L*";
            else                                        cmd = "R*";

            if (!cmd.equals(lastJoystickCommand)) {
                lastJoystickCommand = cmd;
                txtFunc.setText(cmd);
                sendData(cmd);
            }
        });

        // ── Action buttons ────────────────────────────────────────────────────
        btnGrip.setOnClickListener(v     -> { txtFunc.setText("G*"); sendData("G*"); });
        btnUngrip.setOnClickListener(v   -> { txtFunc.setText("U*"); sendData("U*"); });
        btnTriangle.setOnClickListener(v -> { txtFunc.setText("W*"); sendData("W*"); });
        btnSquare.setOnClickListener(v   -> { txtFunc.setText("A*"); sendData("A*"); });
        btnCircle.setOnClickListener(v   -> { txtFunc.setText("D*"); sendData("D*"); });
        btnCross.setOnClickListener(v    -> { txtFunc.setText("X*"); sendData("X*"); });

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(ManualActivity.this, MenuActivity.class));
            finish();
        });

        // ── Tilt toggle ───────────────────────────────────────────────────────
        toggleTilt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isTiltEnabled = isChecked;
            if (isChecked) {
                joystickLib.setEnabled(false);
                resetTiltState();
                sensorManager.registerListener(
                        sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
                showThrottledToast("Hold phone in gaming position — calibrating...", false);
            } else {
                joystickLib.setEnabled(true);
                sensorManager.unregisterListener(sensorListener);
                resetTiltState();
                txtFunc.setText("S*");
                sendData("S*");
                showThrottledToast("Tilt Control OFF", false);
            }
        });

        // ── Reconnect ─────────────────────────────────────────────────────────
        btnReconnect.setOnClickListener(v -> {
            showThrottledToast("Reconnecting...", false);
            bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
                @Override public void onReconnectSuccess() {
                    updateConnectionStatus();
                    showThrottledToast("Reconnected successfully", false);
                }
                @Override public void onReconnectFailed(String errorMessage) {
                    updateConnectionStatus();
                    showThrottledToast(errorMessage, false);
                }
            });
        });
    }

    // ── Tilt sensor listener ──────────────────────────────────────────────────
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!isTiltEnabled) return;

            // Low-pass filter
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            if (!isCalibrated) {

                // Let sensor stabilize first
                if (warmupFrames < WARMUP_LIMIT) {
                    warmupFrames++;
                    return;
                }

                calibAccum[0] += gravity[0];
                calibAccum[1] += gravity[1];
                calibAccum[2] += gravity[2];
                calibSampleCount++;

                if (calibSampleCount >= CALIB_SAMPLES) {
                    neutralGravity[0] = calibAccum[0] / CALIB_SAMPLES;
                    neutralGravity[1] = calibAccum[1] / CALIB_SAMPLES;
                    neutralGravity[2] = calibAccum[2] / CALIB_SAMPLES;
                    isCalibrated = true;

                    runOnUiThread(() ->
                            showThrottledToast("Tilt Control ON — go!", false));
                }
                return;
            }


            float dy = gravity[1] - neutralGravity[1]; // forward / backward
            float dz = gravity[2] - neutralGravity[2]; // left / right

            if (Math.abs(dy) < 0.2f) dy = 0;
            if (Math.abs(dz) < 0.2f) dz = 0;

            float absDy = Math.abs(dy);
            float absDz = Math.abs(dz);

            boolean isMoving  = !stableCommand.equals("S*");
            float enterThresh = TILT_THRESHOLD;
            String command;

            if (absDy < TILT_RELEASE && absDz < TILT_RELEASE) {
                command = "F*";
            }
            else if (absDy > absDz + 0.3f){
                // Forward / backward dominates
                if      (dy > enterThresh)  command = "R*";
                else if (dy < -enterThresh) command = "L*";
                else if (isMoving) {
                    command = stableCommand;
                }
                else                        command = "F*";

            } else {
                // Left / right dominates
                if      (dz > enterThresh)  command = "B*";
                else if (dz < -enterThresh) command = "S*";
                else if (isMoving) {
                    command = stableCommand;
                }
                else                        command = "F*";
            }

            if (!command.equals(lastCommand)) {

                stableCommand = command;

                lastCommand = command;
                final String cmd = command;
                runOnUiThread(() -> {
                    txtFunc.setText(cmd);
                    sendData(cmd);
                });
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetTiltState() {
        warmupFrames = 0;
        gravity[0] = 0f;        gravity[1] = 0f;        gravity[2] = 0f;
        neutralGravity[0] = 0f; neutralGravity[1] = 0f; neutralGravity[2] = 0f;
        calibAccum[0] = 0f;     calibAccum[1] = 0f;     calibAccum[2] = 0f;
        calibSampleCount    = 0;
        isCalibrated        = false;
        lastCommand         = "S*";
        stableCommand       = "S*";
        lastJoystickCommand = "";
    }

    private void updateConnectionStatus() {
        if (bluetoothConnection.isConnected()) {
            tvStatus.setText("CONNECTED");
            tvStatus.setTextColor(Color.GREEN);
        } else {
            tvStatus.setText("DISCONNECTED");
            tvStatus.setTextColor(Color.RED);
        }
    }

    private void showThrottledToast(String message, boolean throttle) {
        long now = System.currentTimeMillis();
        if (throttle && message.equals(lastToastMessage)
                && (now - lastToastTime) < TOAST_INTERVAL) return;
        if (currentToast != null) currentToast.cancel();
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
        lastToastTime = now;
        lastToastMessage = message;
    }

    private void sendData(String data) {
        long now = System.currentTimeMillis();
        if (data.equals(lastCommand) && (now - lastSentTime < BT_SEND_INTERVAL)) return;
        lastSentTime = now;

        if (bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData(data);
        } else {
            showThrottledToast("Not Connected!", true);
            updateConnectionStatus();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorListener);
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData("S*");
        }
        if (currentToast != null) currentToast.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStatus();
        if (isTiltEnabled) {
            resetTiltState();
            joystickLib.setEnabled(false);
            sensorManager.registerListener(
                    sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            showThrottledToast("Hold phone in gaming position — recalibrating...", false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentToast != null) currentToast.cancel();
    }
}
