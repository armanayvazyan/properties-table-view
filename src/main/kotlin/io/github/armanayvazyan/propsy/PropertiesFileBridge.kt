package io.github.armanayvazyan.propsy

import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiManager

/**
 * Bridges a project-relative path to the bundled Properties PSI and exposes the
 * mutation operations used by the table. All writes run in a [WriteCommandAction]
 * so they are undoable and preserve comments, blank lines and key order.
 */
object PropertiesFileBridge {

    /** A single key/value entry plus a stable reference to the backing PSI property. */
    data class Entry(val key: String, val value: String, val property: IProperty)

    /** Resolves [relPath] (relative to the project base dir) to a [PropertiesFile], or null. */
    fun resolve(project: Project, relPath: String): PropertiesFile? = ReadAction.compute<PropertiesFile?, RuntimeException> {
        val base = project.guessProjectDir() ?: return@compute null
        val vf = base.findFileByRelativePath(relPath.trim()) ?: return@compute null
        if (vf.isDirectory || !vf.isValid) return@compute null
        PsiManager.getInstance(project).findFile(vf) as? PropertiesFile
    }

    /** Reads all entries of [file] in document order. */
    fun entries(file: PropertiesFile): List<Entry> = ReadAction.compute<List<Entry>, RuntimeException> {
        file.properties.map { p ->
            Entry(p.key ?: "", p.value ?: "", p)
        }
    }

    fun setValue(project: Project, property: IProperty, newValue: String) {
        WriteCommandAction.runWriteCommandAction(project, "Edit Property Value", null, {
            property.setValue(newValue)
        }, property.psiElement.containingFile)
    }

    fun setKey(project: Project, property: IProperty, newKey: String) {
        WriteCommandAction.runWriteCommandAction(project, "Rename Property Key", null, {
            property.setName(newKey)
        }, property.psiElement.containingFile)
    }

    /** Returns true if added; false if the key already exists. */
    fun addEntry(project: Project, file: PropertiesFile, key: String, value: String): Boolean {
        if (ReadAction.compute<Boolean, RuntimeException> { file.findPropertyByKey(key) != null }) return false
        WriteCommandAction.runWriteCommandAction(project, "Add Property", null, {
            // Append at the end to preserve existing key order (addProperty inserts alphabetically).
            val anchor = file.properties.lastOrNull() as? Property
            if (anchor != null) file.addPropertyAfter(key, value, anchor) else file.addProperty(key, value)
        }, file.containingFile)
        return true
    }

    fun deleteEntry(project: Project, property: IProperty) {
        WriteCommandAction.runWriteCommandAction(project, "Delete Property", null, {
            property.psiElement.delete()
        }, property.psiElement.containingFile)
    }
}
