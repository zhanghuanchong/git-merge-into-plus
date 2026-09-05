# AGENTS.md

JetBrains plugin project: merge current branch into a user-selected target branch while staying on
the current branch. Written in **Kotlin**.

## Build & verify

- Use the Gradle wrapper: `./gradlew` (Gradle 9.7).
- Build uses JDK 21 (fixed in `gradle.properties` via `org.gradle.java.home`) — do not change.
- `./gradlew build` — compile + test + package; artifact at `build/distributions/*.zip`.
- `./gradlew test` — headless platform tests (fast, run after changes).
- `./gradlew buildPlugin` — package only.
- `./gradlew runIde` — launch a test IDE instance with the plugin (sandbox at
  `.intellijPlatform/sandbox/`; **not** `build/idea-sandbox`, which is stale garbage).
- `./gradlew verifyPlugin` — Plugin Verifier compatibility check; SLOW (downloads multiple IDE
  builds) — only needed before publishing.
- First run downloads the IntelliJ SDK (~1.5 GB) — slow, expect it.
- Do NOT pipe Gradle output through `tail`/`head` when users need to see download progress/errors.

## Project layout

- `src/main/kotlin/com/hans/gitmergeintoplus/` — plugin sources (Kotlin, IntelliJ Platform SDK).
  - `MergeIntoAction.kt` — the `Git.Menu` action entry point.
  - `dialog/MergeIntoDialog.kt` — searchable branch picker with favorites.
  - `git/GitMergeRunner.kt` — the checkout → merge → checkout-back logic.
  - `settings/FavoritesManager.kt` — persisted favorites / last-target (project service).
  - `ui/PluginNotifications.kt` — notification helpers.
- `src/test/kotlin/` — headless `BasePlatformTestCase` tests (proves services/plugin.xml load).
- `src/main/resources/META-INF/plugin.xml` — plugin descriptor.

## Platform constraints (important)

- Target platform: IntelliJ 2026.2 (`intellijIdea("2026.2")`); `since-build=261.0`, `until-build=263.*`.
- **plugin.xml gotcha:** in 2026.x the descriptor parser rejects `<projectService>`/`<notification-group>`
  as direct `<idea-plugin>` children ("Unknown element: projectService"). They MUST live inside
  `<extensions defaultExtensionNs="com.intellij">`. Only `<actions>` stays a top-level element.
- VCS/Git compile deps are NOT exposed by default: add `bundledModule("intellij.platform.vcs.dvcs")`
  (and the other `intellij.platform.vcs.*` / `intellij.vcs.git.shared` modules) + `bundledPlugin("Git4Idea")`.
- The 2026.x Git plugin was refactored (RPC backend). Prefer the **stable low-level API**:
  `GitLineHandler` + `Git.getInstance().runCommand(...)` + `GitCommandResult`, and
  `GitRepositoryManager` / `GitRepository` / `GitBranchUtil`.
- Kotlin: Java getters with args are NOT property-mapped (`getBranchTrackInfo(x)` stays a function
  call; only no-arg getters like `getRoot()` become `.root`).
- Run git operations on a background thread (`Task.Backgroundable`); keep the UI dialog on the EDT.
- The action must override `getActionUpdateThread()` returning `ActionUpdateThread.BGT`.
- `GitLineHandler` always needs a `-m` message for merge commits so no editor is opened.

## Conventions

- No code comments unless the task explicitly asks for them.
- Keep the plugin dependency-light: only the IntelliJ platform + Git4Idea APIs.
- Author / Vendor is **Hans Zhang**.
- Upon completing any feature upgrade or bug fix:
  - Bump the version number in `build.gradle.kts` and `src/main/resources/META-INF/plugin.xml`.
  - Update Overview (`<description>`) if features changed, and What's New (`<change-notes>`) in `src/main/resources/META-INF/plugin.xml`.
  - Re-package via `./gradlew buildPlugin`.
- Verify with `./gradlew test` (or at least `./gradlew compileKotlin`) after edits before reporting done.
