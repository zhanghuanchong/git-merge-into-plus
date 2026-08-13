# AGENTS.md

JetBrains plugin project: merge current branch into a user-selected target branch while staying on
the current branch.

## Build & verify

- Build uses JDK 21 (fixed in `gradle.properties` via `org.gradle.java.home`) — do not change.
- `gradle verifyPlugin` — validate `plugin.xml` and plugin configuration.
- `gradle build` — compile and package; artifact at `build/distributions/*.zip`.
- `gradle runIde` — launch a test IDE instance with the plugin.
- First run downloads the IntelliJ SDK (~1.5 GB) — slow, expect it.
- Do NOT pipe Gradle output through `tail`/`head` when users need to see download progress/errors.

## Project layout

- `src/main/java/com/hans/gitmergeintoplus/` — plugin sources (Java, IntelliJ Platform SDK).
  - `MergeIntoAction.java` — the `Git.Menu` action entry point.
  - `dialog/MergeIntoDialog.java` — searchable branch picker with favorites.
  - `git/GitMergeRunner.java` — the checkout → merge → checkout-back logic.
  - `settings/FavoritesManager.java` — persisted favorites / last-target (project service).
- `src/main/resources/META-INF/plugin.xml` — action registration, Git menu group `Git.Menu`,
  keyboard shortcuts (`$default` + `Mac OS X` keymaps), dependencies (`Git4Idea`, `com.intellij.modules.vcs`).

## Platform constraints (important)

- Target platform: IntelliJ 2026.2; `since-build=261.0`, `until-build=263.*`.
- The 2026.x Git plugin was refactored (RPC backend). Prefer the **stable low-level API**:
  `GitLineHandler` + `Git.getInstance().runCommand(...)` + `GitCommandResult`, and
  `GitRepositoryManager` / `GitRepository` / `GitBranchUtil`.
- Run git operations on a background thread (`Task.Backgroundable`); keep the UI dialog on the EDT.
- The action must override `getActionUpdateThread()` returning `ActionUpdateThread.BGT`.
- `GitLineHandler` always needs a `-m` message for merge commits so no editor is opened.

## Conventions

- No code comments unless the task explicitly asks for them.
- Keep the plugin dependency-light: only the IntelliJ platform + Git4Idea APIs.
- Verify compilation after edits (`gradle compileJava`) before reporting done.
