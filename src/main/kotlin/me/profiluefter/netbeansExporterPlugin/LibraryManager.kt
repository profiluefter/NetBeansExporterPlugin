package me.profiluefter.netbeansExporterPlugin

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.containers.enumMapOf
import java.io.File

class LibraryManager {
    companion object {
        val changedFiles: MutableList<File> = ArrayList()
        val libraries: MutableMap<DependencyScope, MutableList<Library>> = enumMapOf()

        init {
            DependencyScope.values().forEach {
                libraries[it] = ArrayList()
            }
        }

        fun init(project: Project) {
            val projectBase = LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
                ?: throw NetBeansExportException("Project root directory does not exist")

            var librariesFolder = projectBase.findChild("lib")
            if (librariesFolder == null)
                WriteAction.run<NetBeansExportException> {
                    librariesFolder = projectBase.createChildDirectory(null, "lib")
                    changedFiles.add(File(librariesFolder!!.path))
                }
            assert(librariesFolder != null)

            fun addLibrary(pair: Pair<DependencyScope, com.intellij.openapi.roots.libraries.Library>) {
                val scope = pair.first
                val library = pair.second

                val classPathEntries = ArrayList<String>()
                for (virtualFile in library.getFiles(OrderRootType.CLASSES)) {
                    if (librariesFolder!!.findChild(virtualFile.name) == null)
                        WriteAction.run<NetBeansExportException> {
                            val libraryFile = (LocalFileSystem.getInstance()
                                .findFileByPath(virtualFile.path.trimEnd('!', '/', '\\'))
                                ?: throw NetBeansExportException("Library path for library \"${library.name}\" could not be found on disk!"))
                            libraryFile.copy(null, librariesFolder!!, virtualFile.name)

                            changedFiles.add(File(libraryFile.path))
                        }

                    classPathEntries.add("\${base}/${virtualFile.name}")
                }
                val name = library.name ?: libraries.size.toString()
                val libraryObject = Library(
                    name.filter { it.isJavaIdentifierPart() },
                    classPathEntries.joinToString(":"),
                    name
                )

                libraries.getValue(scope).add(libraryObject)
            }

            val disposableModels: MutableList<ModifiableRootModel> = ArrayList()

            ModuleManager.getInstance(project).modules
                .flatMap {
                    disposableModels.addAndReturn(ModuleRootManager.getInstance(it).modifiableModel)
                        .orderEntries.asIterable() }
                .filterIsInstance<LibraryOrderEntry>()
                .map {
                    if(it.scope == DependencyScope.COMPILE && ModuleRootManager.getInstance(it.ownerModule).getSourceRoots(false).isEmpty())
                        return@map DependencyScope.TEST to it.library!!
                    return@map it.scope to it.library!!
                }
                .forEach(::addLibrary)

            disposableModels.forEach(ModifiableRootModel::dispose)
        }
    }
}

data class Library(
        val id: String,
        val classpath: String,
        val displayName: String
)