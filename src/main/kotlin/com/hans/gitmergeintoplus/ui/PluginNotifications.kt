package com.hans.gitmergeintoplus.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object PluginNotifications {

    private const val GROUP_ID = "gitmergeintoplus.notifications"

    fun info(project: Project?, title: String, content: String) =
        show(project, title, content, NotificationType.INFORMATION)

    fun warning(project: Project?, title: String, content: String) =
        show(project, title, content, NotificationType.WARNING)

    fun error(project: Project?, title: String, content: String) =
        show(project, title, content, NotificationType.ERROR)

    private fun show(project: Project?, title: String, content: String, type: NotificationType) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
        group?.createNotification(title, content, type)?.notify(project)
    }
}
