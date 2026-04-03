package com.example.level_1_app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.List;

public class OverlayView extends View {

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();

    private List<ObjectDetector.Detection> results = Collections.emptyList();

    private float scaleX = 1f;
    private float scaleY = 1f;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        boxPaint.setColor(Color.CYAN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);

        textPaint.setColor(Color.CYAN);
        textPaint.setTextSize(40f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setResults(List<ObjectDetector.Detection> list, float sX, float sY) {
        results = list;
        scaleX = sX;
        scaleY = sY;
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (ObjectDetector.Detection det : results) {

            RectF mappedBox = new RectF(
                    det.box.left * scaleX,
                    det.box.top * scaleY,
                    det.box.right * scaleX,
                    det.box.bottom * scaleY
            );

            canvas.drawRect(mappedBox, boxPaint);

            canvas.drawText(
                    det.label + " " + (int) (det.score * 100) + "%",
                    mappedBox.left,
                    mappedBox.top - 10f,
                    textPaint
            );
        }
    }
}