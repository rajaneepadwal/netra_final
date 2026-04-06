//package com.example.level_1_app;
//
//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.view.Gravity;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.LinearLayout;
//import android.widget.ScrollView;
//import android.widget.TextView;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.io.IOException;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//import okhttp3.MediaType;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//
//public class Chatbot_Activity extends AppCompatActivity {
//
//    private final String SERVER_URL = "https://Vishrut29-trfchatbot.hf.space/chat";
//
//    private LinearLayout chatContainer;
//    private EditText etMessage;
//    private Button btnSend;
//    private ScrollView scrollView;
//
//    private final OkHttpClient client = new OkHttpClient();
//    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        setContentView(R.layout.activity_chatbot);
//
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
//        }
//
//        chatContainer = findViewById(R.id.chat_container);
//        etMessage = findViewById(R.id.et_message);
//        btnSend = findViewById(R.id.btn_send);
//        scrollView = findViewById(R.id.scroll_view);
//
//        btnSend.setOnClickListener(v -> {
//            String msg = etMessage.getText().toString().trim();
//            if (!msg.isEmpty()) {
//                sendMessage(msg);
//            }
//        });
//    }
//
//    private void sendMessage(String msg) {
//        addMessage(msg, true);
//        etMessage.setText(""); // we use setText("") to clear in Java
//
//        executorService.execute(() -> {
//            try {
//                JSONObject json = new JSONObject();
//                json.put("message", msg);
//
//                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
//                RequestBody body = RequestBody.create(json.toString(), mediaType);
//
//                Request request = new Request.Builder()
//                        .url(SERVER_URL)
//                        .post(body)
//                        .build();
//
//                try (Response response = client.newCall(request).execute()) {
//                    if (response.isSuccessful() && response.body() != null) {
//                        String respBody = response.body().string();
//                        // Extracting "answer" from JSON response
//                        JSONObject jsonResponse = new JSONObject(respBody);
//                        String botAnswer = jsonResponse.optString("answer", "No answer received");
//
//                        runOnUiThread(() -> addMessage(botAnswer, false));
//                    } else {
//                        runOnUiThread(() -> addMessage("Server Error: " + response.code(), false));
//                    }
//                }
//            } catch (IOException | JSONException e) {
//                runOnUiThread(() -> addMessage("Connection Failed", false));
//            }
//        });
//    }
//
//    private void addMessage(String text, boolean isUser) {
//        TextView tv = new TextView(this);
//        tv.setText(text);
//        tv.setTextSize(18f);
//        tv.setPadding(30, 20, 30, 20);
//        tv.setTextColor(Color.WHITE);
//
//        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//        );
//        params.setMargins(10, 10, 10, 10);
//
//        if (isUser) {
//            params.gravity = Gravity.END;
//            tv.setBackgroundColor(Color.parseColor("#4CAF50"));
//        } else {
//            params.gravity = Gravity.START;
//            tv.setBackgroundColor(Color.parseColor("#444444"));
//        }
//
//        tv.setLayoutParams(params);
//        chatContainer.addView(tv);
//
//        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        executorService.shutdown();
//    }
//}