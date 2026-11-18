package com.seagori.hgr.desktop

import com.seagori.hgr.i18n.AppLocale
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object DesktopFiles {
    fun openMarkdown(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Markdown"
            fileFilter = FileNameExtensionFilter("Markdown (*.md, *.markdown, *.txt)", "md", "markdown", "txt")
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun openBackup(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Backup"
            fileFilter = FileNameExtensionFilter("${AppLocale.strings.appName} (*.scbackup)", "scbackup", "zip")
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun saveBackup(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Backup"
            fileFilter = FileNameExtensionFilter("${AppLocale.strings.appName} (*.scbackup)", "scbackup")
            selectedFile = File("onestep-backup.scbackup")
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val selected = chooser.selectedFile ?: return null
        return if (selected.name.contains('.')) selected else File(selected.parentFile, "${selected.name}.scbackup")
    }
}
