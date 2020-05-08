package me.profiluefter.netbeansExporterPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class NetBeansExportAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) = exportNetBeansProject(event.project, false)
}