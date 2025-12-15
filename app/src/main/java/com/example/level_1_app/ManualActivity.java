package com.example.level_1_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.joystick_lib.Joystick_Lib;

public class ManualActivity extends AppCompatActivity {
    private Joystick_Lib joystickLib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Action bar hide
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.BLACK);

        setContentView(R.layout.activity_manual);

        TextView txtFunc = findViewById(R.id.txtFunc);
        TextView tvTitle = findViewById(R.id.txtStatus);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
//        new Intent(BluetoothActivity.this, IntroActivity.class);
            startActivity(new Intent(ManualActivity.this, MenuActivity.class));//back to intro screen
            finish();
        });
        tvTitle.setText("DISCONNECTED");

        joystickLib = findViewById(R.id.joystick_Lib);
        joystickLib.setOnMoveListener(new Joystick_Lib.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                Log.d("Joystick", "Angle: " + angle + ", Strength: " + strength);
                if (angle>=315 && angle<=359 || angle<=45&&angle>=0){
                    txtFunc.setText("R*");
                } else if (angle>=225 && angle<=314) {
                    txtFunc.setText("B*");
                } else if (angle>=134 && angle<=224) {
                    txtFunc.setText("L*");
                }else{
                    txtFunc.setText("F*");

                }
            }
        });
    }
}