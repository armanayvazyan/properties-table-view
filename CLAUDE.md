# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An IntelliJ Platform plugin (Kotlin) that renders configured `.properties` files as an editable Key/Value table in a bottom tool window. Edits write straight to disk through the bundled Properties PSI, preserving comments, blank lines, and key order, with full undo support.

## Commands

```bash
./gradlew test          # run all tests
./gradlew buildPlugin    # build installable zip → build/distributions/
./gradlew runIde         # launch a sandbox IDE with the plugin loaded
./gradlew verifyPlugin    # plugin-structure verification (also run in CI)
```

Run a single test class:

```bash
./gradlew test --tests "io.github.armanayvazyan.propstableview.PropertiesFileBridgeTest"
```

- JDK 21 required (Kotlin + Java toolchain pinned to 21).
- First build needs internet (Gradle downloads the IntelliJ Platform `2026.1`).
- CI (`.github/workflows/build.yml`) runs test → verifyPlugin → buildPlugin on push/PR to `main`. Release (`release.yml`) triggers on `v*` tags: signs the zip, attaches to GitHub Release, publishes to JetBrains Marketplace. Signing/publishing read env vars (`CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`).

## Architecture

All code lives in `src/main/kotlin/io/github/armanayvazyan/propstableview/`. Data flows in one direction: settings → tool window → PSI bridge → disk.

- **`PropsViewSettings`** — `@Service(PROJECT)` `PersistentStateComponent`, stored in `propsTableView.xml`. Holds a list of `PathEntry(name, path)` (path relative to project base dir). Migrates the legacy bare-`paths` field into named `entries` on load. Exposes `CHANGED_TOPIC` (message-bus `Topic`) that the settings page publishes and the tool window subscribes to — this is how config edits push a refresh into the open table.
- **`PropsViewConfigurable`** — the Settings | Tools | Properties Table View page. Editable Name/Path table (Name editable, Path read-only). `+` opens a file chooser; **Scan** delegates to `PropertiesScanner`. On `apply()` it writes settings and fires `CHANGED_TOPIC`.
- **`PropertiesScanner`** — discovers `.properties` files via `FilenameIndex.getAllFilesByExt`, filtered to in-content, non-excluded, non-library files; names each `PathEntry` after its owning module.
- **`PropsTableToolWindowFactory`** / **`PropsTablePanel`** — the bottom tool window: a `ComboBox<PathEntry>` picker over a `JBTable`. `refreshAll()` rebuilds the combo from settings (guarded by `suppressComboEvents` so the action listener doesn't fire mid-rebuild) and reloads the selected file.
- **`PropsTableModel`** — `AbstractTableModel` over one `PropertiesFile`. Col 0 = key, col 1 = value. Cell edits write through the bridge, then `load()` re-reads so cached key/value stay consistent with the PSI.
- **`PropertiesFileBridge`** — the only place that touches PSI. `resolve()` and `entries()` run in `ReadAction`; all mutations run in `WriteCommandAction` (named, undoable). `addEntry` appends after the last property (not alphabetically) to preserve key order and rejects duplicate keys.

`plugin.xml` declares `<depends>com.intellij.properties</depends>` — the Properties PSI API (`PropertiesFile`, `IProperty`, `Property`) comes from that bundled plugin, declared in `build.gradle.kts` as `bundledPlugin("com.intellij.properties")`.

## Conventions / gotchas

- Package is `io.github.armanayvazyan.propstableview`; plugin id `io.github.armanayvazyan.propstableview`. (The README's "Project layout" section still shows a stale `com.example.propstableview` path and `2024.2` minimum — the actual platform is `2026.1` / `sinceBuild = 261`.)
- All PSI access must be wrapped in `ReadAction`/`WriteCommandAction` — follow `PropertiesFileBridge` rather than calling PSI directly elsewhere.
- `PathEntry` is a serializable mutable bean (`@Tag("entry")`) with value `equals`/`hashCode`; settings getters/setters hand out defensive copies, so compare by value, not reference.
- Tests extend `BasePlatformTestCase` and use `myFixture.configureByText(...)`.
