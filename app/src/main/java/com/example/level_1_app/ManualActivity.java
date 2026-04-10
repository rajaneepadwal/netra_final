package com.example.level_1_app;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;
import com.example.joystick_lib.Joystick_Lib;

public class ManualActivity extends AppCompatActivity {
    private Joystick_Lib joystickLib;
    private BluetoothConnectionManager bluetoothConnection;
    private TextView txtFunc, tvStatus;

    private Toast currentToast = null;
    private long lastToastTime = 0;
    private static final long TOAST_INTERVAL = 3000;
    private String lastToastMessage = "";

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] adjustedRotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private boolean isTiltEnabled = false;
    private String lastMovementCommand = "S*";

    // ✅ NEW (for smoothing)
    private float smoothedPitch = 0;
    private float smoothedRoll = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(R.layout.activity_manual);

        txtFunc = findViewById(R.id.txtFunc);
        tvStatus = findViewById(R.id.txtStatus);
        joystickLib = findViewById(R.id.joystick_Lib);
        ToggleButton toggleTilt = findViewById(R.id.toggleTilt);

        Button btnGrip = findViewById(R.id.btnGrip);
        Button btnUngrip = findViewById(R.id.btnUngrip);
        Button btnTriangle = findViewById(R.id.btnTriangle);
        Button btnSquare = findViewById(R.id.btnSquare);
        Button btnCircle = findViewById(R.id.btnCircle);
        Button btnCross = findViewById(R.id.btnCross);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnReconnect = findViewById(R.id.btnReconnect);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        bluetoothConnection = BluetoothConnectionManager.getInstance();

        updateConnectionStatus();

        joystickLib.setOnMoveListener((angle, strength) -> {
            if (isTiltEnabled) return;

            String command = "S*";
            if (strength > 0) {
                if (angle >= 45 && angle <= 135) command = "F*";
                else if (angle >= 225 && angle <= 315) command = "B*";
                else if (angle > 135 && angle < 225) command = "L*";
                else command = "R*";
            }

            if (!command.equals(lastMovementCommand)) {
                executeCommand(command);
                lastMovementCommand = command;
            }
        });

        btnGrip.setOnClickListener(v -> executeCommand("G*"));
        btnUngrip.setOnClickListener(v -> executeCommand("U*"));
        btnTriangle.setOnClickListener(v -> executeCommand("W*"));
        btnSquare.setOnClickListener(v -> executeCommand("A*"));
        btnCircle.setOnClickListener(v -> executeCommand("D*"));
        btnCross.setOnClickListener(v -> executeCommand("X*"));

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(ManualActivity.this, MenuActivity.class));
            finish();
        });

        toggleTilt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isTiltEnabled = isChecked;
            if (isChecked) {
                joystickLib.setEnabled(false);
                joystickLib.setAlpha(0.3f);
                executeCommand("S*");
                lastMovementCommand = "S*";
                sensorManager.registerListener(sensorListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
                showThrottledToast("LANDSCAPE GYRO ACTIVE", false);
            } else {
                joystickLib.setEnabled(true);
                joystickLib.setAlpha(1.0f);
                sensorManager.unregisterListener(sensorListener);
                executeCommand("S*");
                lastMovementCommand = "S*";
                showThrottledToast("JOYSTICK ACTIVE", false);
            }
        });

        btnReconnect.setOnClickListener(v -> {
            showThrottledToast("Syncing...", false);
            bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
                @Override public void onReconnectSuccess() { updateConnectionStatus(); }
                @Override public void onReconnectFailed(String err) { updateConnectionStatus(); }
            });
        });
    }

    // ✅ FULLY FIXED SENSOR LOGIC
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!isTiltEnabled) return;

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    adjustedRotationMatrix
            );

            SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles);

            float pitch = (float) Math.toDegrees(orientationAngles[1]);

            float[] gravity = new float[3];
            gravity[0] = adjustedRotationMatrix[6];
            gravity[1] = adjustedRotationMatrix[7];
            gravity[2] = adjustedRotationMatrix[8];

            float rollStable = (float) Math.toDegrees(Math.atan2(gravity[0], gravity[2]));

            pitch = -pitch;
            // rollStable = -rollStable; // uncomment if needed

            // smoothing
            float alpha = 0.85f;
            smoothedPitch = alpha * smoothedPitch + (1 - alpha) * pitch;
            smoothedRoll = alpha * smoothedRoll + (1 - alpha) * rollStable;

            float DEAD_ZONE = 10;

            float finalPitch = Math.abs(smoothedPitch) < DEAD_ZONE ? 0 : smoothedPitch;
            float finalRoll = Math.abs(smoothedRoll) < DEAD_ZONE ? 0 : smoothedRoll;

            String command = "S*";

            if (Math.abs(finalPitch) > Math.abs(finalRoll)) {
                if (finalPitch > 45) command = "F*";
                else if (finalPitch < -10) command = "B*";
            } else {
                if (finalRoll > 15) command = "L*";
                else if (finalRoll < -15) command = "R*";
            }

            if (!command.equals(lastMovementCommand)) {
                executeCommand(command);
                lastMovementCommand = command;
            }
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void executeCommand(String command) {
        txtFunc.setText(command);
        sendData(command);
    }

    private void updateConnectionStatus() {
        if (bluetoothConnection.isConnected()) {
            tvStatus.setText("Connected");
            tvStatus.setTextColor(Color.parseColor("#39FF14"));
            txtFunc.setShadowLayer(20, 0, 0, Color.parseColor("#00E5FF"));
        } else {
            tvStatus.setText("Disconnected");
            tvStatus.setTextColor(Color.parseColor("#FF003C"));
            txtFunc.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
    }

    private void sendData(String data) {
        if (bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData(data);
            triggerHapticFeedback();
        } else {
            showThrottledToast("Connection LOST!", true);
            updateConnectionStatus();
        }
    }

    private void triggerHapticFeedback() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(20);
            }
        }
    }

    private void showThrottledToast(String message, boolean throttle) {
        long currentTime = System.currentTimeMillis();
        if (throttle && message.equals(lastToastMessage) && (currentTime - lastToastTime) < TOAST_INTERVAL) return;
        if (currentToast != null) currentToast.cancel();
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
        lastToastTime = currentTime;
        lastToastMessage = message;
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorListener);
        executeCommand("S*");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executeCommand("S*");
    }
}