package io.github.armanayvazyan.propsy

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/** Backend-neutral key/value entry. [handle] is opaque and owned by the producing backend. */
data class PropsyEntry(val key: String, val value: String, val handle: Any)

/**
 * A pluggable key/value file backend — one per file format (.properties, .env).
 * Registered via the `io.github.armanayvazyan.propsy.backend` extension point;
 * the .env backend is contributed only when the dotenv plugin is present.
 */
interface PropsyBackend {
    /** Resolves [relPath] (relative to project base) to an opaque handle this backend owns, or null. */
    fun resolve(project: Project, relPath: String): Any?

    /** Reads entries of a handle produced by [resolve], in document order. */
    fun entries(handle: Any): List<PropsyEntry>

    fun setValue(project: Project, entry: PropsyEntry, newValue: String)

    fun setKey(project: Project, entry: PropsyEntry, newKey: String)

    /** Returns true if added; false if the key already exists. Appends at the end. */
    fun addEntry(project: Project, handle: Any, key: String, value: String): Boolean

    fun deleteEntry(project: Project, entry: PropsyEntry)

    /** Discovers files of this backend's format in the project, each named after its owning module. */
    fun discover(project: Project): List<PathEntry>

    companion object {
        val EP_NAME: ExtensionPointName<PropsyBackend> =
            ExtensionPointName.create("io.github.armanayvazyan.propsy.backend")
    }
}
