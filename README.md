# Git Merge Into Plus

A JetBrains plugin that merges the **current branch** into a **target branch of your choice** and
automatically returns to the current branch when the operation finishes.

It combines the best of two existing plugins:

- [Git Merge Into](https://plugins.jetbrains.com/plugin/30138-git-merge-into) — merge without
  branch selection (only a default) — lacks choice.
- [Merge To Target Branch](https://plugins.jetbrains.com/plugin/25829-merge-to-target-branch) —
  branch selection, but **no search** and **no favorites**.

## Features

- **Git Branches popup & context menu integration** — trigger directly from the IDE status bar branch widget, the main navigation toolbar Git widget, or right-click any branch in the branch popup / Git tool window to merge with automatic target pre-selection.
- **Multi-target branch merge** — select multiple target branches simultaneously (`Cmd/Ctrl`+Click or `Shift`+Click) to merge into multiple target branches sequentially in one operation with automatic rollback if any branch encounters conflicts.
- **Searchable branch picker & real-time counter** — live badge displaying total, favorite, and filtered branch counts.
- **Favorites** — star frequently used branches with radiant golden stars (right-click or click star); favorites are pinned to the top of the list, and unstarred branches feature subtle faint outlines.
- **Pre-merge divergence intelligence** — real-time calculation of incoming commits to merge (ahead), divergence metrics (behind), fast-forward checks, and an unambiguous *Up to date* badge.
- **Target branch commit preview** — inspect the latest commit (hash, author, date, message) before merging.
- **Pre-merge remote synchronization** — option to automatically update the target branch from its remote tracking branch (`git pull --ff-only`) before merging (enabled by default, with instant preference memory).
- **Custom merge commit message** — optionally provide custom commit messages with `--no-ff`, falling back to Git standard message if blank.
- **Remembers the last target** branch per repository.
- **Stays on the current branch** — after merging, the plugin always checks the original branch
  back out, even when something fails (conflicts abort the merge and you're returned safely).
- **Optional `--no-ff` merge commit** (default on), **remote sync** (default on), and **optional push** of the target branch.
- **Multi-root projects** — choose which repository to operate on when several are mapped.
- **Integrated in VCS Operations Popup** (`Alt + \`` / `Ctrl + V`) as well as the **Git menu** with dedicated merge icon and keyboard shortcut.

## Access Points & Shortcuts

You can invoke **Merge Into...** using any of the following convenient methods:

| Access Method | Shortcut / Path | Note |
|---|---|---|
| **Git Branches Popup** | Click branch widget in Status Bar / Toolbar → `Merge Into...` | Listed right at the top alongside *New Branch...* |
| **Branch Context Menu** | Click/Right-click branch in Git Popup / Tool Window → `Merge '<current>' into '<target>' (Plus)...` | Pre-selects the chosen target branch automatically |
| **VCS Operations Popup** | `Ctrl + V` (macOS) / `Alt + \`` (Win/Linux) | Injected into the IDE's built-in quick list popup |
| **Dedicated Shortcut** | `Cmd + Alt + Shift + M` (macOS) / `Ctrl + Alt + Shift + M` (Win/Linux) | Rebindable in **Settings → Keymap** |
| **Git Main Menu** | `Git → Merge Into...` | Displays official Git merge icon |
| **Context Menu** | Right-click in Editor or Project View → `Git → Merge Into...` | Fast contextual access |

## Installation

1. Build the plugin:

   ```bash
   ./gradlew buildPlugin
   ```

2. Install `build/distributions/git-merge-into-plus-*.zip` via
   **Settings → Plugins → ⚙️ → Install Plugin from Disk...**

Or run it directly in a test IDE:

```bash
./gradlew runIde
```

## Usage

1. Be on the branch you want to merge **from** (e.g., your feature branch `feat/login`).
2. Invoke **Merge Into...** via:
   - **Git Branches Popup** (click branch name in status bar or main toolbar → `Merge Into...`)
   - **Branch Actions Menu** (click/right-click a branch in Git popup or tool window → `Merge Current into '...' (Plus)...`)
   - **VCS Operations Popup** (`Ctrl + V` / `Alt + \``)
   - **Dedicated Shortcut** (`Cmd + Alt + Shift + M` / `Ctrl + Alt + Shift + M`)
   - **Git Menu** (`Git → Merge Into...`)
3. Pick one or more target branches (search, click a starred favorite, or `Cmd`/`Ctrl`+click / `Shift`+click to select multiple).
4. Review the divergence preview (*ahead* / *behind* commits) and recent commit info (or multi-target summary when multiple branches are selected).
5. Configure your options (remembered automatically):
   - **Update target branch from remote before merging**: Fast-forwards the target branch from its remote tracking branch prior to merging.
   - **Create a merge commit (`--no-ff`)**: Check this to create a merge commit, and optionally enter a custom commit message.
   - **Push target branch after merge**: Pushes the merged target branch to its remote.
6. Click **Merge into ...**. The plugin switches to each target branch in sequence, pulls remote updates (if enabled), merges, pushes (if enabled), and automatically returns you safely to your original branch.

> Tip: the first `gradle` run downloads the IntelliJ SDK (~1.5 GB) — be patient.

## Requirements

- IntelliJ Platform 2026.1+
- The bundled **Git** plugin (Git4Idea)

## Development

```bash
./gradlew verifyPlugin   # validate plugin.xml
./gradlew build          # compile + test + package
./gradlew runIde         # launch a test IDE with this plugin
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
Copyright © 2026 Hans Zhang.

