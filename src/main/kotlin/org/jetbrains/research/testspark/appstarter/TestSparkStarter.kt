package org.jetbrains.research.testspark.appstarter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.research.testspark.bundles.llm.LLMDefaultsBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.monitor.DefaultErrorMonitor
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.TestCompiler
import org.jetbrains.research.testspark.core.test.data.CodeType
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.llm.JsonEncoding
import org.jetbrains.research.testspark.java.JavaPsiMethodWrapper
import org.jetbrains.research.testspark.kotlin.KotlinPsiHelperProvider
import org.jetbrains.research.testspark.langwrappers.PsiHelperProvider
import org.jetbrains.research.testspark.progress.HeadlessProgressIndicator
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.tools.TestProcessor
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.factories.TestCompilerFactory
import org.jetbrains.research.testspark.tools.llm.Llm
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * This class is responsible for generating and running tests based on the provided arguments in headless mode.
 */
class TestSparkStarter : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String = "testspark"

    /** Sets main (start) thread for IDE in headless as not edt. */
    override val requiredModality: Int = ApplicationStarter.NOT_IN_EDT

    @Suppress("TooGenericExceptionCaught")
    @OptIn(ExperimentalSerializationApi::class)
    override fun main(args: List<String>) {
        // Project path
        val projectPath = args[1]
        // Path to the target file (.java file)
        val cutSourceFilePath = Paths.get(projectPath, args[2]).toAbsolutePath()
        // CUT name (<package-name>.<class-name>)
        val classUnderTestName = args[3]
        // Paths to compilation output of the project under test (seperated by ':')
        val projectClassPath = args[4]
        val classPath = "$projectPath${ToolUtils.pathSep}$projectClassPath"
        // JUnit Version
        val jUnitVersion = args[5]
        // Selected model
        val model = args[6]
        // Token
        val token = args[7]
        // Filepath to a file containing the prompt template
        val promptTemplateFile = args[8]
        // Output directory
        val output = args[9]
        // Run coverage
        val runCoverage = args[10].toBoolean()
        // Method under test name(or empty string for class level generation)
        val methodName = args[11]

        val testsExecutionResultManager = TestsExecutionResultManager()
        // TODO check for suitable refactoring
        val language =
            if (cutSourceFilePath.toString().endsWith(".kt")) SupportedLanguage.Kotlin else SupportedLanguage.Java

        println("Test generation requested for $projectPath")

        // remove the `.idea` folder in the $projectPath if exists
        val ideaFolderPath = "$projectPath${File.separator}.idea"
        val ideaFolder = File(ideaFolderPath)
        if (ideaFolder.exists()) {
            ideaFolder.deleteRecursively()
        }

        // open and resolve the project
        val project = try {
            JvmProjectConfigurator().openProject(
                Paths.get(projectPath),
                fullResolve = true,
                parentDisposable = Disposer.newDisposable(),
            )
        } catch (e: Throwable) {
            e.printStackTrace(System.err)
            exitProcess(1)
        }

        ApplicationManager.getApplication().invokeAndWait {
            println("Detected project: $project")
            // Continue when the project is indexed
            println("Indexing project...")
            project.let {
                DumbService.getInstance(it).runWhenSmart {
                    try {
                        // open target file
                        val cutSourceVirtualFile =
                            LocalFileSystem.getInstance().findFileByPath(cutSourceFilePath.toString()) ?: run {
                                println("Couldn't open file $cutSourceFilePath")
                                exitProcess(1)
                            }
                        // get target PsiClass
                        val psiFile = PsiManager.getInstance(project).findFile(cutSourceVirtualFile)
                        val targetPsiClass = detectPsiClass(
                            when (language) {
                                SupportedLanguage.Java -> psiFile as PsiJavaFile
                                SupportedLanguage.Kotlin -> psiFile as KtFile
                            }.classes,
                            classUnderTestName
                        ) ?: run {
                            println("Couldn't find $classUnderTestName in $cutSourceFilePath")
                            exitProcess(1)
                        }

                        println("PsiClass ${targetPsiClass.qualifiedName} is detected! Start the test generation process.")

                        // Get project SDK
                        val projectSDKPath = getProjectSdkPath(project)
                        // update settings
                        val settingsState = project.getService(LLMSettingsService::class.java).state
                        settingsState.currentLLMPlatformName = LLMDefaultsBundle.get("grazieName")
                        settingsState.grazieToken = token
                        settingsState.grazieModel = model
                        settingsState.classPrompts =
                            JsonEncoding.encode(mutableListOf(File(promptTemplateFile).readText()))
                        settingsState.junitVersion = when (jUnitVersion.filter { it.isDigit() }) {
                            "4" -> JUnitVersion.JUnit4
                            "5" -> JUnitVersion.JUnit5
                            else -> {
                                throw IllegalArgumentException("JUnit version $jUnitVersion is not supported. Supported JUnit versions are '4' and '5'")
                            }
                        }
                        project.service<PluginSettingsService>().state.buildPath = classPath

                        // Prepare Project Context
                        // First, get CUT Module
                        val cutModule = ProjectFileIndex.getInstance(project)
                            .getModuleForFile(targetPsiClass.containingFile.virtualFile)
                        // Then, instantiate the project context
                        val projectContext = ProjectContext(
                            classPath,
                            output,
                            targetPsiClass.qualifiedName,
                            cutModule,
                        )
                        // Prepare the test generation data
                        val testGenerationData = TestGenerationData(
                            resultPath = output,
                            testResultName = "HeadlessGeneratedTests",
                        )
                        println("[TestSpark Starter] Indexing is done")

                        // get package name
                        val packageList = targetPsiClass.qualifiedName.toString().split(".").dropLast(1).toMutableList()
                        val packageName = packageList.joinToString(".")

                        // Get PsiHelper
                        val psiHelper = when (language) {
                            SupportedLanguage.Kotlin -> KotlinPsiHelperProvider().getPsiHelper(psiFile as KtFile)
                            SupportedLanguage.Java -> PsiHelperProvider.getPsiHelper(psiFile as PsiJavaFile)
                        }
                        if (psiHelper == null) {
                            // TODO exception: the support for the current language does not exist
                        }
                        // Create a process Manager
                        val llmProcessManager = Llm()
                            .getLLMProcessManager(
                                project,
                                psiHelper!!,
                                targetPsiClass.textRange.startOffset,
                                testSamplesCode = "", // we don't provide samples to LLM
                                projectSDKPath = projectSDKPath,
                            )

                        println("[TestSpark Starter] Starting the test generation process")
                        // Start test generation
                        val indicator = HeadlessProgressIndicator()
                        val errorMonitor = DefaultErrorMonitor()
                        val testCompiler = TestCompilerFactory.create(
                            project,
                            settingsState.junitVersion,
                            psiHelper.language,
                            projectSDKPath.toString(),
                        )
                        val codeType = when (methodName) {
                            "" -> FragmentToTestData(CodeType.CLASS)
                            else -> {
                                val psiMethod = targetPsiClass.methods.find { it.name == methodName } ?: run {
                                    println("Couldn't find method $methodName")
                                    exitProcess(1)
                                }
                                FragmentToTestData(
                                    CodeType.METHOD,
                                    psiHelper.generateMethodDescriptor(JavaPsiMethodWrapper(psiMethod as PsiMethod))
                                )
                            }
                        }

                        val uiContext = llmProcessManager.runTestGenerator(
                            indicator,
                            codeType,
                            packageName,
                            projectContext,
                            testGenerationData,
                            errorMonitor,
                            testsExecutionResultManager,
                        )

                        // Check test Generation Output
                        if (uiContext != null) {
                            println("[TestSpark Starter] Test generation completed successfully")
                            // Run test file
                            if (runCoverage) {
                                runTestsWithCoverageCollection(
                                    project,
                                    output,
                                    packageList,
                                    classPath,
                                    projectContext,
                                    projectSDKPath,
                                    testCompiler,
                                    settingsState.junitVersion,
                                )
                            }
                        } else {
                            println("[TestSpark Starter] Test generation failed")
                        }

                        ProjectManager.getInstance().closeAndDispose(project)

                        println("[TestSpark Starter] Exiting the headless mode")
                        exitProcess(0)
                    } catch (e: Throwable) {
                        println("[TestSpark Starter] Exiting the headless mode with an exception")

                        ProjectManager.getInstance().closeAndDispose(project)
                        e.printStackTrace(System.err)
                        exitProcess(0)
                    }
                }
            }
        }
    }

    /**
     * Retrieves the project SDK based on the provided parameters.
     *
     * @param project the project inder test
     * @return the project SDK for running and compiling tests generated in headless mode
     */
    private fun getProjectSdkPath(project: Project): Path {
        return when (val projectSdk = ProjectRootManager.getInstance(project).projectSdk) {
            null -> {
                println("Did not resolve the project SDK, using default SDK")
                Paths.get(System.getProperty("java.home"))
            }

            else -> Paths.get(projectSdk.homeDirectory!!.path)
        }
    }

    private fun runTestsWithCoverageCollection(
        project: Project,
        out: String,
        packageList: MutableList<String>,
        classPath: String,
        projectContext: ProjectContext,
        projectSDKPath: Path,
        testCompiler: TestCompiler,
        junitVersion: JUnitVersion,
    ) {
        val targetDirectory = "$out${File.separator}${packageList.joinToString(File.separator)}"
        println("Run tests in $targetDirectory")
        File(targetDirectory).walk().forEach {
            // TODO Doesn't work for compiled kotlin files
            if (it.name.endsWith(".class")) {
                println("Running test ${it.name}")
                var testcaseName = it.nameWithoutExtension.removePrefix("Generated")
                testcaseName = testcaseName[0].lowercaseChar() + testcaseName.substring(1)
                // The current test is compiled and is ready to run jacoco

                val testExecutionError = TestProcessor(project, projectSDKPath).createXmlFromJacoco(
                    it.nameWithoutExtension,
                    "$targetDirectory${File.separator}jacoco-${it.nameWithoutExtension}",
                    testcaseName,
                    classPath,
                    packageList.joinToString("."),
                    out,
                    projectContext,
                    testCompiler,
                    junitVersion,
                )
                // Saving exception (if exists) thrown during the test execution
                saveException(testcaseName, targetDirectory, testExecutionError)
            }
        }
    }

    private fun saveException(
        testcaseName: String,
        targetDirectory: String,
        testExecutionError: String,
    ) {
        if (testExecutionError.isBlank() || !testExecutionError.contains("Exception", ignoreCase = false)) {
            return
        }
        val targetPath = Paths.get(targetDirectory, "$testcaseName-exception.log")

        // Save the exception
        targetPath.toFile().writeText(testExecutionError.replace("\tat ", "\nat "))
    }

    private fun detectPsiClass(classes: Array<PsiClass>, classUnderTestName: String): PsiClass? {
        for (psiClass in classes) {
            if (psiClass.qualifiedName == classUnderTestName) {
                return psiClass
            }
        }
        return null
    }
}
