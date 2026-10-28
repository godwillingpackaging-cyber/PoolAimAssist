package com.example.poolaimassist

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** A dedicated MOVE handle avoids button touch events swallowing the drag gesture. */
class ControlPanelView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onCalibrate: () -> Unit,
    private val onStop: () -> Unit
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.argb(220, 20, 20, 20))
        setPadding(6, 5, 6, 5)
        val handle = TextView(context).apply {
            text = "≡ MOVE"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(12, 20, 12, 20)
        }
        addView(handle)
        addView(Button(context).apply { text = "Calibrate"; setOnClickListener { onCalibrate() } })
        addView(Button(context).apply { text = "Stop"; setOnClickListener { onStop() } })
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = layoutParams.x; startY = layoutParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = (startX + (event.rawX - downX).toInt()).coerceAtLeast(0)
                    layoutParams.y = (startY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    windowManager.updateViewLayout(this, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
}
