package com.hans.gitmergeintoplus.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.hans.gitmergeintoplus.ui.PluginNotifications
import git4idea.branch.GitBranchUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

object GitMergeRunner {

    private val LOG = Logger.getInstance(GitMergeRunner::class.java)

    fun resolveCommitMessage(currentBranch: String, targetBranch: String, customMessage: String?): String {
        return customMessage?.trim()?.takeIf { it.isNotEmpty() } ?: "Merge branch '$currentBranch' into $targetBranch"
    }

    fun run(
        project: Project,
        repository: GitRepository,
        currentBranch: String,
        targetBranch: String,
        noFF: Boolean,
        push: Boolean,
        pullBeforeMerge: Boolean = false,
        customCommitMessage: String? = null,
    ) {
        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, "Merge '$currentBranch' into '$targetBranch'", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Preparing merge..."
                perform(project, repository, currentBranch, targetBranch, noFF, push, pullBeforeMerge, customCommitMessage, indicator)
            }
        })
    }

    private fun perform(
        project: Project,
        repository: GitRepository,
        currentBranch: String,
        targetBranch: String,
        noFF: Boolean,
        push: Boolean,
        pullBeforeMerge: Boolean,
        customCommitMessage: String?,
        indicator: ProgressIndicator,
    ) {
        val root = repository.root

        val dirty = isWorkingTreeDirty(project, root)
        if (dirty && !confirmProceedWithUncommittedChanges(project)) {
            PluginNotifications.info(
                project, "Merge cancelled",
                "Merge into '$targetBranch' was cancelled due to uncommitted changes."
            )
            return
        }

        val checkout = runCommand(project, root, GitCommand.CHECKOUT, targetBranch)
        if (!checkout.success()) {
            PluginNotifications.error(
                project, "Checkout failed",
                "Could not switch to branch '$targetBranch'.\n\n" + errorText(checkout)
            )
            return
        }

        if (pullBeforeMerge) {
            val remote = resolveRemote(repository, targetBranch)
            if (remote != null) {
                indicator.text = "Updating '$targetBranch' from '$remote'..."
                val trackInfo = repository.getBranchTrackInfo(targetBranch)
                val remoteBranch = trackInfo?.remoteBranch?.nameForRemoteOperations ?: targetBranch
                val pullResult = runCommand(project, root, GitCommand.PULL, "--ff-only", remote, remoteBranch)
                if (!pullResult.success()) {
                    PluginNotifications.error(
                        project, "Update failed",
                        "Could not fast-forward '$targetBranch' from '$remote/$remoteBranch'.\n\n" +
                            errorText(pullResult)
                    )
                    return
                }
            } else {
                PluginNotifications.warning(
                    project, "Update skipped",
                    "No remote found for branch '$targetBranch' — update was skipped."
                )
            }
        }

        var merged = false
        try {
            indicator.text = "Merging '$currentBranch' into '$targetBranch'..."
            val mergeArgs = buildList {
                if (noFF) add("--no-ff")
                add("-m")
                add(resolveCommitMessage(currentBranch, targetBranch, customCommitMessage))
                add(currentBranch)
            }
            val merge = runCommand(project, root, GitCommand.MERGE, mergeArgs)

            if (!merge.success()) {
                if (isMergeConflict(merge)) {
                    val abort = runCommand(project, root, GitCommand.MERGE, "--abort")
                    PluginNotifications.error(
                        project, "Merge conflicts",
                        "Merging '$currentBranch' into '$targetBranch' caused conflicts and was aborted.\n\n" +
                            "Conflicts detected:\n" + conflictText(merge) +
                            if (abort.success()) "" else "\n\n(merge --abort also failed: " + errorText(abort) + ")"
                    )
                } else {
                    PluginNotifications.error(
                        project, "Merge failed",
                        "Merging '$currentBranch' into '$targetBranch' failed.\n\n" + errorText(merge)
                    )
                }
                return
            }
            merged = true

            if (push) {
                val remote = resolveRemote(repository, targetBranch)
                if (remote == null) {
                    PluginNotifications.warning(
                        project, "Push skipped",
                        "No remote found for branch '$targetBranch' — push was skipped."
                    )
                } else {
                    indicator.text = "Pushing '$targetBranch' to '$remote'..."
                    val pushResult = runCommand(project, root, GitCommand.PUSH, remote, targetBranch)
                    if (!pushResult.success()) {
                        PluginNotifications.warning(
                            project, "Push failed",
                            "Push of '$targetBranch' to '$remote' failed.\n\n" + errorText(pushResult)
                        )
                    }
                }
            }
        } finally {
            indicator.text = "Returning to '$currentBranch'..."
            val back = runCommand(project, root, GitCommand.CHECKOUT, currentBranch)
            if (merged && back.success()) {
                PluginNotifications.info(
                    project, "Merge complete",
                    "Merged '$currentBranch' into '$targetBranch'.\n" +
                        "You are back on '$currentBranch'."
                )
            } else if (merged) {
                PluginNotifications.warning(
                    project, "Merge complete, checkout back failed",
                    "Merged '$currentBranch' into '$targetBranch', but could not return to " +
                        "'$currentBranch'. You are now on '$targetBranch'.\n\n" + errorText(back)
                )
            }
            GitBranchUtil.updateBranches(project, listOf(repository), emptyList())
        }
    }

    private fun isWorkingTreeDirty(project: Project, root: VirtualFile): Boolean {
        val status = runCommand(project, root, GitCommand.STATUS, "--porcelain")
        return status.success() && status.output.isNotEmpty()
    }

    private fun confirmProceedWithUncommittedChanges(project: Project): Boolean {
        var result = false
        ApplicationManager.getApplication().invokeAndWait {
            result = Messages.showYesNoDialog(
                project,
                "You have uncommitted changes in the working tree.\n\n" +
                    "The merge will switch branches, which may be blocked or may carry your changes along.\n\n" +
                    "Continue anyway?",
                "Uncommitted Changes",
                "Merge Anyway",
                "Cancel",
                Messages.getWarningIcon()
            ) == Messages.YES
        }
        return result
    }

    private fun isMergeConflict(result: GitCommandResult): Boolean {
        return (result.output + result.errorOutput).any { line ->
            line.startsWith("CONFLICT") || line.startsWith("Merge conflict in ") ||
                line.contains("Automatic merge failed")
        }
    }

    private fun conflictText(result: GitCommandResult): String {
        val lines = (result.output + result.errorOutput)
            .filter { it.startsWith("CONFLICT") || it.startsWith("Merge conflict in ") }
            .map { it.trim() }
        return if (lines.isEmpty()) {
            "conflicting files were detected (see Git console for details)"
        } else {
            lines.joinToString("\n")
        }
    }

    private fun resolveRemote(repository: GitRepository, targetBranch: String): String? {
        val trackInfo = repository.getBranchTrackInfo(targetBranch)
        if (trackInfo?.remote != null) {
            val name = trackInfo.remote.name
            if (!name.isNullOrEmpty() && name != ".") {
                return name
            }
        }
        return repository.remotes
            .firstOrNull { !it.name.isNullOrEmpty() && it.name != "." }
            ?.name
    }

    private fun runCommand(project: Project, root: VirtualFile, command: GitCommand, vararg args: String):
            GitCommandResult = runCommand(project, root, command, args.toList())

    private fun runCommand(project: Project, root: VirtualFile, command: GitCommand, args: List<String>):
            GitCommandResult {
        val handler = GitLineHandler(project, root, command)
        handler.addParameters(args)
        return Git.getInstance().runCommand(handler)
    }

    private fun errorText(result: GitCommandResult): String {
        val error = result.errorOutputAsJoinedString
        val output = result.outputAsJoinedString
        return when {
            error.isNullOrEmpty() && output.isNullOrEmpty() -> "(no output)"
            error.isNullOrEmpty() -> output.orEmpty()
            else -> error
        }
    }
}
