package io.github.armanayvazyan.propsy

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Tool window content: a path picker on top and an editable Key/Value table below.
 */
class PropsyTablePanel(
    private val project: Project,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()) {

    private val comboModel = DefaultComboBoxModel<PathEntry>()
    private val combo = ComboBox(comboModel)
    private val statusLabel = JBLabel()
    private val tableModel = PropsyTableModel(project)
    private val table = JBTable(tableModel)

    /** Guards combo mutations in [refreshAll] so the action listener does not fire mid-rebuild. */
    private var suppressComboEvents = false

    init {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(true)

        combo.addActionListener { if (!suppressComboEvents) reloadSelectedFile() }
        combo.renderer = SimpleListCellRenderer.create("") { it.name.ifBlank { it.path } }

        val top = JPanel(BorderLayout())
        top.border = JBUI.Borders.empty(4)
        top.add(combo, BorderLayout.CENTER)
        top.add(statusLabel, BorderLayout.SOUTH)

        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddAction { addRow() }
            .setRemoveAction { removeRow() }
            .addExtraAction(object : DumbAwareAction("Refresh", "Reload from disk", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) = refreshAll()
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            .createPanel()

        add(top, BorderLayout.NORTH)
        add(decorated, BorderLayout.CENTER)

        project.messageBus.connect(parentDisposable)
            .subscribe(PropsySettings.CHANGED_TOPIC, Runnable { refreshAll() })

        refreshAll()
    }

    /** Repopulates the combo from settings, keeping the current selection if still present. */
    private fun refreshAll() {
        val previousPath = (combo.selectedItem as? PathEntry)?.path
        val entries = PropsySettings.getInstance(project).entries
        suppressComboEvents = true
        try {
            comboModel.removeAllElements()
            entries.forEach { comboModel.addElement(it) }
            when {
                entries.isEmpty() -> combo.selectedItem = null
                previousPath != null && entries.any { it.path == previousPath } ->
                    combo.selectedItem = entries.first { it.path == previousPath }
                else -> combo.selectedIndex = 0
            }
        } finally {
            suppressComboEvents = false
        }
        if (entries.isEmpty()) {
            tableModel.load(null)
            statusLabel.text = "No paths configured. Add them in Settings | Tools | Propsy."
        } else {
            reloadSelectedFile()
        }
    }

    private fun currentPath(): String? = (combo.selectedItem as? PathEntry)?.path

    private fun reloadSelectedFile() {
        val path = currentPath() ?: run {
            tableModel.load(null)
            return
        }
        val file = PropsyFiles.resolve(project, path)
        if (file == null) {
            tableModel.load(null)
            statusLabel.text = "Cannot resolve '$path' (missing or not a supported .properties/.env file)."
        } else {
            tableModel.load(file)
            statusLabel.text = " "
        }
    }

    private fun currentFileOrWarn(): ResolvedFile? {
        val file = tableModel.currentFile()
        if (file == null) {
            Messages.showWarningDialog(project, "Select a resolvable properties/.env file first.", "Propsy")
        }
        return file
    }

    private fun addRow() {
        val file = currentFileOrWarn() ?: return
        val key = Messages.showInputDialog(
            project, "Property key:", "Add Property", null,
        )?.trim()
        if (key.isNullOrEmpty()) return
        val added = file.addEntry(project, key, "")
        if (!added) {
            Messages.showWarningDialog(project, "Key '$key' already exists.", "Add Property")
            return
        }
        reloadSelectedFile()
    }

    private fun removeRow() {
        val row = table.selectedRow
        if (row < 0) return
        val file = tableModel.currentFile() ?: return
        val entry = tableModel.entryAt(table.convertRowIndexToModel(row)) ?: return
        file.deleteEntry(project, entry)
        reloadSelectedFile()
    }
}
