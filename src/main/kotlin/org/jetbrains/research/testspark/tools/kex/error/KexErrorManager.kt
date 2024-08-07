package org.jetbrains.research.testspark.tools.kex.error

import com.intellij.execution.process.OSProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.tools.template.error.ErrorManager

class KexErrorManager : ErrorManager {

    private var output = ""

    /**
     * Appends a line to the EvoSuite output.
     *
     * @param line the line to be added to the EvoSuite output
     */
    fun addLineToKexOutput(line: String) {
        output += line + "\n"
    }

    private val log = Logger.getInstance(this::class.java)
    override fun errorProcess(message: String, project: Project, errorMonitor: ErrorMonitor) {
        if (errorMonitor.notifyErrorOccurrence()) {
            log.warn("Error in Test Generation: $message")
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LLM Execution Error")
                .createNotification(
                    PluginMessagesBundle.get("kexErrorTitle"),
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
                PluginMessagesBundle.get("kexWarningTitle"),
                message,
                NotificationType.WARNING,
            )
            .notify(project)
    }

    fun isProcessCorrect(
        handler: OSProcessHandler,
        project: Project,
        kexProcessTimeout: Long,
        indicator: CustomProgressIndicator,
        errorMonitor: ErrorMonitor,
    ): Boolean {
        // exceeded timeout error
//        if (!handler.waitFor(kexProcessTimeout)) {
//            errorProcess(
//                getExceededTimeoutMessage(kexProcessTimeout),
//                project,
//                errorMonitor,
//            )
//            return false
//        }

        // non zero exit code error
        if (handler.exitCode != 0) {
            errorProcess(output, project, errorMonitor)
            return false
        }
        return true
        // TODO few other error cases to import from evosuite
    }
}
