package io.github.armanayvazyan.propsy

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Settings page under Settings | Tools | Propsy.
 * Onboarding text plus an editable Name/Path table of the configured files.
 */
class PropsyConfigurable(private val project: Project) : Configurable {

    private val model = EntriesTableModel(emptyList())
    private val table = JBTable(model)
    private var panel: JComponent? = null

    override fun getDisplayName(): String = "Propsy"

    override fun createComponent(): JComponent {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.emptyText.text = "No properties files configured — click Scan or +"
        table.columnModel.getColumn(0).preferredWidth = 160
        table.columnModel.getColumn(1).preferredWidth = 420

        val header = JBLabel(
            "<html><body style='width:480px'>" +
                "Choose which <b>.properties</b> files appear in the Propsy tool window. " +
                "Click <b>Scan</b> to auto-discover every <code>.properties</code> file in your modules, " +
                "or <b>+</b> to add one manually. Edit the <b>Name</b> column to label each file — " +
                "that name is what the tool window shows." +
                "</body></html>",
        )
        header.border = JBUI.Borders.empty(8)

        val tablePanel = ToolbarDecorator.createDecorator(table)
            .setAddAction { chooseAndAdd() }
            .setRemoveAction { removeSelected() }
            .addExtraAction(object : DumbAwareAction(
                "Scan",
                "Scan modules for .properties files",
                AllIcons.Actions.Refresh,
            ) {
                override fun actionPerformed(e: AnActionEvent) = scanAndMerge()
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            .createPanel()

        val root = JPanel(BorderLayout())
        root.add(header, BorderLayout.NORTH)
        root.add(tablePanel, BorderLayout.CENTER)
        panel = root
        reset()
        return root
    }

    private fun chooseAndAdd() {
        val base = project.guessProjectDir()
        if (base == null) {
            Messages.showWarningDialog(project, "Project base directory is unknown.", "Add Path")
            return
        }
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Select Properties File")
            .withFileFilter { it.extension.equals("properties", ignoreCase = true) }
            .withRoots(base)
        val chosen = FileChooser.chooseFile(descriptor, project, base) ?: return
        val rel = VfsUtilCore.getRelativePath(chosen, base, '/')
        if (rel == null) {
            Messages.showWarningDialog(project, "File must live inside the project.", "Add Path")
            return
        }
        if (model.paths().contains(rel)) return
        val name = ProjectFileIndex.getInstance(project).getModuleForFile(chosen)?.name
            ?: rel.substringAfterLast('/')
        model.add(PathEntry(name, rel))
    }

    private fun scanAndMerge() {
        val existing = model.paths()
        val discovered = PropsyFiles.discoverAll(project).filter { it.path !in existing }
        discovered.forEach { model.add(it) }
        val message = if (discovered.isEmpty()) {
            "No new .properties files found."
        } else {
            "Added ${discovered.size} file(s)."
        }
        Messages.showInfoMessage(project, message, "Scan Properties Files")
    }

    private fun removeSelected() {
        val row = table.selectedRow
        if (row >= 0) model.removeAt(table.convertRowIndexToModel(row))
    }

    override fun isModified(): Boolean =
        model.items() != PropsySettings.getInstance(project).entries

    override fun apply() {
        val settings = PropsySettings.getInstance(project)
        settings.entries = model.items()
        project.messageBus.syncPublisher(PropsySettings.CHANGED_TOPIC).run()
    }

    override fun reset() {
        model.replaceAll(PropsySettings.getInstance(project).entries)
    }

    /** Two-column model: editable Name (0), read-only Path (1). */
    private class EntriesTableModel(initial: List<PathEntry>) : AbstractTableModel() {
        private val rows = initial.map { PathEntry(it.name, it.path) }.toMutableList()

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 2
        override fun getColumnName(column: Int): String = if (column == 0) "Name" else "Path"
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            if (columnIndex == 0) rows[rowIndex].name else rows[rowIndex].path

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex != 0) return
            rows[rowIndex].name = aValue?.toString()?.trim() ?: ""
            fireTableCellUpdated(rowIndex, columnIndex)
        }

        fun items(): List<PathEntry> = rows.map { PathEntry(it.name, it.path) }
        fun paths(): Set<String> = rows.map { it.path }.toSet()

        fun add(entry: PathEntry) {
            rows.add(PathEntry(entry.name, entry.path))
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
        }

        fun removeAt(index: Int) {
            rows.removeAt(index)
            fireTableRowsDeleted(index, index)
        }

        fun replaceAll(items: List<PathEntry>) {
            rows.clear()
            rows.addAll(items.map { PathEntry(it.name, it.path) })
            fireTableDataChanged()
        }
    }
}
