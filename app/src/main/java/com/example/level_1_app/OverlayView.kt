package com.example.level_1_app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.level_1_app.ObjectDetector.Detection

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f // Slightly thicker for better visibility
    }

    private val textPaint = Paint().apply {
        color = Color.CYAN
        textSize = 45f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var results: List<Detection> = emptyList()
    private var scaleX = 1f
    private var scaleY = 1f

    fun setResults(list: List<Detection>, sX: Float, sY: Float) {
        results = list
        scaleX = sX
        scaleY = sY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (det in results) {
            val box = det.box

            // Map detection box directly to screen coordinates with zero smoothing
            val mappedBox = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY
            )

            canvas.drawRect(mappedBox, boxPaint)

            canvas.drawText(
                "${det.label} ${(det.score * 100).toInt()}%",
                mappedBox.left,
                mappedBox.top - 15f,
                textPaint
            )
        }
    }
}