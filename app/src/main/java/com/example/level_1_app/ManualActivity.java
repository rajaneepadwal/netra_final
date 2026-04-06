package com.example.level_1_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.joystick_lib.Joystick_Lib;
import com.example.level_1_app.BluetoothConnectionManager;
import com.example.level_1_app.BluetoothActivity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.ToggleButton;
public class ManualActivity extends AppCompatActivity {
    private Joystick_Lib joystickLib;
    private BluetoothConnectionManager bluetoothConnection;
    private TextView txtFunc, tvStatus;

    // Toast management variables [web:23][web:27]
    private Toast currentToast = null;
    private long lastToastTime = 0;
    private static final long TOAST_INTERVAL = 3000; // 3 seconds between toasts
    private String lastToastMessage = "";
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private boolean isTiltEnabled = false;
    private String lastCommand = "S*";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Action bar hide
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        setContentView(R.layout.activity_manual);
        txtFunc = findViewById(R.id.txtFunc);
        tvStatus = findViewById(R.id.txtStatus);

        Button btnGrip = findViewById(R.id.btnGrip);
        Button btnUngrip = findViewById(R.id.btnUngrip);

        Button btnTriangle = findViewById(R.id.btnTriangle);
        Button btnSquare = findViewById(R.id.btnSquare);
        Button btnCircle = findViewById(R.id.btnCircle);
        Button btnCross = findViewById(R.id.btnCross);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnReconnect = findViewById(R.id.btnReconnect);

        joystickLib = findViewById(R.id.joystick_Lib);

        ToggleButton toggleTilt = findViewById(R.id.toggleTilt);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        bluetoothConnection = BluetoothConnectionManager.getInstance();

        updateConnectionStatus(); // Centralized status update

        joystickLib.setOnMoveListener(new Joystick_Lib.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // Determine command based on angle
                if (strength == 0) {
                    txtFunc.setText("S*");
                } else if (angle >= 45 && angle <= 135) {
                    txtFunc.setText("F*");
                } else if (angle >= 225 && angle <= 315) {
                    txtFunc.setText("B*");
                } else if (angle > 135 && angle < 225) {
                    txtFunc.setText("L*");
                } else {
                    txtFunc.setText("R*");
                }

                String currentCommand = txtFunc.getText().toString();
                sendData(currentCommand);
            }
        });

        btnGrip.setOnClickListener(v -> {
            txtFunc.setText("G*");
            sendData("G*");
        });

        btnUngrip.setOnClickListener(v -> {
            txtFunc.setText("U*");
            sendData("U*");
        });

        btnTriangle.setOnClickListener(v -> {
            txtFunc.setText("W*");
            sendData("W*");
        });

        btnSquare.setOnClickListener(v -> {
            txtFunc.setText("A*");
            sendData("A*");
        });

        btnCircle.setOnClickListener(v -> {
            txtFunc.setText("D*");
            sendData("D*");
        });

        btnCross.setOnClickListener(v -> {
            txtFunc.setText("X*");
            sendData("X*");
        });

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ManualActivity.this, MenuActivity.class);
            startActivity(intent);
            finish();
        });
        toggleTilt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isTiltEnabled = isChecked;

            if (isChecked) {
                joystickLib.setEnabled(false);

                // STOP before switching
                txtFunc.setText("S*");
                sendData("S*");
                lastCommand = "S*";

                sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                showThrottledToast("Tilt Control ON", false);

            } else {
                joystickLib.setEnabled(true);
                sensorManager.unregisterListener(sensorListener);
                showThrottledToast("Tilt Control OFF", false);
            }
        });

        btnReconnect.setOnClickListener(v -> {
            showThrottledToast("Reconnecting...", false); // Always show reconnect attempt

            bluetoothConnection.reconnectToLastDevice(this, new BluetoothConnectionManager.ReconnectCallback() {
                @Override
                public void onReconnectSuccess() {
                    updateConnectionStatus();
                    showThrottledToast("Reconnected successfully", false); // Important status
                }

                @Override
                public void onReconnectFailed(String errorMessage) {
                    updateConnectionStatus();
                    showThrottledToast(errorMessage, false); // Important error
                }
            });
        });
    }

    // Centralized connection status update [web:23]
    private void updateConnectionStatus() {
        if (bluetoothConnection.isConnected()) {
            tvStatus.setText("CONNECTED");
            tvStatus.setTextColor(Color.GREEN);
        } else {
            tvStatus.setText("DISCONNECTED");
            tvStatus.setTextColor(Color.RED);
        }
    }

    // Rate-limited Toast with duplicate prevention [web:23][web:27][web:29]
    private void showThrottledToast(String message, boolean throttle) {
        long currentTime = System.currentTimeMillis();

        // Skip if same message within interval and throttling enabled
        if (throttle &&
                message.equals(lastToastMessage) &&
                (currentTime - lastToastTime) < TOAST_INTERVAL) {
            return;
        }

        // Cancel previous toast to avoid accumulation [web:23][web:30]
        if (currentToast != null) {
            currentToast.cancel();
        }

        // Show new toast
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();

        lastToastTime = currentTime;
        lastToastMessage = message;
    }
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {

            if (!isTiltEnabled) return;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float shake = Math.abs(x) + Math.abs(y) + Math.abs(z);

            String command;

// SHAKE STOP
            if (shake > 25) {
                command = "S*";
            }

// DEAD ZONE (no movement)
            else if (Math.abs(x) < 2 && Math.abs(y) < 2) {
                command = "S*";
            }

// LEFT / RIGHT priority
            else if (x > 3) {
                command = "L*";
            }
            else if (x < -3) {
                command = "R*";
            }

// FORWARD / BACK
            else if (y > 3) {
                command = "F*";
            }
            else if (y < -3) {
                command = "B*";
            }
            else {
                command = "S*";
            }

            // Avoid sending same command repeatedly
            if (!command.equals(lastCommand)) {
                txtFunc.setText(command);
                sendData(command);
                lastCommand = command;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void sendData(String data) {
        if (bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData(data);
        } else {
            // Only show "Not Connected" toast every 3 seconds [web:23]
            showThrottledToast("Not Connected!", true);
            updateConnectionStatus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(sensorListener);

        if (currentToast != null) {
            currentToast.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            bluetoothConnection.sendData("S*");
        }
        // Cancel toast on destroy [web:23]
        if (currentToast != null) {
            currentToast.cancel();
        }
    }
}
