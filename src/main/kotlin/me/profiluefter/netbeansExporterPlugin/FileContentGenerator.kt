package me.profiluefter.netbeansExporterPlugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.packaging.impl.elements.ManifestFileUtil
import me.profiluefter.netbeansExporterPlugin.Scope.*
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

fun generateFileContent(project: Project, file: NetBeansProjectFile): String {
    return when (file) {
        NetBeansProjectFile.GENFILES_PROPERTIES -> {
            val properties = Properties()
            arrayOf("data", "script", "stylesheet").forEach { properties["nbproject/build-impl.xml.$it.CRC32"] = "" }
            properties.output
        }
        NetBeansProjectFile.PROJECT_XML -> {
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
                    this += document.createElement("libraries").apply {
                        this["xmlns"] = "http://www.netbeans.org/ns/ant-project-libraries/1"
                        this += document.createElement("definitions").apply {
                            textContent = ".${File.separator}lib${File.separator}nblibraries.properties"
                        }
                    }
                }
            }

            document.content
        }
        NetBeansProjectFile.PROJECT_PROPERTIES -> {
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

            val main = ManifestFileUtil.selectMainClass(project, null)
                ?: throw NetBeansExportException("Please select a Main class!")
            properties["main.class"] = main.qualifiedName

            fun buildClasspath(vararg scopes: Scope): String {
                val classpath = ArrayList<String>()
                for (scope in scopes) {
                    when (scope) {
                        ProductionOutput -> classpath.add("\${build.classes.dir}")
                        TestOutput -> classpath.add("\${build.test.classes.dir}")
                        else -> {
                            classpath.addAll(LibraryManager.libraries[scope.dependencyScope]!!
                                .map(Library::id).map { "\${libs.$it.classpath}" })
                        }
                    }
                }
                return classpath.filterNot { it.isEmpty() }.distinct().joinToString(":")
            }

            properties["javac.classpath"] = buildClasspath(Compile, Provided)
            properties["run.classpath"] = buildClasspath(Compile, Runtime, ProductionOutput)
            properties["javac.test.classpath"] = buildClasspath(Compile, Test, Provided, ProductionOutput)
            properties["run.test.classpath"] = buildClasspath(Compile, Test, Runtime, Provided, ProductionOutput, TestOutput)

            properties.output
        }
        NetBeansProjectFile.MANIFEST_MF -> "Manifest-Version: 1.0\n"
        NetBeansProjectFile.NBLIBRARIES_PROPERTIES -> {
            val properties = Properties()

            LibraryManager.libraries.flatMap { it.value }.forEach {
                it.apply {
                    properties["libs.$id.classpath"] = classpath
                    properties["libs.$id.displayName"] = displayName
                    properties["libs.$id.prop-version"] = "3.0"
                }
            }

            properties.output
        }
    }
}

enum class Scope(val dependencyScope: DependencyScope?) {
    Compile(DependencyScope.COMPILE),
    Test(DependencyScope.TEST),
    Runtime(DependencyScope.RUNTIME),
    Provided(DependencyScope.PROVIDED),
    ProductionOutput(null),
    TestOutput(null)
}