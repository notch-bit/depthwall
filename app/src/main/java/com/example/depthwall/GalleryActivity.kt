package com.example.depthwall

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/** Shows images bundled in assets/gallery and returns the chosen file name. */
class GalleryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET = "asset_name"
        private const val DIR = "gallery"
        private val EXT = listOf(".jpg", ".jpeg", ".png", ".webp")
    }

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val names = (assets.list(DIR) ?: emptyArray())
            .filter { n -> EXT.any { n.lowercase().endsWith(it) } }
            .sorted()

        if (names.isEmpty()) {
            setContentView(TextView(this).apply {
                text = "No bundled wallpapers yet.\nAdd images to app/src/main/assets/gallery/ and rebuild."
                setPadding(48, 48, 48, 48)
            })
            return
        }

        val cell = resources.displayMetrics.widthPixels / 2
        val grid = GridView(this).apply {
            numColumns = 2
            adapter = object : BaseAdapter() {
                override fun getCount() = names.size
                override fun getItem(i: Int) = names[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
                    val iv = (convert as? ImageView) ?: ImageView(this@GalleryActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = AbsListViewParams(cell, (cell * 1.6f).toInt())
                    }
                    val name = names[i]
                    iv.tag = name
                    val cached = cache[name]
                    if (cached != null) {
                        iv.setImageBitmap(cached)
                    } else {
                        iv.setImageDrawable(null)
                        pool.execute {
                            val bmp = loadThumb(name, cell)
                            if (bmp != null) main.post {
                                cache[name] = bmp
                                if (iv.tag == name) iv.setImageBitmap(bmp)
                            }
                        }
                    }
                    return iv
                }
            }
            setOnItemClickListener { _, _, pos, _ ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_ASSET, names[pos]))
                finish()
            }
        }
        setContentView(grid)
    }

    private fun AbsListViewParams(w: Int, h: Int) = android.widget.AbsListView.LayoutParams(w, h)

    private fun loadThumb(name: String, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open("$DIR/$name").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        assets.open("$DIR/$name").use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) { null }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
    }
}
