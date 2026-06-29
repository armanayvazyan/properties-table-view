package io.github.armanayvazyan.propsy

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import javax.swing.table.AbstractTableModel

/**
 * Editable table model over a single [PropertiesFile]. Column 0 = key, column 1 = value.
 * Edits are written straight through the [PropertiesFileBridge]; the owning view is
 * responsible for reloading after structural changes (add/delete/key rename).
 */
class PropsyTableModel(
    private val project: Project,
) : AbstractTableModel() {

    private var file: PropertiesFile? = null
    private var rows: List<PropertiesFileBridge.Entry> = emptyList()

    fun load(file: PropertiesFile?) {
        this.file = file
        rows = file?.let { PropertiesFileBridge.entries(it) } ?: emptyList()
        fireTableDataChanged()
    }

    fun currentFile(): PropertiesFile? = file

    fun entryAt(row: Int): PropertiesFileBridge.Entry? = rows.getOrNull(row)

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 2

    override fun getColumnName(column: Int): String = if (column == 0) "Key" else "Value"

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val entry = rows[rowIndex]
        return if (columnIndex == 0) entry.key else entry.value
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val entry = rows.getOrNull(rowIndex) ?: return
        val text = aValue?.toString() ?: ""
        if (columnIndex == 0) {
            if (text.isBlank() || text == entry.key) return
            PropertiesFileBridge.setKey(project, entry.property, text)
        } else {
            if (text == entry.value) return
            PropertiesFileBridge.setValue(project, entry.property, text)
        }
        // Re-read so cached key/value stay consistent with the PSI.
        load(file)
    }
}
