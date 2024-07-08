package org.jetbrains.research.testspark.tools.kex.error

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.tools.template.error.ErrorManager

class KexErrorManager : ErrorManager {
    private val log = Logger.getInstance(this::class.java)
    override fun errorProcess(message: String, project: Project, errorMonitor: ErrorMonitor) {
        if (errorMonitor.notifyErrorOccurrence()) {
            log.warn("Error in Test Generation: $message")
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LLM Execution Error")
                .createNotification(
                    PluginMessagesBundle.get("llmErrorTitle"),
                    message,
                    NotificationType.ERROR,
                )
                .notify(project)
        }
    }

    override fun warningProcess(message: String, project: Project) {
        log.warn("Error in Test Generation: $message")
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LLM Execution Error")
            .createNotification(
                PluginMessagesBundle.get("llmWarningTitle"),
                message,
                NotificationType.WARNING,
            )
            .notify(project)
    }
}