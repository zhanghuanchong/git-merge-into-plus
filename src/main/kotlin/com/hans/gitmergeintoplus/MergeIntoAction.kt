package com.hans.gitmergeintoplus

import com.hans.gitmergeintoplus.dialog.MergeIntoDialog
import com.hans.gitmergeintoplus.git.GitMergeRunner
import com.hans.gitmergeintoplus.settings.FavoritesManager
import com.hans.gitmergeintoplus.ui.PluginNotifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class MergeIntoAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
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

        val dialog = MergeIntoDialog(project, repositories, defaultRepository)
        if (!dialog.showAndGet()) {
            return
        }

        val repository = dialog.getRepository() ?: return
        val currentBranch = dialog.getCurrentBranchName() ?: return
        val targetBranch = dialog.getSelectedBranch() ?: return

        FavoritesManager.getInstance(project).setLastTarget(repository.root.path, targetBranch)

        GitMergeRunner.run(project, repository, currentBranch, targetBranch,
            dialog.isNoFF(), dialog.isPushAfterMerge())
    }

    private fun resolveDefaultRepository(
        project: Project,
        dataContext: DataContext,
        repositories: List<GitRepository>,
    ): GitRepository {
        try {
            GitBranchUtil.guessRepositoryForOperation(project, dataContext)?.let { return it }
        } catch (_: Exception) {
            // fall through to the first on-branch repository
        }
        repositories.firstOrNull { it.isOnBranch }?.let { return it }
        return repositories.first()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
