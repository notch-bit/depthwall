package com.example.depthwall

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.depthwall.Store.clockColor
import com.example.depthwall.Store.clockSize
import com.example.depthwall.Store.clockStyle
import com.example.depthwall.Store.intensity
import com.example.depthwall.Store.showClock
import com.example.depthwall.Store.use24h
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ImageView
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val bmp = try { decode(uri) } catch (e: Exception) { null }
        if (bmp == null) {
            Toast.makeText(this, "Could not open that image", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        segment(bmp)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val name = res.data?.getStringExtra(GalleryActivity.EXTRA_ASSET) ?: return@registerForActivityResult
        val bmp = try {
            assets.open("gallery/$name").use { BitmapFactory.decodeStream(it) }
                ?.let { b ->
                    val s = 1600f / maxOf(b.width, b.height)
                    if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
                }
        } catch (e: Exception) { null }
        if (bmp == null) {
            Toast.makeText(this, "Could not open that wallpaper", Toast.LENGTH_SHORT).show()
        } else {
            segment(bmp.copy(Bitmap.Config.ARGB_8888, false))
        }
    }

    private fun decode(uri: android.net.Uri): Bitmap {
        val maxSide = 1600
        val raw: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = maxSide.toFloat() / maxOf(info.size.width, info.size.height)
                if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
        } else {
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it)!! }
        }
        return raw.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun segment(bmp: Bitmap) {
        status.text = "Detecting subject… (first run may download a small model)"
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        segmenter.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { result ->
                val fg = result.foregroundBitmap
                Store.saveLayers(this, bmp, fg)
                preview.setImageBitmap(bmp)
                status.text = if (fg != null) "Subject found. Ready to apply." else "No subject found; using plain parallax."
            }
            .addOnFailureListener {
                Store.saveLayers(this, bmp, null)
                preview.setImageBitmap(bmp)
                status.text = "Segmentation unavailable (${it.message}). Using plain parallax."
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            Store.loadBackground(this@MainActivity)?.let { setImageBitmap(it) }
        }
        status = TextView(this).apply { text = "Pick a photo with a clear subject (person, pet, object)." }

        val pick = Button(this).apply {
            text = "Choose photo"
            setOnClickListener {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        val gallery = Button(this).apply {
            text = "Browse built-in gallery"
            setOnClickListener {
                galleryLauncher.launch(Intent(this@MainActivity, GalleryActivity::class.java))
            }
        }

        val clockBox = CheckBox(this).apply {
            text = "Show clock behind subject"
            isChecked = this@MainActivity.showClock
            setOnCheckedChangeListener { _, v -> this@MainActivity.showClock = v }
        }
        val h24 = CheckBox(this).apply {
            text = "24-hour clock"
            isChecked = this@MainActivity.use24h
            setOnCheckedChangeListener { _, v -> this@MainActivity.use24h = v }
        }

        fun slider(label: String, init: Int, onChange: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(TextView(this).apply { text = label })
            row.addView(SeekBar(this).apply {
                max = 100
                progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) = onChange(p)
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
            return row
        }

        val intensityRow = slider("Parallax strength", (intensity / 1.5f * 100).toInt()) {
            intensity = it / 100f * 1.5f
        }
        val sizeRow = slider("Clock size", ((clockSize - 0.1f) / 0.3f * 100).toInt()) {
            clockSize = 0.1f + it / 100f * 0.3f
        }

        val styleNames = listOf("Thin digital", "Stacked", "Time + date", "Analog", "Outline", "Heavy bold")
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        styleRow.addView(TextView(this).apply { text = "Clock style" })
        styleRow.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, styleNames)
            setSelection(this@MainActivity.clockStyle)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    this@MainActivity.clockStyle = pos
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })

        val colors = listOf(
            "White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(), "Amber" to 0xFFFFC107.toInt(),
            "Sky" to 0xFF4FC3F7.toInt(), "Rose" to 0xFFFF6B9A.toInt(), "Mint" to 0xFF69F0AE.toInt()
        )
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colorRow.addView(TextView(this).apply { text = "Clock color" })
        colorRow.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, colors.map { it.first })
            setSelection(colors.indexOfFirst { it.second == this@MainActivity.clockColor }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    this@MainActivity.clockColor = colors[pos].second
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })

        val apply = Button(this).apply {
            text = "Set as live wallpaper"
            setOnClickListener {
                val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, DepthWallpaperService::class.java)
                )
                try { startActivity(i) } catch (e: Exception) {
                    startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                }
            }
        }

        listOf(preview, status, pick, gallery, clockBox, styleRow, colorRow, h24, sizeRow, intensityRow, apply).forEach { root.addView(it) }
        setContentView(root)
    }
}
