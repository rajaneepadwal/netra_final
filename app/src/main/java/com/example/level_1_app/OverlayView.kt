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
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var results: List<Detection> = emptyList()
    private var scaleX = 1f
    private var scaleY = 1f
    private var rotationDegrees = 0

    // 🔥 Added rotation parameter to setResults
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

            // 🔥 COORDINATE TRANSFORMATION BASED ON SENSOR ROTATION
            // This fixes boxes appearing "to the side" or "above"
            when (rotationDegrees) {
                90 -> {
                    rect.left = (1f - box.bottom) * scaleX
                    rect.top = box.left * scaleY
                    rect.right = (1f - box.top) * scaleX
                    rect.bottom = box.right * scaleY
                }
                270 -> {
                    rect.left = box.top * scaleX
                    rect.top = (1f - box.right) * scaleY
                    rect.right = box.bottom * scaleX
                    rect.bottom = (1f - box.left) * scaleY
                }
                else -> { // 0 or 180
                    rect.left = box.left * scaleX
                    rect.top = box.top * scaleY
                    rect.right = box.right * scaleX
                    rect.bottom = box.bottom * scaleY
                }
            }

            canvas.drawRect(rect, boxPaint)
            canvas.drawText("${det.label}", rect.left, rect.top - 10f, textPaint)
        }
    }
}