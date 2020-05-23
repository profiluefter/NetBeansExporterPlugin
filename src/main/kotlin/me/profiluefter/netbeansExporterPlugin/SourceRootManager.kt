package me.profiluefter.netbeansExporterPlugin

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.enumMapOf
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.util.logging.Logger

class SourceRootManager {
    companion object {
        val sourceRoots: MutableMap<SourceRootType, MutableList<MySourceRoot>> = enumMapOf()

        init {
            SourceRootType.values().forEach { sourceRoots[it] = ArrayList() }
        }

        fun init(project: Project) {
            val projectBaseFolder = LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
                ?: throw NetBeansExportException("Project root does not exist!")

            val projectRootManager = ProjectRootManager.getInstance(project)
            projectRootManager
                .getModuleSourceRoots(setOf(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE))
                .forEach {
                    val name = (getModuleNameByFile(project, it) ?: "") + it.name
                    sourceRoots[SourceRootType.Source]!!.add(
                        MySourceRoot(
                            generateNextId(it.name, SourceRootType.Source),
                            it.relativePathFrom(projectBaseFolder),
                            name.replaceFirst(project.name + ".", "")
                        )
                    )
                }
            projectRootManager
                .getModuleSourceRoots(setOf(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE))
                .forEach {
                    val name = (getModuleNameByFile(project, it) ?: "") + it.name
                    sourceRoots[SourceRootType.Test]!!.add(
                        MySourceRoot(
                            generateNextId(it.name, SourceRootType.Test),
                            it.relativePathFrom(projectBaseFolder),
                            name.replaceFirst(project.name + ".", "")
                        )
                    )
                }

            projectRootManager.orderEntries()
        }

        private fun getModuleNameByFile(project: Project, file: VirtualFile): String? {
            val modules = ModuleManager.getInstance(project).modules.filter { it.moduleScope.contains(file) }.toList()
            if (modules.size > 1)
                Logger.getLogger(SourceRootManager::class.java.name)
                    .warning("Found more than one matching module for folder ${file.path}! Using first result!")
            return if (modules.isNotEmpty()) modules[0].name.replaceFirst(project.name+".", "")+"." else null
        }

        private fun generateNextId(folderName: String, type: SourceRootType): String {
            val filteredName = folderName.filter { it.isJavaIdentifierPart() }

            if (sourceRoots[type]!!.any { it.id == filteredName }) {
                if (filteredName.last().isDigit())
                    return generateNextId(filteredName.dropLast(1) + filteredName.last().toInt().inc().toString(), type)
                return generateNextId(filteredName + "2", type)
            }
            return filteredName
        }
    }
}

data class MySourceRoot(
        val id: String,
        val path: String,
        val name: String
)

enum class SourceRootType {
    Source, Test
}