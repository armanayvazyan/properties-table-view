package io.github.armanayvazyan.propsy

import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project

/** Backend for `.properties` files, delegating to [PropertiesFileBridge]. Handle = PropertiesFile; entry handle = IProperty. */
class PropertiesBackend : PropsyBackend {
    override fun resolve(project: Project, relPath: String): Any? =
        PropertiesFileBridge.resolve(project, relPath)

    override fun entries(handle: Any): List<PropsyEntry> =
        PropertiesFileBridge.entries(handle as PropertiesFile)
            .map { PropsyEntry(it.key, it.value, it.property) }

    override fun setValue(project: Project, entry: PropsyEntry, newValue: String) =
        PropertiesFileBridge.setValue(project, entry.handle as IProperty, newValue)

    override fun setKey(project: Project, entry: PropsyEntry, newKey: String) =
        PropertiesFileBridge.setKey(project, entry.handle as IProperty, newKey)

    override fun addEntry(project: Project, handle: Any, key: String, value: String): Boolean =
        PropertiesFileBridge.addEntry(project, handle as PropertiesFile, key, value)

    override fun deleteEntry(project: Project, entry: PropsyEntry) =
        PropertiesFileBridge.deleteEntry(project, entry.handle as IProperty)

    override fun discover(project: Project): List<PathEntry> = PropertiesScanner.scan(project)
}
