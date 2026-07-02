package io.github.armanayvazyan.propsy

import com.intellij.openapi.project.Project
import javax.swing.table.AbstractTableModel

/**
 * Editable table model over a single resolved key/value file. Column 0 = key, column 1 = value.
 * Edits are written straight through the backing [ResolvedFile]; the owning view reloads
 * after structural changes (add/delete/key rename).
 */
class PropsyTableModel(
    private val project: Project,
) : AbstractTableModel() {

    private var file: ResolvedFile? = null
    private var rows: List<PropsyEntry> = emptyList()

    fun load(file: ResolvedFile?) {
        this.file = file
        rows = file?.entries() ?: emptyList()
        fireTableDataChanged()
    }

    fun currentFile(): ResolvedFile? = file

    fun entryAt(row: Int): PropsyEntry? = rows.getOrNull(row)

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
        val f = file ?: return
        val text = aValue?.toString() ?: ""
        if (columnIndex == 0) {
            if (text.isBlank() || text == entry.key) return
            f.setKey(project, entry, text)
        } else {
            if (text == entry.value) return
            f.setValue(project, entry, text)
        }
        // Re-read so cached key/value stay consistent with the backing file.
        load(file)
    }
}
