package com.example.level_1_app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

public class IntroActivity extends AppCompatActivity {

    private static final String TAG = "IntroActivity";

    private boolean hasNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        if (savedInstanceState != null) {
            hasNavigated = savedInstanceState.getBoolean("hasNavigated", false);
        }

        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> {

                if (hasNavigated) return; // prevent double click / multiple launches
                hasNavigated = true;

                Log.d(TAG, "Get Started clicked");

                try {
                    Intent intent = new Intent(IntroActivity.this, BluetoothActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start BluetoothActivity", e);
                }

                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });

        } else {
            Log.e(TAG, "Button not found! Check ID in XML.");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("hasNavigated", hasNavigated);
    }
}
