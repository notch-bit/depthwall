package com.example.depthwall

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.depthwall.Store.clockColor
import com.example.depthwall.Store.clockSize
import com.example.depthwall.Store.clockStyle
import com.example.depthwall.Store.intensity
import com.example.depthwall.Store.showClock
import com.example.depthwall.Store.use24h
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class DepthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DepthEngine()

    private inner class DepthEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val sensors by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }

        private var bg: Bitmap? = null
        private var fg: Bitmap? = null

        private var targetX = 0f
        private var targetY = 0f
        private var curX = 0f
        private var curY = 0f
        private var baseRoll = Float.NaN
        private var basePitch = Float.NaN

        private val rotMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        private var visible = false
        private val layerMatrix = Matrix()
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }

        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 16)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            reloadImages()
        }

        private fun reloadImages() {
            bg = Store.loadBackground(applicationContext)
            fg = Store.loadForeground(applicationContext)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                reloadImages()
                baseRoll = Float.NaN
                basePitch = Float.NaN
                val s = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                if (s != null) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
                handler.post(frame)
            } else {
                sensors.unregisterListener(this)
                handler.removeCallbacks(frame)
            }
        }

        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
            super.onSurfaceChanged(h, f, w, ht)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            sensors.unregisterListener(this)
            handler.removeCallbacks(frame)
        }

        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, e.values)
            SensorManager.getOrientation(rotMatrix, orientation)
            val pitch = orientation[1]
            val roll = orientation[2]
            if (baseRoll.isNaN()) {
                baseRoll = roll
                basePitch = pitch
            }
            // ~0.35 rad of tilt = full range
            targetX = ((roll - baseRoll) / 0.35f).coerceIn(-1f, 1f)
            targetY = ((pitch - basePitch) / 0.35f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}

        private fun drawLayer(c: Canvas, bmp: Bitmap, w: Int, h: Int, depth: Float, maxShift: Float) {
            val cover = max(w / bmp.width.toFloat(), h / bmp.height.toFloat()) * 1.12f
            layerMatrix.reset()
            layerMatrix.postScale(cover, cover)
            layerMatrix.postTranslate(
                (w - bmp.width * cover) / 2f + curX * maxShift * depth,
                (h - bmp.height * cover) / 2f + curY * maxShift * depth
            )
            c.drawBitmap(bmp, layerMatrix, bitmapPaint)
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private fun fmt(pattern: String, d: Date) =
            SimpleDateFormat(pattern, Locale.getDefault()).format(d)

        private fun drawClock(c: Canvas, w: Int, cx: Float, cy: Float) {
            val ctx = applicationContext
            val now = Date()
            val size = w * ctx.clockSize * 1.6f
            val color = ctx.clockColor
            val timePattern = if (ctx.use24h) "HH:mm" else "h:mm"

            clockPaint.color = color
            clockPaint.style = Paint.Style.FILL
            clockPaint.textSize = size

            when (ctx.clockStyle) {
                // 0: Thin digital
                0 -> {
                    clockPaint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                    c.drawText(fmt(timePattern, now), cx, cy, clockPaint)
                }
                // 1: Stacked hours over minutes
                1 -> {
                    clockPaint.typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
                    clockPaint.textSize = size * 1.1f
                    val hh = fmt(if (ctx.use24h) "HH" else "hh", now)
                    c.drawText(hh, cx, cy - size * 0.1f, clockPaint)
                    c.drawText(fmt("mm", now), cx, cy + size * 0.95f, clockPaint)
                }
                // 2: Time with date line
                2 -> {
                    clockPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    c.drawText(fmt(timePattern, now), cx, cy, clockPaint)
                    clockPaint.textSize = size * 0.22f
                    c.drawText(fmt("EEEE, d MMMM", now).uppercase(), cx, cy + size * 0.35f, clockPaint)
                }
                // 3: Analog
                3 -> {
                    val r = size * 0.9f
                    val cal = java.util.Calendar.getInstance()
                    val hr = (cal.get(java.util.Calendar.HOUR) + cal.get(java.util.Calendar.MINUTE) / 60f)
                    val min = cal.get(java.util.Calendar.MINUTE) + cal.get(java.util.Calendar.SECOND) / 60f
                    strokePaint.color = color
                    strokePaint.strokeWidth = r * 0.03f
                    c.drawCircle(cx, cy, r, strokePaint)
                    for (i in 0 until 12) {
                        val a = Math.toRadians(i * 30.0)
                        val inner = if (i % 3 == 0) 0.82f else 0.90f
                        c.drawLine(
                            cx + (Math.sin(a) * r * inner).toFloat(), cy - (Math.cos(a) * r * inner).toFloat(),
                            cx + (Math.sin(a) * r * 0.96f).toFloat(), cy - (Math.cos(a) * r * 0.96f).toFloat(),
                            strokePaint
                        )
                    }
                    fun hand(angleDeg: Double, len: Float, width: Float) {
                        strokePaint.strokeWidth = width
                        val a = Math.toRadians(angleDeg)
                        c.drawLine(cx, cy, cx + (Math.sin(a) * r * len).toFloat(), cy - (Math.cos(a) * r * len).toFloat(), strokePaint)
                    }
                    hand(hr * 30.0, 0.5f, r * 0.06f)
                    hand(min * 6.0, 0.78f, r * 0.04f)
                }
                // 4: Outline text
                4 -> {
                    clockPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    clockPaint.style = Paint.Style.STROKE
                    clockPaint.strokeWidth = size * 0.03f
                    c.drawText(fmt(timePattern, now), cx, cy, clockPaint)
                }
                // 5: Heavy bold
                else -> {
                    clockPaint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                    clockPaint.textSize = size * 1.15f
                    c.drawText(fmt(timePattern, now), cx, cy, clockPaint)
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
            try {
                val w = canvas.width
                val h = canvas.height
                canvas.drawColor(Color.BLACK)

                // Smooth the motion
                curX += (targetX - curX) * 0.12f
                curY += (targetY - curY) * 0.12f

                val ctx = applicationContext
                val maxShift = w * 0.04f * ctx.intensity

                bg?.let { drawLayer(canvas, it, w, h, -0.5f, maxShift) }

                if (ctx.showClock) {
                    val cx = w / 2f + curX * maxShift * -0.2f
                    val cy = h * 0.30f + curY * maxShift * -0.2f
                    drawClock(canvas, w, cx, cy)
                }

                fg?.let { drawLayer(canvas, it, w, h, 0.6f, maxShift) }
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }
    }
}
