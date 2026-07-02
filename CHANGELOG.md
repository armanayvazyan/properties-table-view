# Changelog

All notable changes to Propsy are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Support for `.env` files in the Key/Value tool window (requires the
  ".env files support" plugin, `ru.adelf.idea.dotenv`). Edits preserve
  comments, blank lines and key order like `.properties`.

### Changed

- Lowered the minimum IDE to 2025.2 (`sinceBuild = 252`).
- Reworked the Propsy settings page: clearer description, a `.env` support
  status row with a one-click install shortcut when the plugin is missing, and
  a download icon for the Scan action.

## [0.1.3]

### Changed

- Renamed the project to Propsy.

### Added

- Open-source project files and automatic changelog generation.

## [0.1.2]

### Added

- Onboarding flow and a named, scannable settings table.
- `PropertiesScanner` to discover `.properties` files via the filename index,
  naming each entry after its owning module.
- Plugin icons.

### Changed

- Renamed the project to **Propsy**.
- Tool window selector now shows entry names.
- Settings table holds named `PathEntry(name, path)` items; the legacy bare
  `paths` field is migrated on load.

## [0.1.1]

### Added

- Per-project configuration of tracked `.properties` files.

## [0.1.0]

### Added

- Initial release: render configured `.properties` files as an editable
  Key/Value table in a bottom tool window. Edits write through the bundled
  Properties PSI, preserving comments, blank lines, and key order, with full
  undo support.

[Unreleased]: https://github.com/armanayvazyan/propsy/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/armanayvazyan/propsy/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/armanayvazyan/propsy/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/armanayvazyan/propsy/releases/tag/v0.1.0
