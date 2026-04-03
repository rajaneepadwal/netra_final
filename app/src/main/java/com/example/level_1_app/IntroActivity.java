package com.example.level_1_app;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

public class IntroActivity extends AppCompatActivity {

    private static final String TAG = "IntroActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> {
                Log.d(TAG, "Get Started clicked");

                Intent intent = new Intent(IntroActivity.this, BluetoothActivity.class);
                startActivity(intent);

                finish();

                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        } else {
            Log.e(TAG, "Button not found! Check ID in XML.");
        }
    }
}