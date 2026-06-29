package io.github.armanayvazyan.propsy

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Discovers .properties files inside the project's content roots and turns each
 * into a [PathEntry] named after its owning module.
 */
object PropertiesScanner {

    fun scan(project: Project): List<PathEntry> = ReadAction.compute<List<PathEntry>, RuntimeException> {
        val base = project.guessProjectDir() ?: return@compute emptyList()
        val fileIndex = ProjectFileIndex.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        FilenameIndex.getAllFilesByExt(project, "properties", scope)
            .asSequence()
            .filter { vf ->
                !vf.isDirectory &&
                    vf.isValid &&
                    fileIndex.isInContent(vf) &&
                    !fileIndex.isExcluded(vf) &&
                    !fileIndex.isInLibrary(vf)
            }
            .mapNotNull { vf ->
                val rel = VfsUtilCore.getRelativePath(vf, base, '/') ?: return@mapNotNull null
                val name = fileIndex.getModuleForFile(vf)?.name ?: base.name
                PathEntry(name, rel)
            }
            .distinct()
            .sortedWith(compareBy({ it.name }, { it.path }))
            .toList()
    }
}
