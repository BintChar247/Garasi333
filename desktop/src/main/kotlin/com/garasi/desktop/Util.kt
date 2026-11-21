package com.garasi.desktop

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

object Os {
    fun copy(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

    fun paste(): String? = runCatching {
        val c = Toolkit.getDefaultToolkit().systemClipboard
        if (c.isDataFlavorAvailable(DataFlavor.stringFlavor)) c.getData(DataFlavor.stringFlavor) as String else null
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun browse(url: String): Boolean = runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)

    fun openFolder(dir: File): Boolean = runCatching { dir.mkdirs(); Desktop.getDesktop().open(dir); true }.getOrDefault(false)

    fun pickImages(title: String): List<File> {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } }
            file = "*.jpg;*.jpeg;*.png"
            isVisible = true
        }
        return d.files?.toList().orEmpty()
    }
}
