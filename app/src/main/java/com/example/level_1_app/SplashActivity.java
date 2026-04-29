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
    private boolean hasNavigated = false;

    private Runnable navigationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        lottieAnim1 = findViewById(R.id.lottieAnim1);
        lottieAnim2 = findViewById(R.id.lottieAnim2);
        popupText = findViewById(R.id.popupText);

        popupText.setAlpha(0f);

        // Restore state (important for rotation / process kill)
        if (savedInstanceState != null) {
            anim1Done = savedInstanceState.getBoolean("anim1Done", false);
            anim2Done = savedInstanceState.getBoolean("anim2Done", false);
            hasNavigated = savedInstanceState.getBoolean("hasNavigated", false);
        }

        // Prevent listener stacking
        lottieAnim1.removeAllAnimatorListeners();
        lottieAnim2.removeAllAnimatorListeners();

        lottieAnim1.setSpeed(1f);
        lottieAnim2.setSpeed(1f);

        lottieAnim1.playAnimation();
        lottieAnim2.playAnimation();

        lottieAnim2.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showPopupText();
                anim2Done = true;
                checkAndMove();
            }
        });

        lottieAnim1.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                anim1Done = true;
                checkAndMove();
            }
        });

        // If already done (after recreation), continue flow
        checkAndMove();
    }

    private void showPopupText() {

        if (isFinishing() || isDestroyed()) return;

        popupText.setScaleX(0.05f);
        popupText.setScaleY(0.05f);
        popupText.setAlpha(0f);

        popupText.post(() -> {

            if (isFinishing() || isDestroyed()) return;

            popupText.setPivotX(popupText.getWidth() / 2f);
            popupText.setPivotY(popupText.getHeight() / 2f);

            popupText.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.OvershootInterpolator())
                    .withEndAction(() ->

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
        if (anim1Done && anim2Done && !hasNavigated) {
            hasNavigated = true;

            navigationRunnable = () -> {
                try {
                    startActivity(new Intent(SplashActivity.this, IntroActivity.class));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                finish();
            };

            popupText.postDelayed(navigationRunnable, 400);
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
        if (lottieAnim1 != null && !lottieAnim1.isAnimating()) {
            lottieAnim1.resumeAnimation();
        }
        if (lottieAnim2 != null && !lottieAnim2.isAnimating()) {
            lottieAnim2.resumeAnimation();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("anim1Done", anim1Done);
        outState.putBoolean("anim2Done", anim2Done);
        outState.putBoolean("hasNavigated", hasNavigated);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (navigationRunnable != null) {
            popupText.removeCallbacks(navigationRunnable);
        }

        if (lottieAnim1 != null) lottieAnim1.cancelAnimation();
        if (lottieAnim2 != null) lottieAnim2.cancelAnimation();
    }
}
