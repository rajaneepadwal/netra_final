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
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.CYAN
        textSize = 45f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var results: List<Detection> = emptyList()
    private var scaleX = 1f
    private var scaleY = 1f
    private var rotationDegrees = 0

    fun setResults(list: List<Detection>, sX: Float, sY: Float, rotation: Int) {
        results = list
        scaleX = sX
        scaleY = sY
        rotationDegrees = rotation
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (det in results) {
            val box = det.box
            val rect = RectF()

            // 🔥 COORDINATE ROTATION FIX:
            // This prevents the box from appearing at the "top" or "side" of the object
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                // Swap and flip coordinates to align sensor-space with screen-space
                rect.left = box.top * scaleX
                rect.top = box.left * scaleY
                rect.right = box.bottom * scaleX
                rect.bottom = box.right * scaleY
            } else {
                rect.left = box.left * scaleX
                rect.top = box.top * scaleY
                rect.right = box.right * scaleX
                rect.bottom = box.bottom * scaleY
            }

            canvas.drawRect(rect, boxPaint)
            canvas.drawText("${det.label} ${(det.score * 100).toInt()}%", rect.left, rect.top - 15f, textPaint)
        }
    }
}