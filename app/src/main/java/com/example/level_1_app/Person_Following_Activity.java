package com.example.level_1_app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Person_Following_Activity extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_following);

        TextView tvTitle = findViewById(R.id.tvFaceTitle);
        tvTitle.setText("Person Following- Coming Soon");
    }
}
