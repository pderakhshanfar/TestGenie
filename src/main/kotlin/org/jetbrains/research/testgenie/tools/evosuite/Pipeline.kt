package org.jetbrains.research.testgenie.tools.evosuite

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.research.testgenie.TestGenieBundle
import org.jetbrains.research.testgenie.Util
import org.jetbrains.research.testgenie.services.RunnerService
import org.jetbrains.research.testgenie.services.StaticInvalidationService
import org.jetbrains.research.testgenie.services.TestCaseCachingService
import org.jetbrains.research.testgenie.tools.evosuite.generation.EvoSuiteProcessManager
import org.evosuite.result.TestGenerationResultImpl
import org.evosuite.utils.CompactReport
import org.jetbrains.research.testgenie.data.Report
import org.jetbrains.research.testgenie.data.TestCase
import org.jetbrains.research.testgenie.tools.ProjectBuilder
import org.jetbrains.research.testgenie.tools.getKey
import org.jetbrains.research.testgenie.tools.clearDataBeforeTestGeneration
import org.jetbrains.research.testgenie.tools.receiveGenerationResult
import java.io.File
import java.util.UUID

/**
 * A utility class that runs evosuite as a separate process in its various
 * modes of operation.
 *
 * @param projectPath The root of the project we're testing, this sets the working dir of the evosuite process
 * @param projectClassPath Path to the class path containing the compiled classes. This will change according to
 * build system (e.g. Maven target/classes or Gradle build/classes)
 * @param classFQN Fully qualified name of the class under test
 */
class Pipeline(
    private val project: Project,
    private val projectPath: String,
    private val projectClassPath: String,
    private val classFQN: String,
    private val fileUrl: String,
    private val modTs: Long,
) {
    private val log = Logger.getInstance(this::class.java)

    private val evoSuiteProcessManager =
        EvoSuiteProcessManager(project, projectPath, projectClassPath, fileUrl)

    private val sep = File.separatorChar

    private val id = UUID.randomUUID().toString()
    private val testResultDirectory = "${FileUtilRt.getTempDirectory()}${sep}testGenieResults$sep"
    private val testResultName = "test_gen_result_$id"

    var key = getKey(fileUrl, classFQN, modTs, testResultName, projectClassPath)

    private val serializeResultPath = "\"$testResultDirectory$testResultName\""
    private var baseDir = "$testResultDirectory$testResultName-validation"

    private var command = mutableListOf<String>()
    private var cacheFromLine: Int? = null
    private var cacheToLine: Int? = null

    private var skipCache: Boolean = false

    init {
        Util.makeTmp()
        Util.makeDir(baseDir)
    }

    /**
     * Sets up evosuite to run for a target class. This is the simplest configuration.
     */
    fun forClass(): Pipeline {
        command = SettingsArguments(projectClassPath, projectPath, serializeResultPath, classFQN, baseDir).build()
        return this
    }

    /**
     * Sets up evosuite to run for a target method of the target class. This attaches a method descriptor argument
     * to the evosuite process.
     *
     * @param methodDescriptor The method descriptor of the method under test
     */
    fun forMethod(methodDescriptor: String): Pipeline {
        command =
            SettingsArguments(projectClassPath, projectPath, serializeResultPath, classFQN, baseDir).forMethod(
                methodDescriptor,
            ).build()

        // attach method desc. to target unit key
        key = getKey(fileUrl, "$classFQN#$methodDescriptor", modTs, testResultName, projectClassPath)

        return this
    }

    /**
     * Sets up evosuite to run for a target line of the target class. This attaches the selected line argument
     * to the evosuite process.
     */
    fun forLine(selectedLine: Int): Pipeline {
        command = SettingsArguments(projectClassPath, projectPath, serializeResultPath, classFQN, baseDir).forLine(
            selectedLine,
        ).build(true)

        return this
    }

    /**
     * Configures lines for the cache (0-indexed)
     */
    fun withCacheLines(fromLine: Int, toLine: Int): Pipeline {
        this.cacheFromLine = fromLine + 1
        this.cacheToLine = toLine + 1
        return this
    }

    /**
     * Method to invalidate the cache.
     *
     * @param linesToInvalidate set of lines to invalidate
     */
    fun invalidateCache(linesToInvalidate: Set<Int>): Pipeline {
        val staticInvalidator = project.service<StaticInvalidationService>()
        staticInvalidator.invalidateCacheLines(fileUrl, linesToInvalidate)
        log.info("Going to invalidate $linesToInvalidate lines")
        return this
    }

    /**
     * Builds the project and launches the EvoSuite process,
     * tracking it from a separate thread.
     * Generate tests even if there is no cache miss.
     */
    fun withoutCache(): Pipeline {
        this.skipCache = true
        return this
    }

    /**
     * Builds the project and launches EvoSuite on a separate thread.
     *
     * @return the path to which results will be (eventually) saved
     */
    fun runTestGeneration(): String {
        log.info("Starting build and EvoSuite task")
        log.info("EvoSuite results will be saved to $serializeResultPath")

        val hasCachedTests = !skipCache && tryShowCachedTestCases()

        clearDataBeforeTestGeneration(project, key, testResultName)

        val projectBuilder = ProjectBuilder(project)

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, TestGenieBundle.message("testGenerationMessage")) {
                override fun run(indicator: ProgressIndicator) {
                    if (hasCachedTests) {
                        indicator.stop()
                        return
                    }

                    if (indicator.isCanceled) {
                        indicator.stop()
                        return
                    }

                    if (projectBuilder.runBuild(indicator)) {
                        evoSuiteProcessManager.runEvoSuite(indicator, command, log, testResultName, classFQN)
                    }

                    // Revert to previous state
                    val runnerService = project.service<RunnerService>()
                    runnerService.isRunning = false

                    // TODO uncomment after the validator fixing
//                    val testCaseDisplayService = project.service<TestCaseDisplayService>()
//                    testCaseDisplayService.validateButton.isEnabled = true

                    indicator.stop()
                }
            })

        // TODO uncomment after the validator fixing
//        val testCaseDisplayService = project.service<TestCaseDisplayService>()
//        testCaseDisplayService.toggleJacocoButton.isEnabled = false

        return testResultName
    }

    /**
     * Attempts to retrieve and display cached test cases.
     *
     * @return true if cached tests were found, false otherwise
     */
    private fun tryShowCachedTestCases(): Boolean {
        val cache = project.service<TestCaseCachingService>()
        val testCases = cache.retrieveFromCache(fileUrl, cacheFromLine!!, cacheToLine!!)

        if (testCases.isEmpty()) {
            // no suitable cached tests found
            return false
        }

        // retrieve the job of an arbitrary valid test case
        val testJobInfo = cache.getTestJobInfo(fileUrl, testCases[0].testCode)

        ApplicationManager.getApplication().invokeLater {
            val report = Report(CompactReport(TestGenerationResultImpl()))
            val testMap = hashMapOf<String, TestCase>()
            testCases.forEach {
                testMap[it.testName] = it
            }

            report.testCaseList = testMap
            report.allCoveredLines = testCases.map { it.coveredLines }.flatten().toSet()

            receiveGenerationResult(project, testResultName, report, this, testJobInfo)

            project.service<RunnerService>().isRunning = false
        }

        return true
    }
}