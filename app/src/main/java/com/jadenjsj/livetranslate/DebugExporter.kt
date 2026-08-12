package com.jadenjsj.livetranslate

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class DebugExporter(private val context: Context) {
    fun share() {
        val output = File(context.cacheDir, "exports/livetranslate-debug.zip").apply {
            parentFile?.mkdirs()
        }
        ZipOutputStream(FileOutputStream(output)).use { zip ->
            addTree(zip, File(context.filesDir, "debug_logs"), "debug_logs")
            addTree(zip, File(context.filesDir, "translation_history"), "translation_history")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", output)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(share, "Export Live Translate diagnostics")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun addTree(zip: ZipOutputStream, root: File, prefix: String) {
        if (!root.exists()) return
        root.walkTopDown().filter(File::isFile).forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            zip.putNextEntry(ZipEntry("$prefix/$relative"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
