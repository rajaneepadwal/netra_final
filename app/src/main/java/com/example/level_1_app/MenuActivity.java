package com.example.level_1_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    private TextView tvDeviceName;
    private Button btnOption1, btnOption2, btnOption3, btnDisconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        tvDeviceName = findViewById(R.id.tvDeviceName);
        btnOption1 = findViewById(R.id.btnOption1);
        btnOption2 = findViewById(R.id.btnOption2);
        btnOption3 = findViewById(R.id.btnOption3);
        btnDisconnect = findViewById(R.id.btnDisconnect);

        // Get connection info from singleton
        BluetoothConnectionManager mgr = BluetoothConnectionManager.getInstance();

        if (mgr.isConnected() && mgr.getDeviceName() != null) {
            tvDeviceName.setText("Connected: " + mgr.getDeviceName());
        } else {
            tvDeviceName.setText("No device connected");
        }

        btnOption1.setOnClickListener(v ->
                startActivity(new Intent(this, ManualActivity.class)));

        btnOption2.setOnClickListener(v ->
                startActivity(new Intent(this, Person_Following_Activity.class)));

        btnOption3.setOnClickListener(v ->
                startActivity(new Intent(this, Ocr_Activity.class)));


        btnDisconnect.setOnClickListener(v -> {
            mgr.disconnect();
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void sendCommand(String cmd) {
        BluetoothConnectionManager mgr = BluetoothConnectionManager.getInstance();
        if (!mgr.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = mgr.sendData(cmd + "\n");
        if (!ok) {
            Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show();
        }
    }
}
