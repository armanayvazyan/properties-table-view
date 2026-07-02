package io.github.armanayvazyan.propsy

import com.intellij.openapi.project.Project

/** A resolved key/value file: a backend paired with its opaque handle. */
class ResolvedFile(val backend: PropsyBackend, val handle: Any) {
    fun entries(): List<PropsyEntry> = backend.entries(handle)
    fun setValue(project: Project, entry: PropsyEntry, newValue: String) = backend.setValue(project, entry, newValue)
    fun setKey(project: Project, entry: PropsyEntry, newKey: String) = backend.setKey(project, entry, newKey)
    fun addEntry(project: Project, key: String, value: String): Boolean = backend.addEntry(project, handle, key, value)
    fun deleteEntry(project: Project, entry: PropsyEntry) = backend.deleteEntry(project, entry)
}

/** Resolves a project-relative path and discovers files across all registered backends. */
object PropsyFiles {
    fun resolve(project: Project, relPath: String): ResolvedFile? {
        for (backend in PropsyBackend.EP_NAME.extensionList) {
            val handle = backend.resolve(project, relPath) ?: continue
            return ResolvedFile(backend, handle)
        }
        return null
    }

    fun discoverAll(project: Project): List<PathEntry> =
        PropsyBackend.EP_NAME.extensionList
            .flatMap { it.discover(project) }
            .distinct()
            .sortedWith(compareBy({ it.name }, { it.path }))
}
