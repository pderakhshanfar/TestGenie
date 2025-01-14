package org.jetbrains.research.testspark.tools.evosuite.generation

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.evosuite.utils.CompactReport
import org.jetbrains.research.testspark.bundles.evosuite.EvoSuiteDefaultsBundle
import org.jetbrains.research.testspark.bundles.evosuite.EvoSuiteMessagesBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.generation.llm.getImportsCodeFromTestSuiteCode
import org.jetbrains.research.testspark.core.generation.llm.getPackageFromTestSuiteCode
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.data.CodeType
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.IJReport
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.services.EvoSuiteSettingsService
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.settings.evosuite.EvoSuiteSettingsState
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.evosuite.EvoSuiteSettingsArguments
import org.jetbrains.research.testspark.tools.evosuite.error.EvoSuiteErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.StandardRequestManagerFactory
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.io.FileReader
import java.nio.charset.Charset
import java.util.regex.Pattern
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * This class manages the execution of EvoSuite, a test generation tool.
 *
 * @param project the project in which the tests will be generated
 * @param projectPath the path to the project directory
 */
class EvoSuiteProcessManager(
    private val project: Project,
    private val projectPath: String,
) : ProcessManager {
    private val log = Logger.getInstance(this::class.java)

    private val evoSuiteSettingsState: EvoSuiteSettingsState
        get() = project.getService(EvoSuiteSettingsService::class.java).state

    private val evoSuiteProcessTimeout: Long = 12000000
    private val evosuiteVersion = EvoSuiteDefaultsBundle.get("evosuiteVersion")

    private val pluginsPath = com.intellij.openapi.application.PathManager.getPluginsPath()
    private var evoSuitePath = "$pluginsPath${ToolUtils.sep}TestSpark${ToolUtils.sep}lib${ToolUtils.sep}evosuite-$evosuiteVersion.jar"

    private val settingsProjectState = project.service<PluginSettingsService>().state

    private val evoSuiteErrorManager: EvoSuiteErrorManager = EvoSuiteErrorManager()

    /**
     * Executes EvoSuite.
     *
     * @param indicator the progress indicator
     */
    override fun runTestGenerator(
        indicator: CustomProgressIndicator,
        codeType: FragmentToTestData,
        packageName: String,
        projectContext: ProjectContext,
        generatedTestsData: TestGenerationData,
        errorMonitor: ErrorMonitor,
        testsExecutionResultManager: TestsExecutionResultManager,
    ): UIContext? {
        try {
            if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

            val version = ToolUtils.getJavaVersion(evoSuiteSettingsState.javaPath)
            if (version == null || version > 11) {
                evoSuiteErrorManager.errorProcess(EvoSuiteMessagesBundle.get("incorrectJavaVersion"), project, errorMonitor)
                return null
            }

            val projectClassPath = projectContext.projectClassPath!!
            val classFQN = projectContext.classFQN!!
            val baseDir = generatedTestsData.baseDir!!
            val resultName = "${generatedTestsData.resultPath}${ToolUtils.sep}EvoSuiteResult"

            Path(generatedTestsData.resultPath).createDirectories()

            // get command
            val command = when (codeType.type!!) {
                CodeType.CLASS -> EvoSuiteSettingsArguments(projectClassPath, projectPath, resultName, classFQN, baseDir, evoSuiteSettingsState).build()
                CodeType.METHOD -> {
                    EvoSuiteSettingsArguments(projectClassPath, projectPath, resultName, classFQN, baseDir, evoSuiteSettingsState).forMethod(codeType.objectDescription).build()
                }

                CodeType.LINE -> EvoSuiteSettingsArguments(projectClassPath, projectPath, resultName, classFQN, baseDir, evoSuiteSettingsState).forLine(codeType.objectIndex).build(true)
            }

            if (evoSuiteSettingsState.seed.isNotBlank()) command.add("-seed=${evoSuiteSettingsState.seed}")
            if (evoSuiteSettingsState.configurationId.isNotBlank()) command.add("-Dconfiguration_id=${evoSuiteSettingsState.configurationId}")
            if (evoSuiteSettingsState.evosuitePort.isNotBlank()) command.add("-Dprocess_communication_port=${evoSuiteSettingsState.evosuitePort}")

            // update build path
            var buildPath = projectClassPath
            if (settingsProjectState.buildPath.isEmpty()) {
                // User did not set own path
                buildPath = ToolUtils.getBuildPath(project)
            }
            command[command.indexOf(projectClassPath)] = buildPath
            log.info("Generating tests for project $projectPath with classpath $buildPath inside the project")

            // construct command
            val cmd = ArrayList<String>()
            cmd.add(evoSuiteSettingsState.javaPath)
            cmd.add("-Djdk.attach.allowAttachSelf=true")
            cmd.add("-jar")
            cmd.add(evoSuitePath)
            cmd.addAll(command)

            val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
            log.info("Starting EvoSuite with arguments: $cmdString")

//            indicator.isIndeterminate = false
            indicator.setText(PluginMessagesBundle.get("searchMessage"))

            val evoSuiteProcess = GeneralCommandLine(cmd)
            evoSuiteProcess.charset = Charset.forName("UTF-8")
            evoSuiteProcess.setWorkDirectory(projectPath)
            val handler = OSProcessHandler(evoSuiteProcess)

            // attach process listener for output
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (ToolUtils.isProcessStopped(errorMonitor, indicator)) {
                        handler.destroyProcess()
                        return
                    }

                    val text = event.text

                    evoSuiteErrorManager.addLineToEvoSuiteOutput(text)

                    val progressMatcher =
                        Pattern.compile("Progress:[>= ]*(\\d+(?:\\.\\d+)?)%").matcher(text)
                    val coverageMatcher = Pattern.compile("Cov:[>= ]*(\\d+(?:\\.\\d+)?)%").matcher(text)

                    log.info(text) // kept for debugging purposes

                    val progress =
                        if (progressMatcher.find()) {
                            progressMatcher.group(1)?.toDouble()?.div(100)
                        } else {
                            null
                        }
                    val coverage =
                        if (coverageMatcher.find()) {
                            coverageMatcher.group(1)?.toDouble()?.div(100)
                        } else {
                            null
                        }

                    if (progress != null && coverage != null) {
                        indicator.setFraction(if (progress >= coverage) progress else coverage)
                    } else if (progress != null) {
                        indicator.setFraction(progress)
                    } else if (coverage != null) {
                        indicator.setFraction(coverage)
                    }

                    if (indicator.getFraction() == 1.0 && indicator.getText() != PluginMessagesBundle.get("testCasesSaving")) {
                        indicator.setText(PluginMessagesBundle.get("testCasesSaving"))
                    }
                }
            })

            handler.startNotify()

            if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

            // evosuite errors check
            if (!evoSuiteErrorManager.isProcessCorrect(handler, project, evoSuiteProcessTimeout, indicator, errorMonitor)) return null

            val gson = Gson()
            val reader = JsonReader(FileReader(resultName))

            val testGenerationResult: CompactReport = gson.fromJson(reader, CompactReport::class.java)

            ToolUtils.saveData(
                project,
                IJReport(testGenerationResult),
                getPackageFromTestSuiteCode(testGenerationResult.testSuiteCode, SupportedLanguage.Java),
                getImportsCodeFromTestSuiteCode(testGenerationResult.testSuiteCode, classFQN),
                projectContext.fileUrlAsString!!,
                generatedTestsData,
                testsExecutionResultManager,
            )
        } catch (e: Exception) {
            evoSuiteErrorManager.errorProcess(EvoSuiteMessagesBundle.get("evosuiteErrorMessage").format(e.message), project, errorMonitor)
            e.printStackTrace()
        }

        return UIContext(projectContext, generatedTestsData, StandardRequestManagerFactory(project).getRequestManager(project), errorMonitor)
    }
}
