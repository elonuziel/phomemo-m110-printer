package de.l0rz.m110

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Speichert gedruckte Labels als PNG im App-Speicher und lädt sie für die Galerie. */
object LabelStore {

    private fun dir(ctx: Context) = File(ctx.filesDir, "labels").apply { mkdirs() }

    /** ts = Zeitstempel (System.currentTimeMillis) — vom Aufrufer, damit hier keine Uhr nötig ist. */
    fun save(ctx: Context, bmp: Bitmap, ts: Long) {
        val f = File(dir(ctx), "label_$ts.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun loadThumb(f: File, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var s = 1
        while (o.outHeight / s > maxPx || o.outWidth / s > maxPx) s *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun loadFull(f: File): Bitmap? = BitmapFactory.decodeFile(f.absolutePath)

    fun delete(f: File): Boolean = f.delete()
}
