package com.hans.gitmergeintoplus.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginNotificationsTest : BasePlatformTestCase() {

    fun testNotificationCanBeCreatedWithoutError() {
        PluginNotifications.info(project, "Merge complete", "Test notification")
        PluginNotifications.warning(project, "Push failed", "Test warning")
        PluginNotifications.error(project, "Merge failed", "Test error")
    }
}
