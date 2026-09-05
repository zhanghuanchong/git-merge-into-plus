package com.hans.gitmergeintoplus

import com.hans.gitmergeintoplus.dialog.MergeIntoDialog
import com.hans.gitmergeintoplus.git.GitMergeRunner
import com.hans.gitmergeintoplus.settings.FavoritesManager
import com.hans.gitmergeintoplus.ui.PluginNotifications
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import git4idea.GitReference
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class MergeIntoAction : AnAction() {

    init {
        templatePresentation.icon = AllIcons.Vcs.Merge
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDisposed) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val defaultRepository = resolveDefaultRepository(project, e.dataContext, repositories)
        val currentBranch = defaultRepository.currentBranch?.name
        if (currentBranch == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val targetBranchName = getSelectedTargetBranchName(e)
        if (targetBranchName != null) {
            val resolvedLocalBranch = resolveLocalTargetBranch(defaultRepository, targetBranchName)
            if (resolvedLocalBranch == null || resolvedLocalBranch == currentBranch) {
                e.presentation.isEnabledAndVisible = false
                return
            }
            e.presentation.text = "Merge '$currentBranch' into '$resolvedLocalBranch' (Plus)..."
        } else {
            e.presentation.text = "Merge Into..."
        }

        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDisposed) {
            return
        }

        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) {
            PluginNotifications.warning(project, "No Git repository",
                "No Git repositories are mapped for this project.")
            return
        }

        val defaultRepository = resolveDefaultRepository(project, e.dataContext, repositories)
        val targetBranchName = getSelectedTargetBranchName(e)
        val preselectedTarget = if (targetBranchName != null) {
            resolveLocalTargetBranch(defaultRepository, targetBranchName)
        } else {
            null
        }

        val dialog = MergeIntoDialog(project, repositories, defaultRepository, preselectedTarget)
        if (!dialog.showAndGet()) {
            return
        }

        val repository = dialog.getRepository() ?: return
        val currentBranch = dialog.getCurrentBranchName() ?: return
        val targetBranches = dialog.getSelectedBranches()
        if (targetBranches.isEmpty()) {
            return
        }

        val favManager = FavoritesManager.getInstance(project)
        favManager.setLastTarget(repository.root.path, targetBranches.first())
        favManager.setNoFF(dialog.isNoFF())
        favManager.setPushAfterMerge(dialog.isPushAfterMerge())
        favManager.setPullBeforeMerge(dialog.isPullBeforeMerge())

        GitMergeRunner.run(
            project, repository, currentBranch, targetBranches,
            dialog.isNoFF(), dialog.isPushAfterMerge(), dialog.isPullBeforeMerge(), dialog.getCustomCommitMessage()
        )
    }

    internal fun resolveDefaultRepository(
        project: Project,
        dataContext: DataContext,
        repositories: List<GitRepository>,
    ): GitRepository {
        try {
            dataContext.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY)?.let { return it }
        } catch (_: Throwable) {
        }
        try {
            GitBranchUtil.guessRepositoryForOperation(project, dataContext)?.let { return it }
        } catch (_: Throwable) {
        }
        repositories.firstOrNull { it.isOnBranch }?.let { return it }
        return repositories.first()
    }

    internal fun getSelectedTargetBranchName(e: AnActionEvent): String? {
        val ref = e.getData(DataKey.create<Any>("Git.Selected.Ref")) ?: return null
        return when (ref) {
            is GitReference -> ref.name
            else -> {
                try {
                    val m = ref.javaClass.getMethod("getName")
                    m.invoke(ref) as? String
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    internal fun resolveLocalTargetBranch(repository: GitRepository, rawTargetName: String): String? {
        val localBranches = repository.branches.localBranches.map { it.name }
        if (localBranches.contains(rawTargetName)) {
            return rawTargetName
        }
        val stripped = rawTargetName.substringAfter('/')
        if (localBranches.contains(stripped)) {
            return stripped
        }
        return null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
