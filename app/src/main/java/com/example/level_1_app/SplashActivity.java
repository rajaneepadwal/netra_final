package com.example.level_1_app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    private LottieAnimationView lottieAnim1, lottieAnim2;
    private TextView popupText;

    private boolean anim1Done = false;
    private boolean anim2Done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        lottieAnim1 = findViewById(R.id.lottieAnim1);
        lottieAnim2 = findViewById(R.id.lottieAnim2);
        popupText = findViewById(R.id.popupText);

        // Hide text initially
        popupText.setAlpha(0f);

        // Normal speed
        lottieAnim1.setSpeed(1f);
        lottieAnim2.setSpeed(1f);

        // Start animations
        lottieAnim1.playAnimation();
        lottieAnim2.playAnimation();

        // Animate text AFTER main animation ends (best UX)
        lottieAnim2.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

                showPopupText();  // <-- text animation

                anim2Done = true;
                checkAndMove();
            }
        });

        // Listener for animation 1
        lottieAnim1.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                anim1Done = true;
                checkAndMove();
            }
        });
    }

    private void showPopupText() {

        // Start as a tiny dot
        popupText.setScaleX(0.05f);
        popupText.setScaleY(0.05f);
        popupText.setAlpha(0f);

        // Ensure it expands from center (dot effect)
        popupText.post(() -> {
            popupText.setPivotX(popupText.getWidth() / 2f);
            popupText.setPivotY(popupText.getHeight() / 2f);

            // Step 1: Explosive expand
            popupText.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.OvershootInterpolator())
                    .withEndAction(() ->

                            // Step 2: Settle to normal size
                            popupText.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(120)
                                    .withEndAction(this::startShake)
                                    .start()

                    )
                    .start();
        });
    }
    private void startShake() {
        popupText.animate()
                .translationX(10f)
                .setDuration(50)
                .withEndAction(() ->
                        popupText.animate()
                                .translationX(-10f)
                                .setDuration(50)
                                .withEndAction(() ->
                                        popupText.animate()
                                                .translationX(6f)
                                                .setDuration(40)
                                                .withEndAction(() ->
                                                        popupText.animate()
                                                                .translationX(-6f)
                                                                .setDuration(40)
                                                                .withEndAction(() ->
                                                                        popupText.animate()
                                                                                .translationX(0f)
                                                                                .setDuration(30)
                                                                                .start()
                                                                )
                                                                .start()
                                                )
                                                .start()
                                )
                                .start()
                )
                .start();
    }

    private void checkAndMove() {
        if (anim1Done && anim2Done) {
            popupText.postDelayed(() -> {
                startActivity(new Intent(SplashActivity.this, IntroActivity.class));
                finish();
            }, 400); // small delay so user sees text
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lottieAnim1 != null) lottieAnim1.pauseAnimation();
        if (lottieAnim2 != null) lottieAnim2.pauseAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lottieAnim1 != null) lottieAnim1.resumeAnimation();
        if (lottieAnim2 != null) lottieAnim2.resumeAnimation();
    }
}