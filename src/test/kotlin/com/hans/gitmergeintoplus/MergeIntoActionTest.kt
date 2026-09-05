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
}

