package me.profiluefter.netbeansExporterPlugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

class NetBeansExportAction : AnAction(), DumbAware {
    private val newGroup: DefaultActionGroup? =
        ActionManager.getInstance().getAction("FileExportGroup") as DefaultActionGroup?
    private val oldGroup: DefaultActionGroup? =
        ActionManager.getInstance().getAction("ExportImportGroup") as DefaultActionGroup?

    init {
        newGroup?.remove(this)
        newGroup?.addAction(this)
    }

    override fun update(e: AnActionEvent) {
        //FIXME: Think of a better way of backwards compatibility
        if (newGroup != null) {
            oldGroup?.remove(this)
            e.presentation.text = "Project to NetBeans..."
        }
    }

    override fun actionPerformed(event: AnActionEvent) = exportNetBeansProject(event.project, false)
}