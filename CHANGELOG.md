# Changelog
All notable changes to **Git Merge Into Plus** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.9] - 2026-09-05
### Added
- **Multi-Target Branch Merging**: Support selecting multiple target branches simultaneously (`Cmd/Ctrl`+click or `Shift`+click) to merge the current branch into all chosen branches sequentially in one run.
- **Fail-Safe Sequential Execution**: If any target branch fails or encounters conflicts during multi-merge, the operation safely aborts the conflict, reports partial completion, skips remaining branches, and always switches back to the original starting branch.
- **Adaptive Action Button & Preview**: Dialog OK button dynamically adapts to the selection (e.g. `Merge into 'dev'`, `Merge into 3 branches`), with multi-branch preview summary.

## [0.1.8] - 2026-09-05
### Added
- **VCS Operations Popup Integration**: Added `Merge Into...` directly to the IDE's built-in VCS Operations Popup (`Alt + \`` on Windows/Linux, `Ctrl + V` on macOS) for quick keyboard-driven access.
- Expanded access points documentation and shortcuts table in `README.md`.

## [0.1.7] - 2026-09-05
### Added
- **Enhanced Favorite Star Contrast**: Replaced Unicode character stars with custom anti-aliased Java2D vector stars (`StarIcon`). Favorited branches are rendered in bright golden yellow (`#F5A623`), while unfavorited stars display delicate faint outlines. Added interactive hand cursor when hovering over the star column.
### Changed
- **Remote Sync Enabled by Default & Instant Persistence**: Enabled `Update target branch from remote before merging` by default. Checkbox toggles now persist immediately without requiring dialog submission.

## [0.1.6] - 2026-09-05
### Added
- **Pre-Merge Divergence Intelligence**: Real-time divergence inspection calculating incoming commits to merge (ahead), divergence metrics (behind), fast-forward viability, and an unambiguous *Up to date* badge.
- **Pre-Merge Remote Target Synchronization**: Added option to fast-forward the target branch from its remote tracking branch (`git pull --ff-only`) before merging.

## [0.1.5] - 2026-09-05
### Added
- **Custom Merge Commit Message**: Added optional commit message input field activated when `--no-ff` is checked, supporting team-specific commit formats with automatic fallback to standard Git messages.
- **Branch Counter & Fast Filter Badge**: Live counter in search bar showing total branch count, favorite star count, and dynamic filtered matches.

## [0.1.4] - 2026-09-05
### Added
- **Official Plugin Branding**: Added official SVG plugin icons (`pluginIcon.svg` and `pluginIcon_dark.svg`) for light and dark IDE themes in JetBrains Marketplace and Plugin Manager.

## [0.1.3] - 2026-09-05
### Added
- **Dedicated Action Icon**: Added official Git merge icon (`AllIcons.Vcs.Merge`) to `Git → Merge Into...` menu entries.
- **Target Branch Commit Preview**: Added real-time preview of the latest commit (hash, author, date, subject) on any selected target branch.

## [0.1.2] - 2026-09-05
### Added
- **Dynamic Plugin (Hot-Swapping) Support**: Converted project service and notification group registrations to IntelliJ dynamic plugin extensions, allowing install, update, and unload without IDE restarts.
- **Dynamic Plugin Verification Test**: Added automated platform test validating dynamic unloadability.
### Changed
- Migrated notifications to modern `NotificationGroupManager` API.

## [0.1.1] - 2026-09-05
### Added
- **Persistent Merge Options**: Automatically saves and restores `--no-ff` and `push` preferences.
### Fixed
- Fixed an issue where the initially selected target branch could not be confirmed without switching branches first.
### Changed
- Updated author metadata to Hans Zhang.

## [0.1.0] - 2026-09-05
### Added
- Initial release.
- Core checkout → merge → checkout-back engine.
- Searchable branch list dialog with pinned favorites.
