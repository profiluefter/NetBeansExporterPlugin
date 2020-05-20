package me.profiluefter.netbeansExporterPlugin

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBList
import com.intellij.util.io.exists
import me.profiluefter.netbeansExporterPlugin.NetBeansProjectFile.values
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList

val notificationGroup: NotificationGroup =
    NotificationGroup("NetBeans Export Errors", NotificationDisplayType.BALLOON, true)

class NetBeansExportException(override var message: String) : Exception(message)

enum class NetBeansProjectFile(val fileName: String) {
    PROJECT_XML("nbproject/project.xml"),
    PROJECT_PROPERTIES("nbproject/project.properties"),
    GENFILES_PROPERTIES("nbproject/genfiles.properties"),
    MANIFEST_MF("manifest.mf"),
    NBLIBRARIES_PROPERTIES("lib/nblibraries.properties")
}

fun handleError(exception: NetBeansExportException, project: Project?) {
    val notification =
        notificationGroup.createNotification("Error while exporting!", null, exception.message, NotificationType.ERROR)
    notification.addAction(NetBeansExportAction())
    notification.notify(project)
}

fun exportNetBeansProject(project: Project?, force: Boolean = false) {
    try {
        if (project == null)
            throw NetBeansExportException("Project not yet initialized!")
        if (project.basePath == null)
            throw NetBeansExportException("Could not get path of project!")

        val map = EnumMap<NetBeansProjectFile, String>(NetBeansProjectFile::class.java)

        val conflicts = values().filter { Paths.get(project.basePath!!, it.fileName).exists() }.toList()
        if (!force && conflicts.isNotEmpty()) {
            askIfShouldOverride(project, conflicts)
            return
        }

        val changedFiles: MutableList<File> = ArrayList()

        LibraryManager.init(project)
        changedFiles.addAll(LibraryManager.changedFiles)

        values().forEach {
            var exception: NetBeansExportException? = null
            ReadAction.run<NetBeansExportException> {
                try {
                    map[it] = generateFileContent(project, it)
                } catch (e: NetBeansExportException) {
                    exception = e
                }
            }
            if (exception != null) throw exception!!
        }

        map.forEach {
            val file = Paths.get(project.basePath!!, it.key.fileName).toFile()
            file.parentFile.mkdirs()
            file.writeText(it.value)
            changedFiles.add(file)
        }

        LocalFileSystem.getInstance().refreshIoFiles(changedFiles)
    } catch (exception: NetBeansExportException) {
        handleError(exception, project)
    }
}

fun askIfShouldOverride(project: Project, conflicts: List<NetBeansProjectFile>) {
    val override = DialogBuilder(project).apply {
        title("Files already exist!")
        setErrorText("The above files already exist and would be overwritten!")
        setCenterPanel(JBList(conflicts.map { it.fileName }))
        okActionEnabled(true)
        addOkAction().setText("Override")
        addCancelAction().setText("Cancel")
    }.showAndGet()

    if (override)
        exportNetBeansProject(project, true)
}
