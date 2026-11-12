package com.example.cleaner

import android.content.Context
import android.os.Environment
import java.io.File

data class JunkItem(val file: File, val category: String, val size: Long)

object Cleaner {

    private val tempExt = setOf("tmp", "temp", "bak", "old", "chk", "dmp", "crdownload", "part")

    /** Pindai file sampah di penyimpanan + cache aplikasi ini. */
    fun scan(context: Context): List<JunkItem> {
        val result = mutableListOf<JunkItem>()

        // 1. Cache aplikasi ini sendiri
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                result += JunkItem(f, "Cache aplikasi", sizeOf(f))
            }
        }

        // 2. Penyimpanan bersama
        val root = Environment.getExternalStorageDirectory()
        if (!root.canRead()) return result

        root.walkTopDown()
            .onEnter { dir ->
                val p = dir.absolutePath
                // Android/data & Android/obb tidak bisa diakses di Android 11+
                !(p.endsWith("/Android/data") || p.endsWith("/Android/obb"))
            }
            .forEach { f ->
                try {
                    when {
                        f.isDirectory -> {
                            if (f != root && f.list()?.isEmpty() == true)
                                result += JunkItem(f, "Folder kosong", 0)
                        }
                        f.parentFile?.name == ".thumbnails" ->
                            result += JunkItem(f, "Thumbnail", f.length())
                        f.length() == 0L ->
                            result += JunkItem(f, "File kosong", 0)
                        f.extension.lowercase() == "log" ->
                            result += JunkItem(f, "File log", f.length())
                        f.extension.lowercase() in tempExt ->
                            result += JunkItem(f, "File sementara", f.length())
                        f.extension.lowercase() == "apk" ->
                            result += JunkItem(f, "APK sisa instalasi", f.length())
                    }
                } catch (_: SecurityException) { }
            }
        return result
    }

    /** Hapus item terpilih, kembalikan total byte yang dibebaskan. */
    fun clean(items: List<JunkItem>): Long {
        var freed = 0L
        items.forEach { item ->
            if (item.file.exists() && item.file.deleteRecursively()) freed += item.size
        }
        return freed
    }

    private fun sizeOf(f: File): Long =
        if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.2f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
