# Named Paths, Scan & Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bare path list with named entries, add a one-click Scan that discovers every `.properties` file in the project's modules, and add onboarding text to the settings page; the tool window selector shows the name instead of the path.

**Architecture:** `PropsViewSettings` stores a list of `PathEntry(name, path)` and auto-migrates the legacy `List<String>` paths on load. A new `PropertiesScanner` enumerates `.properties` files via `FilenameIndex` filtered through `ProjectFileIndex`. The settings configurable becomes an onboarding header + editable name/path table with Add/Scan/Remove. The tool-window combo renders entry names.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (2024.2), `com.intellij.properties` plugin, JUnit via `BasePlatformTestCase`, Gradle (`./gradlew`).

## Global Constraints

- Paths stored relative to project base dir, `/`-separated.
- Edits must keep existing behavior in `PropertiesFileBridge` untouched.
- Duplicate entry names are allowed; user renames manually.
- Migration is one-way and lossless: legacy path → `PathEntry(name = path.substringAfterLast('/'), path = path)`.
- Test command base: `./gradlew test` (single class: `./gradlew test --tests "io.github.armanayvazyan.propstableview.<Class>"`).

---

### Task 1: Data model + migration in `PropsViewSettings`

Introduce `PathEntry` and `State.entries`, migrate legacy `paths`, expose `entries`. Keep a transitional `paths` get/set proxy so `PropsTablePanel` and `PropsViewConfigurable` still compile until their own tasks. The proxy is removed in Task 4.

**Files:**
- Modify: `src/main/kotlin/io/github/armanayvazyan/propstableview/PropsViewSettings.kt`
- Test: `src/test/kotlin/io/github/armanayvazyan/propstableview/PropsViewSettingsTest.kt`

**Interfaces:**
- Produces:
  - `class PathEntry()` with `var name: String`, `var path: String`, secondary ctor `PathEntry(name: String, path: String)`, value-based `equals`/`hashCode`.
  - `var PropsViewSettings.entries: List<PathEntry>` (defensive copy on get and set).
  - Transitional `var PropsViewSettings.paths: List<String>` — get = `entries.map { it.path }`; set = `entries = value.map { PathEntry(it.substringAfterLast('/'), it) }`.

- [ ] **Step 1: Write the failing tests**

Replace the body of `PropsViewSettingsTest.kt` with:

```kotlin
package io.github.armanayvazyan.propstableview

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropsViewSettingsTest : BasePlatformTestCase() {

    fun testEntriesRoundTripThroughState() {
        val settings = PropsViewSettings.getInstance(project)
        settings.entries = listOf(
            PathEntry("messages", "src/messages.properties"),
            PathEntry("app", "config/app.properties"),
        )

        val state = settings.state
        val restored = PropsViewSettings()
        restored.loadState(state)

        assertEquals(
            listOf(
                PathEntry("messages", "src/messages.properties"),
                PathEntry("app", "config/app.properties"),
            ),
            restored.entries,
        )
    }

    fun testLegacyPathsMigrateToEntries() {
        val legacy = PropsViewSettings.State().apply {
            paths = mutableListOf("src/messages.properties", "config/app.properties")
        }
        val settings = PropsViewSettings()
        settings.loadState(legacy)

        assertEquals(
            listOf(
                PathEntry("messages.properties", "src/messages.properties"),
                PathEntry("app.properties", "config/app.properties"),
            ),
            settings.entries,
        )
        // legacy field cleared after migration
        assertTrue(settings.state.paths.isEmpty())
    }

    fun testEntriesGetterReturnsDefensiveCopy() {
        val settings = PropsViewSettings.getInstance(project)
        settings.entries = listOf(PathEntry("a", "a.properties"))
        val first = settings.entries
        settings.entries = listOf(PathEntry("a", "a.properties"), PathEntry("b", "b.properties"))
        assertEquals(1, first.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "io.github.armanayvazyan.propstableview.PropsViewSettingsTest"`
Expected: FAIL — `PathEntry` unresolved, `entries` unresolved.

- [ ] **Step 3: Rewrite `PropsViewSettings.kt`**

```kotlin
package io.github.armanayvazyan.propstableview

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Tag

/** A named, serializable reference to a .properties file (path relative to project base dir). */
@Tag("entry")
class PathEntry() {
    var name: String = ""
    var path: String = ""

    constructor(name: String, path: String) : this() {
        this.name = name
        this.path = path
    }

    override fun equals(other: Any?): Boolean =
        other is PathEntry && other.name == name && other.path == path

    override fun hashCode(): Int = 31 * name.hashCode() + path.hashCode()

    override fun toString(): String = "PathEntry(name=$name, path=$path)"
}

/**
 * Per-project storage for the named list of .properties files shown in the
 * Properties Table tool window. Legacy bare-path settings are migrated on load.
 */
@Service(Service.Level.PROJECT)
@State(name = "PropsViewSettings", storages = [Storage("propsTableView.xml")])
class PropsViewSettings : PersistentStateComponent<PropsViewSettings.State> {

    class State {
        var entries: MutableList<PathEntry> = mutableListOf()

        /** Legacy field from pre-name versions; migrated into [entries] on load, then cleared. */
        var paths: MutableList<String> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        if (myState.entries.isEmpty() && myState.paths.isNotEmpty()) {
            myState.entries = myState.paths
                .map { PathEntry(it.substringAfterLast('/'), it) }
                .toMutableList()
            myState.paths = mutableListOf()
        }
    }

    /** Named entries. Always returns/stores defensive copies. */
    var entries: List<PathEntry>
        get() = myState.entries.map { PathEntry(it.name, it.path) }
        set(value) {
            myState.entries = value.map { PathEntry(it.name, it.path) }.toMutableList()
        }

    /**
     * Transitional path-only view kept until [PropsTablePanel] migrates to [entries].
     * Setting it names each entry by its filename.
     */
    var paths: List<String>
        get() = myState.entries.map { it.path }
        set(value) {
            entries = value.map { PathEntry(it.substringAfterLast('/'), it) }
        }

    companion object {
        val CHANGED_TOPIC: Topic<Runnable> =
            Topic.create("Properties Table View settings changed", Runnable::class.java)

        fun getInstance(project: Project): PropsViewSettings = project.service()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "io.github.armanayvazyan.propstableview.PropsViewSettingsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Verify whole project still compiles**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL (panel/configurable still use the transitional `paths`).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/armanayvazyan/propstableview/PropsViewSettings.kt \
        src/test/kotlin/io/github/armanayvazyan/propstableview/PropsViewSettingsTest.kt
git commit -m "feat: named PathEntry model with legacy path migration"
```

---

### Task 2: `PropertiesScanner`

Discover `.properties` files across modules, filter out excluded/library/non-content files, name each by its module.

**Files:**
- Create: `src/main/kotlin/io/github/armanayvazyan/propstableview/PropertiesScanner.kt`
- Test: `src/test/kotlin/io/github/armanayvazyan/propstableview/PropertiesScannerTest.kt`

**Interfaces:**
- Consumes: `PathEntry` (Task 1).
- Produces: `object PropertiesScanner { fun scan(project: Project): List<PathEntry> }` — entries sorted by `name` then `path`; `path` relative to project base dir.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.armanayvazyan.propstableview

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesScannerTest : BasePlatformTestCase() {

    fun testScanFindsPropertiesFileInContent() {
        myFixture.addFileToProject("config.properties", "a=b")

        val found = PropertiesScanner.scan(project)

        assertTrue(
            "expected config.properties in $found",
            found.any { it.path == "config.properties" },
        )
        assertTrue(
            "scanned entries must have a non-blank name",
            found.first { it.path == "config.properties" }.name.isNotBlank(),
        )
    }

    fun testScanIgnoresNonPropertiesFiles() {
        myFixture.addFileToProject("notes.txt", "hello")

        val found = PropertiesScanner.scan(project)

        assertFalse(found.any { it.path.endsWith("notes.txt") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.armanayvazyan.propstableview.PropertiesScannerTest"`
Expected: FAIL — `PropertiesScanner` unresolved.

- [ ] **Step 3: Create `PropertiesScanner.kt`**

```kotlin
package io.github.armanayvazyan.propstableview

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.armanayvazyan.propstableview.PropertiesScannerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/armanayvazyan/propstableview/PropertiesScanner.kt \
        src/test/kotlin/io/github/armanayvazyan/propstableview/PropertiesScannerTest.kt
git commit -m "feat: PropertiesScanner discovers module .properties files"
```

---

### Task 3: Settings UI — onboarding + name/path table + Scan/Add/Remove

Rewrite `PropsViewConfigurable` to show onboarding text, an editable name/path table, and Add/Scan/Remove actions operating on `entries`.

**Files:**
- Modify: `src/main/kotlin/io/github/armanayvazyan/propstableview/PropsViewConfigurable.kt`

**Interfaces:**
- Consumes: `PathEntry`, `PropsViewSettings.entries` (Task 1); `PropertiesScanner.scan` (Task 2); existing `PropsViewSettings.CHANGED_TOPIC`.
- Produces: no new public symbols (UI only).

- [ ] **Step 1: Rewrite `PropsViewConfigurable.kt`**

```kotlin
package io.github.armanayvazyan.propstableview

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
 * Settings page under Settings | Tools | Properties Table View.
 * Onboarding text plus an editable Name/Path table of the configured files.
 */
class PropsViewConfigurable(private val project: Project) : Configurable {

    private val model = EntriesTableModel(emptyList())
    private val table = JBTable(model)
    private var panel: JComponent? = null

    override fun getDisplayName(): String = "Properties Table View"

    override fun createComponent(): JComponent {
        table.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.emptyText.text = "No properties files configured — click Scan or +"
        table.columnModel.getColumn(0).preferredWidth = 160
        table.columnModel.getColumn(1).preferredWidth = 420

        val header = JBLabel(
            "<html><body style='width:480px'>" +
                "Choose which <b>.properties</b> files appear in the Properties Table tool window. " +
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
        val discovered = PropertiesScanner.scan(project).filter { it.path !in existing }
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
        model.items() != PropsViewSettings.getInstance(project).entries

    override fun apply() {
        val settings = PropsViewSettings.getInstance(project)
        settings.entries = model.items()
        project.messageBus.syncPublisher(PropsViewSettings.CHANGED_TOPIC).run()
    }

    override fun reset() {
        model.replaceAll(PropsViewSettings.getInstance(project).entries)
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
```

- [ ] **Step 2: Verify compile + existing tests pass**

Run: `./gradlew compileKotlin && ./gradlew test --tests "io.github.armanayvazyan.propstableview.*"`
Expected: BUILD SUCCESSFUL; all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/armanayvazyan/propstableview/PropsViewConfigurable.kt
git commit -m "feat: onboarding + named/scannable settings table"
```

---

### Task 4: Tool window shows names; drop transitional `paths`

Render combo by entry name, resolve by path, then remove the transitional `paths` accessor from `PropsViewSettings`.

**Files:**
- Modify: `src/main/kotlin/io/github/armanayvazyan/propstableview/PropsTablePanel.kt`
- Modify: `src/main/kotlin/io/github/armanayvazyan/propstableview/PropsViewSettings.kt`

**Interfaces:**
- Consumes: `PathEntry`, `PropsViewSettings.entries`.
- Produces: none (UI + cleanup).

- [ ] **Step 1: Update the combo section of `PropsTablePanel.kt`**

Change the field declarations (lines ~29-30) from:

```kotlin
    private val comboModel = DefaultComboBoxModel<String>()
    private val combo = ComboBox(comboModel)
```

to:

```kotlin
    private val comboModel = DefaultComboBoxModel<PathEntry>()
    private val combo = ComboBox(comboModel)
```

Add this import alongside the existing `com.intellij.ui.*` imports:

```kotlin
import com.intellij.ui.SimpleListCellRenderer
```

In `init`, immediately after `combo.addActionListener { ... }`, add:

```kotlin
        combo.renderer = SimpleListCellRenderer.create("") { it.name.ifBlank { it.path } }
```

Replace `refreshAll()` with:

```kotlin
    /** Repopulates the combo from settings, keeping the current selection if still present. */
    private fun refreshAll() {
        val previousPath = (combo.selectedItem as? PathEntry)?.path
        val entries = PropsViewSettings.getInstance(project).entries
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
            statusLabel.text = "No paths configured. Add them in Settings | Tools | Properties Table View."
        } else {
            reloadSelectedFile()
        }
    }
```

Replace `currentPath()`:

```kotlin
    private fun currentPath(): String? = (combo.selectedItem as? PathEntry)?.path
```

- [ ] **Step 2: Remove the transitional `paths` accessor from `PropsViewSettings.kt`**

Delete this block (added in Task 1):

```kotlin
    /**
     * Transitional path-only view kept until [PropsTablePanel] migrates to [entries].
     * Setting it names each entry by its filename.
     */
    var paths: List<String>
        get() = myState.entries.map { it.path }
        set(value) {
            entries = value.map { PathEntry(it.substringAfterLast('/'), it) }
        }
```

- [ ] **Step 3: Verify compile + all tests pass**

Run: `./gradlew compileKotlin && ./gradlew test`
Expected: BUILD SUCCESSFUL; all tests PASS. No remaining references to `settings.paths`.

- [ ] **Step 4: Sanity-check no stray `paths` usages**

Run: `grep -rn "\.paths" src/main src/test`
Expected: no output (only `entries` is used now).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/armanayvazyan/propstableview/PropsTablePanel.kt \
        src/main/kotlin/io/github/armanayvazyan/propstableview/PropsViewSettings.kt
git commit -m "feat: tool window selector shows entry names; drop legacy paths accessor"
```

---

### Task 5: Manual verification in sandbox IDE

Confirm the feature end-to-end in a real IDE instance.

**Files:** none.

- [ ] **Step 1: Launch the sandbox IDE**

Run: `./gradlew runIde`

- [ ] **Step 2: Verify onboarding + scan**

In the sandbox IDE, open a project containing `.properties` files. Go to
Settings | Tools | Properties Table View. Confirm:
- onboarding text is visible,
- **Scan** populates the table with rows named by module, paths filled,
- editing a **Name** cell sticks after Apply,
- **+** adds a manually chosen file,
- **Remove** deletes the selected row.

- [ ] **Step 3: Verify tool window**

Open the Properties Table tool window. Confirm the combo shows the **names**
(e.g. `cloud-e2e`, `cloud-e2e-gh`), selecting one loads its key/value table.

- [ ] **Step 4: Verify migration**

Add a legacy `propsTableView.xml` with a `paths` list (or open a project saved
by the old version), reopen settings, and confirm the paths appear as entries
named by filename.

---

## Notes for the implementer

- `BasePlatformTestCase` tests are JUnit3-style: method names start with `test`, no annotations.
- `FilenameIndex.getAllFilesByExt` requires the `com.intellij.properties` dependency, already declared in `plugin.xml`.
- All scanner/bridge reads run inside `ReadAction`; do not call them off the EDT without it.
