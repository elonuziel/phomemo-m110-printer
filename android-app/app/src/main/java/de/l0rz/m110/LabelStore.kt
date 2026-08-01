package de.l0rz.m110

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Speichert gedruckte Labels als PNG im App-Speicher und lädt sie für die Galerie. */
object LabelStore {

    private fun dir(ctx: Context) = File(ctx.filesDir, "labels").apply { mkdirs() }

    /**
     * ts = Zeitstempel (System.currentTimeMillis) — vom Aufrufer.
     * Dedup: gleicher Inhalt (Hash) wird nicht doppelt gespeichert. Gibt true zurück, wenn NEU gespeichert.
     */
    fun save(ctx: Context, bmp: Bitmap, ts: Long): Boolean {
        val hash = hashOf(bmp)
        if (list(ctx).any { it.name.contains("_$hash") }) return false
        val f = File(dir(ctx), "label_${ts}_$hash.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }

    private fun hashOf(bmp: Bitmap): String {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val bb = java.nio.ByteBuffer.allocate(px.size * 4 + 8)
        bb.putInt(w); bb.putInt(h)
        for (p in px) bb.putInt(p)
        val md = java.security.MessageDigest.getInstance("MD5").digest(bb.array())
        return md.joinToString("") { "%02x".format(it) }.take(16)
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
