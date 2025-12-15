package com.example.level_1_app;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ProgressBar;

public class ProgressOverlay {

    private final View rootView;
    private final ProgressBar progressBar;
    private final TextureView runnerVideo;

    private MediaPlayer mediaPlayer;
    private Surface surface;
    private int progress = 0;

    public ProgressOverlay(View rootView) {
        this.rootView = rootView;

        progressBar = rootView.findViewById(R.id.progressBar);
        runnerVideo = rootView.findViewById(R.id.runnerVideo);

        // Enable transparency for TextureView
        runnerVideo.setOpaque(false);

        runnerVideo.setSurfaceTextureListener(
                new TextureView.SurfaceTextureListener() {

                    @Override
                    public void onSurfaceTextureAvailable(
                            SurfaceTexture surfaceTexture, int width, int height) {
                        playRunnerVideo(surfaceTexture);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(
                            SurfaceTexture surfaceTexture, int w, int h) {}

                    @Override
                    public boolean onSurfaceTextureDestroyed(
                            SurfaceTexture surfaceTexture) {
                        releaseMediaPlayer();
                        if (surface != null) {
                            surface.release();
                            surface = null;
                        }
                        return true;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(
                            SurfaceTexture surfaceTexture) {}
                });
    }

    private void playRunnerVideo(SurfaceTexture surfaceTexture) {
        try {
            releaseMediaPlayer();

            Context context = rootView.getContext();
            surface = new Surface(surfaceTexture);

            mediaPlayer = MediaPlayer.create(context, R.raw.doraemon);

            if (mediaPlayer == null) {
                android.util.Log.e("ProgressOverlay", "Failed to create MediaPlayer");
                return;
            }

            mediaPlayer.setSurface(surface);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void show() {
        progress = 0;
        progressBar.setProgress(0);
        runnerVideo.setTranslationX(0);
        rootView.setVisibility(View.VISIBLE);

        if (runnerVideo.isAvailable() && (mediaPlayer == null || !mediaPlayer.isPlaying())) {
            playRunnerVideo(runnerVideo.getSurfaceTexture());
        }
    }

    public void updateProgress(int value) {
        progress = Math.min(value, 100);
        progressBar.setProgress(progress);

        progressBar.post(() -> {
            int progressBarWidth = progressBar.getWidth();
            int runnerWidth = runnerVideo.getWidth();

            if (progressBarWidth > 0 && runnerWidth > 0) {
                int maxWidth = progressBarWidth - runnerWidth;
                float position = (progress / 100f) * maxWidth;
                runnerVideo.setTranslationX(position);
            }
        });

        if (progress >= 100) {
            progressBar.postDelayed(this::hide, 300);
        }
    }

    public void hide() {
        rootView.setVisibility(View.GONE);
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mediaPlayer = null;
            }
        }
    }

    public void release() {
        releaseMediaPlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }
}
