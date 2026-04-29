package com.example.level_1_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

public class HandOverlayView extends View {

    private List<NormalizedLandmark> landmarks;

    private final Paint pointPaint = new Paint();
    private final Paint linePaint  = new Paint();
    private final Paint tipPaint   = new Paint();

    // Finger connections: [start, end] landmark index pairs
    // Full hand skeleton
    private static final int[][] CONNECTIONS = {
            // Palm
            {0, 1}, {1, 2}, {2, 3}, {3, 4},       // Thumb
            {0, 5}, {5, 6}, {6, 7}, {7, 8},         // Index
            {0, 9}, {9, 10}, {10, 11}, {11, 12},    // Middle
            {0, 13}, {13, 14}, {14, 15}, {15, 16},  // Ring
            {0, 17}, {17, 18}, {18, 19}, {19, 20},  // Pinky
            {5, 9}, {9, 13}, {13, 17}                // Palm knuckle row
    };

    // Tip landmark indices (highlighted differently)
    private static final int[] TIPS = {4, 8, 12, 16, 20};

    public HandOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HandOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        pointPaint.setColor(Color.parseColor("#00E5FF"));
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        tipPaint.setColor(Color.parseColor("#FF4081"));
        tipPaint.setStyle(Paint.Style.FILL);
        tipPaint.setAntiAlias(true);

        linePaint.setColor(Color.parseColor("#76FF03"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setAntiAlias(true);
    }

    public void setLandmarks(List<NormalizedLandmark> landmarks) {
        this.landmarks = landmarks;
        invalidate();
    }

    public void clear() {
        this.landmarks = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (landmarks == null || landmarks.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();

        // Draw connections
        for (int[] conn : CONNECTIONS) {
            NormalizedLandmark p1 = landmarks.get(conn[0]);
            NormalizedLandmark p2 = landmarks.get(conn[1]);

            // Mirror X for front camera
            canvas.drawLine(
                    (1f - p1.x()) * w, p1.y() * h,
                    (1f - p2.x()) * w, p2.y() * h,
                    linePaint
            );
        }

        // Draw all joints
        for (int i = 0; i < landmarks.size(); i++) {
            NormalizedLandmark lm = landmarks.get(i);
            float x = (1f - lm.x()) * w;
            float y = lm.y() * h;

            boolean isTip = false;
            for (int tip : TIPS) { if (i == tip) { isTip = true; break; } }

            canvas.drawCircle(x, y, isTip ? 12f : 7f, isTip ? tipPaint : pointPaint);
        }
    }
}