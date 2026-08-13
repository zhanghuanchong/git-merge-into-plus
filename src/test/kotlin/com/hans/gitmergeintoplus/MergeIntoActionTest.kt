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
}
