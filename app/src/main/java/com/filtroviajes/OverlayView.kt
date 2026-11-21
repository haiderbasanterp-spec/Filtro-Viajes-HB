package com.filtroviajes

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Tarjeta flotante que se dibuja encima de la app del conductor. No intercepta toques. */
class OverlayView(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: LinearLayout? = null

    fun show(ok: Boolean, lines: List<String>) {
        hide()
        val d = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setColor(if (ok) 0xEE1B8F3A.toInt() else 0xEEC62828.toInt())
            }
        }
        lines.forEachIndexed { i, s ->
            box.addView(TextView(ctx).apply {
                text = s
                setTextColor(Color.WHITE)
                textSize = if (i == 0) 20f else 14f
                if (i == 0) setTypeface(null, Typeface.BOLD)
            })
        }
        val lp = WindowManager.LayoutParams(
            (ctx.resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (60 * d).toInt()
        }
        try {
            wm.addView(box, lp)
            view = box
        } catch (_: Exception) {
            view = null
        }
    }

    fun hide() {
        view?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        view = null
    }
}
