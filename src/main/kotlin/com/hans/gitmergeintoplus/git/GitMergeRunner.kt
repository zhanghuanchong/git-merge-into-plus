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
        run(project, repository, currentBranch, listOf(targetBranch), noFF, push, pullBeforeMerge, customCommitMessage)
    }

    fun run(
        project: Project,
        repository: GitRepository,
        currentBranch: String,
        targetBranches: List<String>,
        noFF: Boolean,
        push: Boolean,
        pullBeforeMerge: Boolean = false,
        customCommitMessage: String? = null,
    ) {
        if (targetBranches.isEmpty()) return

        val title = if (targetBranches.size == 1) {
            "Merge '$currentBranch' into '${targetBranches.first()}'"
        } else {
            "Merge '$currentBranch' into ${targetBranches.size} branches"
        }

        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Preparing merge..."
                perform(project, repository, currentBranch, targetBranches, noFF, push, pullBeforeMerge, customCommitMessage, indicator)
            }
        })
    }

    private fun perform(
        project: Project,
        repository: GitRepository,
        currentBranch: String,
        targetBranches: List<String>,
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
                "Merge was cancelled due to uncommitted changes."
            )
            return
        }

        val successfulBranches = mutableListOf<String>()
        val pushedBranches = mutableListOf<String>()
        val pushFailedBranches = mutableListOf<String>()
        var failedBranch: String? = null
        var failureMessage: String? = null
        var isConflict = false
        var conflictDetails = ""

        try {
            for ((index, targetBranch) in targetBranches.withIndex()) {
                val stepPrefix = if (targetBranches.size > 1) "(${index + 1}/${targetBranches.size}) " else ""

                indicator.text = "${stepPrefix}Checking out '$targetBranch'..."
                val checkout = runCommand(project, root, GitCommand.CHECKOUT, targetBranch)
                if (!checkout.success()) {
                    failedBranch = targetBranch
                    failureMessage = "Could not switch to branch '$targetBranch'.\n\n" + errorText(checkout)
                    break
                }

                if (pullBeforeMerge) {
                    val remote = resolveRemote(repository, targetBranch)
                    if (remote != null) {
                        indicator.text = "${stepPrefix}Updating '$targetBranch' from '$remote'..."
                        val trackInfo = repository.getBranchTrackInfo(targetBranch)
                        val remoteBranch = trackInfo?.remoteBranch?.nameForRemoteOperations ?: targetBranch
                        val pullResult = runCommand(project, root, GitCommand.PULL, "--ff-only", remote, remoteBranch)
                        if (!pullResult.success()) {
                            failedBranch = targetBranch
                            failureMessage = "Could not fast-forward '$targetBranch' from '$remote/$remoteBranch'.\n\n" +
                                errorText(pullResult)
                            break
                        }
                    } else {
                        PluginNotifications.warning(
                            project, "Update skipped",
                            "No remote found for branch '$targetBranch' — update was skipped."
                        )
                    }
                }

                indicator.text = "${stepPrefix}Merging '$currentBranch' into '$targetBranch'..."
                val mergeArgs = buildList {
                    if (noFF) add("--no-ff")
                    add("-m")
                    add(resolveCommitMessage(currentBranch, targetBranch, customCommitMessage))
                    add(currentBranch)
                }
                val merge = runCommand(project, root, GitCommand.MERGE, mergeArgs)

                if (!merge.success()) {
                    failedBranch = targetBranch
                    if (isMergeConflict(merge)) {
                        isConflict = true
                        val abort = runCommand(project, root, GitCommand.MERGE, "--abort")
                        conflictDetails = "Conflicts detected:\n" + conflictText(merge) +
                            if (abort.success()) "" else "\n\n(merge --abort also failed: " + errorText(abort) + ")"
                        failureMessage = "Merging '$currentBranch' into '$targetBranch' caused conflicts and was aborted."
                    } else {
                        failureMessage = "Merging '$currentBranch' into '$targetBranch' failed.\n\n" + errorText(merge)
                    }
                    break
                }

                successfulBranches.add(targetBranch)

                if (push) {
                    val remote = resolveRemote(repository, targetBranch)
                    if (remote != null) {
                        indicator.text = "${stepPrefix}Pushing '$targetBranch' to '$remote'..."
                        val pushResult = runCommand(project, root, GitCommand.PUSH, remote, targetBranch)
                        if (pushResult.success()) {
                            pushedBranches.add(targetBranch)
                        } else {
                            pushFailedBranches.add(targetBranch)
                        }
                    } else {
                        PluginNotifications.warning(
                            project, "Push skipped",
                            "No remote found for branch '$targetBranch' — push was skipped."
                        )
                    }
                }
            }
        } finally {
            indicator.text = "Returning to '$currentBranch'..."
            val back = runCommand(project, root, GitCommand.CHECKOUT, currentBranch)
            val backSuccess = back.success()

            notifyMergeResults(
                project, currentBranch, targetBranches, successfulBranches,
                pushedBranches, pushFailedBranches, failedBranch, failureMessage,
                isConflict, conflictDetails, backSuccess, back
            )

            GitBranchUtil.updateBranches(project, listOf(repository), emptyList())
        }
    }

    private fun notifyMergeResults(
        project: Project,
        currentBranch: String,
        allTargets: List<String>,
        successfulBranches: List<String>,
        pushedBranches: List<String>,
        pushFailedBranches: List<String>,
        failedBranch: String?,
        failureMessage: String?,
        isConflict: Boolean,
        conflictDetails: String,
        backSuccess: Boolean,
        backResult: GitCommandResult,
    ) {
        if (failedBranch == null) {
            val title = if (allTargets.size == 1) "Merge complete" else "All ${allTargets.size} merges complete"
            val sb = StringBuilder()
            if (allTargets.size == 1) {
                sb.append("Merged '$currentBranch' into '${allTargets.first()}'.\n")
            } else {
                sb.append("Successfully merged '$currentBranch' into ${allTargets.size} branches:\n")
                sb.append(allTargets.joinToString(", "))
                sb.append("\n")
            }
            if (pushedBranches.isNotEmpty()) {
                sb.append("Pushed: ").append(pushedBranches.joinToString(", ")).append("\n")
            }
            if (pushFailedBranches.isNotEmpty()) {
                sb.append("Push failed: ").append(pushFailedBranches.joinToString(", ")).append("\n")
            }
            if (backSuccess) {
                sb.append("You are back on '$currentBranch'.")
                PluginNotifications.info(project, title, sb.toString().trim())
            } else {
                sb.append("\nCould not return to '$currentBranch'.\n\n").append(errorText(backResult))
                PluginNotifications.warning(project, "$title, checkout back failed", sb.toString().trim())
            }
            return
        }

        val title = if (isConflict) "Merge conflicts" else "Merge failed"
        val sb = StringBuilder()
        if (successfulBranches.isNotEmpty()) {
            sb.append("Partially completed.\n")
            sb.append("Merged successfully: ").append(successfulBranches.joinToString(", ")).append("\n\n")
        }
        sb.append(failureMessage.orEmpty()).append("\n")
        if (isConflict && conflictDetails.isNotEmpty()) {
            sb.append("\n").append(conflictDetails).append("\n")
        }

        val remainingBranches = allTargets.dropWhile { it != failedBranch }.drop(1)
        if (remainingBranches.isNotEmpty()) {
            sb.append("\nRemaining branches skipped: ").append(remainingBranches.joinToString(", ")).append("\n")
        }

        if (backSuccess) {
            sb.append("\nYou are back on '$currentBranch'.")
            PluginNotifications.error(project, title, sb.toString().trim())
        } else {
            sb.append("\nCould not return to '$currentBranch'.\n\n").append(errorText(backResult))
            PluginNotifications.warning(project, "$title, checkout back failed", sb.toString().trim())
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
