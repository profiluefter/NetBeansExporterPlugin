package me.profiluefter.netbeansExporterPlugin

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBList
import com.intellij.util.io.exists
import me.profiluefter.netbeansExporterPlugin.NetBeansProjectFile.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringWriter
import java.nio.file.Paths
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

val notificationGroup: NotificationGroup = 
    NotificationGroup("NetBeans Export Errors", NotificationDisplayType.BALLOON, true)

class NetBeansExportException(override var message: String) : Exception(message)

enum class NetBeansProjectFile(val fileName: String) {
    PROJECT_XML("nbproject/project.xml"),
    PROJECT_PROPERTIES("nbproject/project.properties"),
    GENFILES_PROPERTIES("nbproject/genfiles.properties"),
    MANIFEST_MF("manifest.mf")
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

        object : Task.Backgroundable(project, "Exporting NetBeans Project...", true, PerformInBackgroundOption.DEAF) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Generating files..."
                    indicator.isIndeterminate = false

                    values().forEach {
                        indicator.fraction += 1.toDouble() / values().size
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

                    indicator.checkCanceled()
                    indicator.text = "Writing files..."

                    map.forEach {
                        indicator.fraction += 1.toDouble() / values().size
                        val file = Paths.get(project.basePath!!, it.key.fileName).toFile()
                        file.parentFile.mkdirs()
                        file.writeText(it.value)
                    }

                    indicator.checkCanceled()
                    indicator.text = "Refreshing files..."
                    indicator.isIndeterminate = true

                    LocalFileSystem.getInstance().refresh(false)
                } catch (exception: NetBeansExportException) {
                    handleError(exception, project)
                }
            }
        }.queue()
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

fun generateFileContent(project: Project, file: NetBeansProjectFile): String {
    when (file) {
        GENFILES_PROPERTIES -> {
            val properties = Properties()
            arrayOf("data", "script", "stylesheet").forEach { properties["nbproject/build-impl.xml.$it.CRC32"] = "" }
            return properties.output
        }
        PROJECT_XML -> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

            document.xmlStandalone = true //Remove standalone="no"

            document += document.createElement("project").apply {
                this["xmlns"] = "http://www.netbeans.org/ns/project/1"
                this += document.createElement("type").apply {
                    textContent = "org.netbeans.modules.java.j2seproject"
                }
                this += document.createElement("configuration").apply {
                    this += document.createElement("data").apply {
                        this["xmlns"] = "http://www.netbeans.org/ns/j2se-project/3"
                        this += document.createElement("name").apply {
                            textContent = project.name
                        }
                        this += document.createElement("source-roots").apply {
                            //TODO: Use modules
                            this += document.createElement("root").apply {
                                this["id"] = "src.dir"
                            }
                        }
                        this += document.createElement("test-roots").apply {
                            //TODO: Use modules
                            this += document.createElement("root").apply {
                                this["id"] = "test.src.dir"
                            }
                        }
                    }
                }
            }

            return document.content
        }
        PROJECT_PROPERTIES -> {
            val properties = Properties().apply {
                load(object {}.javaClass.classLoader.getResourceAsStream("project.properties"))
            }

            properties["dist.jar"] = "\${dist.dir}/${project.name}.jar"
            properties["dist.jlink.output"] = "\${dist.jlink.dir}/${project.name}"
            properties["jlink.launcher.name"] = project.name

            //TODO: Use modules
            properties["src.dir"] = "src"
            properties["test.src.dir"] = "test"

            val languageLevel =
                LanguageLevelProjectExtension.getInstance(project).languageLevel.toJavaVersion().toString()
            properties["javac.source"] = languageLevel
            properties["javac.target"] = languageLevel

            //TODO: Check for excluded
            properties["excludes"] = ""
            properties["includes"] = "**"

            val list = RunManager.getInstance(project).allConfigurationsList
                .asSequence()
                .filter { it.type is ApplicationConfigurationType }
                .map { it as ApplicationConfiguration }
                .mapNotNull { it.mainClass }
                .mapNotNull { it.qualifiedName }
                .toList()
            //TODO: Support choosing the Main class using TreeClassChooserFactory
            if (list.size != 1)
                throw NetBeansExportException("Could not find a single Main class! Actual amount: ${list.size}")
            properties["main.class"] = list[0]

            //TODO: Dependencies

            return properties.output
        }
        MANIFEST_MF -> {
            return "Manifest-Version: 1.0\n"
        }
    }
}

private val Properties.output: String
    get() {
        val writer = StringWriter()
        store(writer, "Generated by NetBeansExporter-Plugin by Profiluefter")
        return writer.toString()
    }
private val Document.content: String
    get() {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(this), StreamResult(writer))
        return writer.toString()
    }

private operator fun Element.set(name: String, value: String) = setAttribute(name, value)
private operator fun Element.get(name: String): String = getAttribute(name)
private operator fun Node.plusAssign(element: Element) {
    appendChild(element)
}