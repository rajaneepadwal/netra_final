package com.example.level_1_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MenuActivity extends AppCompatActivity {

    private TextView tvDeviceName;

    private CardView cardManual, cardColor, cardPerson, cardChatbot, cardGesture;
    private Button btnDisconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        tvDeviceName = findViewById(R.id.tvDeviceName);

        // Cards (UI buttons)
        cardManual = findViewById(R.id.cardManual);
        cardColor = findViewById(R.id.cardColor);
        cardPerson = findViewById(R.id.cardPerson);
//        cardChatbot = findViewById(R.id.cardChatbot);
        cardGesture = findViewById(R.id.cardGesture);

        // Bottom button
        btnDisconnect = findViewById(R.id.btnDisconnect);

        // Bluetooth status
        BluetoothConnectionManager manager = BluetoothConnectionManager.getInstance();

        if (manager.isConnected() && manager.getDeviceName() != null) {
            tvDeviceName.setText("Connected: " + manager.getDeviceName());
        } else {
            tvDeviceName.setText("No device connected");
        }

        // Click actions
        cardManual.setOnClickListener(v ->
                startActivity(new Intent(this, ManualActivity.class)));

        cardPerson.setOnClickListener(v ->
                startActivity(new Intent(this, Person_Following_Activity.class)));

        cardColor.setOnClickListener(v ->
                startActivity(new Intent(this, ColorActivity.class)));

//        cardChatbot.setOnClickListener(v ->
//                startActivity(new Intent(this, Chatbot_Activity.class)));

        cardGesture.setOnClickListener(v -> {
            startActivity(new Intent(this, GestureActivity.class));
        });

        // Disconnect button
        btnDisconnect.setOnClickListener(v -> {
            manager.disconnect();
            Intent intent = new Intent(MenuActivity.this, BluetoothActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}