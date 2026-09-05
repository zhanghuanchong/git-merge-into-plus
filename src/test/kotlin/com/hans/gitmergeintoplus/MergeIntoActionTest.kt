package com.hans.gitmergeintoplus

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MergeIntoActionTest : BasePlatformTestCase() {

    fun testActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("gitmergeintoplus.MergeInto")
        assertNotNull("action 'gitmergeintoplus.MergeInto' must be registered", action)
    }

    fun testActionIsInsideGitMenuGroup() {
        assertInGroup("Git.MainMenu")
        assertInGroup("Git.Menu")
        assertInGroup("Git.ContextMenu")
    }

    fun testActionIsInsideVcsOperationsPopup() {
        assertInGroup("Vcs.Operations.Popup")
    }

    fun testActionIsInsideGitBranchesPopupAndBranchMenu() {
        assertInGroup("Git.Branches.List")
        assertInGroup("Git.Branch.Backend")
    }

    fun testActionHasIcon() {
        val action = ActionManager.getInstance().getAction("gitmergeintoplus.MergeInto")
        assertNotNull("action must have an icon", action.templatePresentation.icon)
    }

    private fun assertInGroup(groupId: String) {
        val group = ActionManager.getInstance().getAction(groupId) as ActionGroup
        val registered = ActionManager.getInstance().getAction("gitmergeintoplus.MergeInto")
        val children = group.getChildren(null)
        val found = children.any { it === registered }
        assertTrue(
            "action must be present in group $groupId (children=${children.map { it.templatePresentation.text }})",
            found
        )
    }

    fun testPluginIsUnloadableWithoutRestart() {
        val pluginId = com.intellij.openapi.extensions.PluginId.getId("com.hans.git-merge-into-plus")
        val descriptor = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId)
        assertNotNull("plugin descriptor must exist", descriptor)
        val unloadReason = com.intellij.ide.plugins.DynamicPlugins.validateCanUnloadWithoutRestart(descriptor as com.intellij.ide.plugins.PluginMainDescriptor)
        assertNull("plugin should be unloadable without restart, but failed: $unloadReason", unloadReason)

        val loadReason = com.intellij.ide.plugins.DynamicPlugins.validateCanLoadWithoutRestart(descriptor)
        assertNull("plugin should be loadable without restart, but failed: $loadReason", loadReason)
    }

    fun testResolveCommitMessageDefaultWhenNullOrBlank() {
        val defaultMsg = com.hans.gitmergeintoplus.git.GitMergeRunner.resolveCommitMessage("feat/login", "dev", null)
        assertEquals("Merge branch 'feat/login' into dev", defaultMsg)

        val blankMsg = com.hans.gitmergeintoplus.git.GitMergeRunner.resolveCommitMessage("feat/login", "dev", "   ")
        assertEquals("Merge branch 'feat/login' into dev", blankMsg)
    }

    fun testResolveCommitMessageCustom() {
        val custom = com.hans.gitmergeintoplus.git.GitMergeRunner.resolveCommitMessage(
            "feat/login", "dev", "Merge feat/login into dev (#1024)"
        )
        assertEquals("Merge feat/login into dev (#1024)", custom)

        val trimmed = com.hans.gitmergeintoplus.git.GitMergeRunner.resolveCommitMessage(
            "feat/login", "dev", "  Merge feat/login into dev (#1024)  "
        )
        assertEquals("Merge feat/login into dev (#1024)", trimmed)
    }

    fun testResolveCommitMessageForMultipleBranches() {
        val targets = listOf("dev", "test", "staging")
        val messages = targets.map { com.hans.gitmergeintoplus.git.GitMergeRunner.resolveCommitMessage("feat/login", it, null) }
        assertEquals("Merge branch 'feat/login' into dev", messages[0])
        assertEquals("Merge branch 'feat/login' into test", messages[1])
        assertEquals("Merge branch 'feat/login' into staging", messages[2])
    }

    fun testBranchItemHeadersAndFiltering() {
        val items = listOf(
            com.hans.gitmergeintoplus.dialog.MergeIntoDialog.BranchItem.header("Favorites"),
            com.hans.gitmergeintoplus.dialog.MergeIntoDialog.BranchItem.branch("dev", true),
            com.hans.gitmergeintoplus.dialog.MergeIntoDialog.BranchItem.header("All branches"),
            com.hans.gitmergeintoplus.dialog.MergeIntoDialog.BranchItem.branch("main", false),
            com.hans.gitmergeintoplus.dialog.MergeIntoDialog.BranchItem.branch("test", false)
        )
        val branchesOnly = items.filter { !it.header }.map { it.name }
        assertEquals(listOf("dev", "main", "test"), branchesOnly)
    }

    fun testActionUpdateThreadIsBGT() {
        val action = ActionManager.getInstance().getAction("gitmergeintoplus.MergeInto") as MergeIntoAction
        assertEquals(com.intellij.openapi.actionSystem.ActionUpdateThread.BGT, action.actionUpdateThread)
    }
}

