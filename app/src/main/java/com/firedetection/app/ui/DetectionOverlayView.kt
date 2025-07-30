package com.firedetection.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rects = mutableListOf<Pair<RectF, Paint>>()
    private val texts = mutableListOf<Triple<String, Float, Float>>()
    private val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
    }

    fun addRect(rect: RectF, paint: Paint) {
        rects.add(rect to paint)
        invalidate()
    }

    fun addText(text: String, x: Float, y: Float) {
        texts.add(Triple(text, x, y))
        invalidate()
    }

    fun clear() {
        rects.clear()
        texts.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw rectangles
        rects.forEach { (rect, paint) ->
            canvas.drawRect(rect, paint)
        }
        
        // Draw text
        texts.forEach { (text, x, y) ->
            canvas.drawText(text, x, y, textPaint)
        }
    }
} 