package com.example.depthwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Stores settings and the two image layers (background + cut-out subject). */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("depthwall", Context.MODE_PRIVATE)

    private fun bgFile(c: Context) = File(c.filesDir, "bg.jpg")
    private fun fgFile(c: Context) = File(c.filesDir, "fg.png")

    fun saveLayers(c: Context, bg: Bitmap, fg: Bitmap?) {
        FileOutputStream(bgFile(c)).use { bg.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (fg != null) {
            FileOutputStream(fgFile(c)).use { fg.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            fgFile(c).delete()
        }
    }

    fun loadBackground(c: Context): Bitmap? =
        bgFile(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun loadForeground(c: Context): Bitmap? =
        fgFile(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    var Context.intensity: Float
        get() = prefs(this).getFloat("intensity", 1.0f)
        set(v) = prefs(this).edit().putFloat("intensity", v).apply()

    var Context.showClock: Boolean
        get() = prefs(this).getBoolean("clock", true)
        set(v) = prefs(this).edit().putBoolean("clock", v).apply()

    var Context.clockSize: Float
        get() = prefs(this).getFloat("clock_size", 0.22f)
        set(v) = prefs(this).edit().putFloat("clock_size", v).apply()

    var Context.clockStyle: Int
        get() = prefs(this).getInt("clock_style", 0)
        set(v) = prefs(this).edit().putInt("clock_style", v).apply()

    var Context.clockColor: Int
        get() = prefs(this).getInt("clock_color", 0xFFFFFFFF.toInt())
        set(v) = prefs(this).edit().putInt("clock_color", v).apply()

    var Context.use24h: Boolean
        get() = prefs(this).getBoolean("h24", false)
        set(v) = prefs(this).edit().putBoolean("h24", v).apply()
}
