# Git Merge Into Plus

A JetBrains plugin that merges the **current branch** into a **target branch of your choice** and
automatically returns to the current branch when the operation finishes.

It combines the best of two existing plugins:

- [Git Merge Into](https://plugins.jetbrains.com/plugin/30138-git-merge-into) — merge without
  branch selection (only a default) — lacks choice.
- [Merge To Target Branch](https://plugins.jetbrains.com/plugin/25829-merge-to-target-branch) —
  branch selection, but **no search** and **no favorites**.

## Features

- **Searchable branch picker** — great for repos with many branches.
- **Favorites** — star frequently used branches (right-click a branch or click its star);
  favorites are pinned to the top of the list.
- **Remembers the last target** branch per repository.
- **Stays on the current branch** — after merging, the plugin always checks the original branch
  back out, even when something fails (conflicts abort the merge and you're returned safely).
- **Optional `--no-ff` merge commit** (default on) and **optional push** of the target branch.
- **Multi-root projects** — choose which repository to operate on when several are mapped.
- Lives under the **Git** menu and has a default keyboard shortcut.

## Default shortcut

| Keymap            | Shortcut           |
|-------------------|--------------------|
| Default (Win/Lin) | `Ctrl + Alt + Shift + M` |
| macOS             | `Cmd + Alt + Shift + M`   |

Rebind it anytime in **Settings → Keymap**.

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

1. Be on the branch you want to merge **from** (e.g. a feature branch).
2. Run **Git → Merge Into...** (or the keyboard shortcut).
3. Pick the target branch (search or pick from favorites).
4. Optionally uncheck *Create a merge commit* or check *Push target branch*.
5. Click **OK**. The plugin checks out the target, merges, pushes if requested, and returns to
   your original branch.

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
