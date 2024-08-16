package org.jetbrains.research.testspark.tools.kex.generation

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.BlockStmt
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.research.testspark.bundles.kex.KexDefaultsBundle
import org.jetbrains.research.testspark.bundles.kex.KexMessagesBundle
import org.jetbrains.research.testspark.core.data.TestCase
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.generation.llm.getImportsCodeFromTestSuiteCode
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.data.CodeType
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.IJReport
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.services.KexSettingsService
import org.jetbrains.research.testspark.settings.kex.KexSettingsState
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.kex.KexSettingsArguments
import org.jetbrains.research.testspark.tools.kex.error.KexErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.StandardRequestManagerFactory
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.io.File
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

class KexProcessManager(
    private val project: Project,
    private val projectPath: String,
) : ProcessManager {

    private val kexErrorManager: KexErrorManager = KexErrorManager()
    private val log = Logger.getInstance(this::class.java)

    private val kexVersion = KexDefaultsBundle.get("kexVersion")
    private val kexUrl = "https://github.com/edwin1729/kex/releases/download/$kexVersion/kex-$kexVersion.zip"
    private var kexHome: String = kexSettingsState.kexHome

    init {
        // use default cache location if not explicitly provided
        if (kexHome.isBlank()) {
            val userHome = System.getProperty("user.home")
            val kexHomeFile = when {
                // On Windows, use the LOCALAPPDATA environment variable
                // TODO windows stuff is untested
                System.getProperty("os.name").startsWith("Windows") -> System.getenv("LOCALAPPDATA")
                    ?.let { File(it, ToolUtils.osJoin("JetBrains", "TestSpark", "kex")) }
                // On Unix-like systems, use the ~/.cache directory
                else -> File(userHome, ToolUtils.osJoin(".cache", "JetBrains", "TestSpark", "kex"))
            }
            // Ensure the cache directory exists
            if (kexHomeFile != null && !kexHomeFile.exists()) {
                kexHomeFile.mkdirs()
            }
            kexHome = kexHomeFile.toString()
        }
    }
    private val kexSettingsState: KexSettingsState
        get() = project.getService(KexSettingsService::class.java).state
    private var kexExecPath =
        ToolUtils.osJoin(kexHome, "kex-runner", "target", "kex-runner-$kexVersion-jar-with-dependencies.jar")

    override fun runTestGenerator(
        indicator: CustomProgressIndicator,
        codeType: FragmentToTestData,
        packageName: String,
        projectContext: ProjectContext,
        generatedTestsData: TestGenerationData,
        errorMonitor: ErrorMonitor,
    ): UIContext? {
        try {
            if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

            val classFQN = projectContext.classFQN!!
            val resultName = ToolUtils.osJoin(generatedTestsData.resultPath, "KexResult")
            val projectSdk = ProjectRootManager.getInstance(project).projectSdk!!
            val javaExecPath = ToolUtils.osJoin(projectSdk.homePath!!, "bin", "java")

            // set target argument for kex subprocess. ensure not Line codetype which is unsupported
            val target: String = when (codeType.type!!) {
                CodeType.CLASS -> classFQN
                CodeType.METHOD -> "$classFQN::${codeType.objectDescription}"
                CodeType.LINE -> run {
                    kexErrorManager.errorProcess(
                        KexMessagesBundle.get("unsupportedCodeTypeLine"),
                        project,
                        errorMonitor,
                    )
                    return null
                }
            }

            // Disallow old java versions
            val version = ToolUtils.getJavaVersion(javaExecPath)
            if (version == null || version < 8) {
                kexErrorManager.errorProcess(KexMessagesBundle.get("incorrectJavaVersion"), project, errorMonitor)
                return null
            }
            Path(generatedTestsData.resultPath).createDirectories()

            // Download kex executable if not present
            if (!ensureKexExists()) {
                kexErrorManager.errorProcess(KexMessagesBundle.get("invalidUrl"), project, errorMonitor)
                return null
            }

            val cmd = KexSettingsArguments(
                javaExecPath,
                projectContext.cutModule!!,
                target,
                resultName,
                kexSettingsState,
                kexExecPath,
                kexHome,
            ).buildCommand()

            val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
            log.info("Starting Kex with arguments: $cmdString")

            // run kex as subprocess
            if (!runKex(cmd, errorMonitor, indicator)) return null

            log.info("Save generated test suite and test cases into the project workspace")

            preprocessGeneratedTestFiles(resultName, classFQN, generatedTestsData, projectContext, errorMonitor)
        } catch (e: Exception) {
            kexErrorManager.errorProcess(
                KexMessagesBundle.get("kexErrorCommon").format(e.message),
                project,
                errorMonitor,
            )
            e.printStackTrace()
        }

        return UIContext(
            projectContext,
            generatedTestsData,
            StandardRequestManagerFactory(project).getRequestManager(project),
            errorMonitor,
        )
    }

    private fun preprocessGeneratedTestFiles(
        resultName: String,
        classFQN: String,
        generatedTestsData: TestGenerationData,
        projectContext: ProjectContext,
        errorMonitor: ErrorMonitor,
    ) {
        val report = IJReport()
        val imports = mutableSetOf<String>()
        val packageStr = classFQN.substringBeforeLast('.')

        val generatedTestsDir = File(
            "$resultName/tests/${classFQN.substringBeforeLast('.').replace('.', '/')}",
        )
        if (generatedTestsDir.exists() && generatedTestsDir.isDirectory) { // collect all generated tests into a report
            for ((index, file) in generatedTestsDir.listFiles()!!.withIndex()) {
                val testCode = file.readText()

                if (file.name.contains("Equality") || file.name.contains("Reflection")) {
                    // collecting all methods
                    generatedTestsData.otherInfo += "${getHelperClassBody(testCode)}\n"
                } else {
                    // merge @before and @test annotated methods into a single method
                    report.testCaseList[index] =
                        TestCase(
                            index,
                            file.name.substringBefore('.'),
                            extractTestMethod(file, index).toString(),
                            setOf(),
                        )
                }
                // extracting just imports out of the test code
                // remove imports from helper classes
                imports.addAll(
                    getImportsCodeFromTestSuiteCode(testCode, projectContext.classFQN!!)
                        .filterNot {
                            it.contains("import static org.example.EqualityUtils.*;") ||
                                it.contains("import static org.example.ReflectionUtils.*;")
                        },
                )
            }
        } else {
            kexErrorManager.errorProcess(
                KexMessagesBundle.get("testsDontExist"),
                project,
                errorMonitor,
            )
        }

        ToolUtils.transferToIJTestCases(report)
        ToolUtils.saveData(
            project,
            report,
            packageStr,
            imports,
            projectContext.fileUrlAsString!!,
            generatedTestsData,
        )
    }

    /**
     * @return false if the subprocess didn't run successfully
     */
    private fun runKex(
        cmd: MutableList<String>,
        errorMonitor: ErrorMonitor,
        indicator: CustomProgressIndicator,
    ): Boolean {
        try {
            val kexProcess = GeneralCommandLine(cmd)
            kexProcess.charset = Charset.forName("UTF-8")
            kexProcess.workDirectory = File(kexHome)
            kexProcess.environment["KEX_HOME"] = kexHome

            val handler = OSProcessHandler(kexProcess)
            val output = ProcessOutput()
            ProcessTerminatedListener.attach(handler)

            // rerouting stdout and stderr
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (ToolUtils.isProcessStopped(errorMonitor, indicator)) {
                        handler.destroyProcess()
                        return
                    }
                    kexErrorManager.addLineToKexOutput(event.text)
                    if (outputType.toString() == "stdout") {
                        output.appendStdout(event.text)
                    } else if (outputType.toString() == "stderr") {
                        output.appendStderr(event.text)
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    log.info("Process terminated with exit code: ${event.exitCode}")
                    log.info("Output from Kex stdout:\n ${output.stdout}")
                    log.info("Output from Kex stderr:\n ${output.stderr}")
                }
            })

            handler.startNotify()
            handler.waitFor() // no timeout provided since kex has builtin timeouts which we pass as args
            return kexErrorManager.isProcessCorrect(handler, project, errorMonitor)
        } catch (e: IOException) {
            kexErrorManager.errorProcess(
                KexMessagesBundle.get("kexErrorSubprocess").format(e.message),
                project,
                errorMonitor,
            )
            e.printStackTrace()
            log.error("Error running KEX process", e)
            return false
        }
    }

    private fun getHelperClassBody(testCode: String) =
        (
            StaticJavaParser.parse(testCode).findFirst(ClassOrInterfaceDeclaration::class.java)
                .get()
            ).toString().substringAfter('{').substringBeforeLast('}')

    private fun extractTestMethod(file: File?, id: Int): MethodDeclaration {
        val compilationUnit = StaticJavaParser.parse(file)
        // Kex generates a Class for each test case. Each class has exactly two methods:
        // at index 1 an @Before annotated method
        // at index 2 an @Test annotated method
        // merge the two into a single method
        val methods = compilationUnit.findAll(MethodDeclaration::class.java)
        val methodStmts = methods.map { it.body.map { it.statements }.orElse(NodeList()) }

        val fields = NodeList(
            compilationUnit.findAll(FieldDeclaration::class.java)
                .filterNot { it.isPublic } // drops the timeout
                .map { it.toString() }
                .map { StaticJavaParser.parseStatement(it) },
        )
        fields.addAll(methodStmts[1])
        fields.addAll(methodStmts[2])
        methods[2].setBody(BlockStmt(fields))
        methods[2].setName("test$id")
        return methods[2]
    }

    /**
     * Ensures that the necessary files for Kex execution exist. If the files already exist, this method will return true.
     * If any required file is missing, this method will attempt to download the necessary files.
     *
     * @return False if kex doesn't exist and files couldn't be downloaded
     */
    private fun ensureKexExists(): Boolean {
        val kexDir = File(kexHome)
        if (!kexDir.exists()) {
            kexDir.mkdirs()
        }

        // All jars in runtime deps are also required
        // There are more files on top of this which I'm not aware of so the kex zip on github just has the whole project
        // Importantly this list has kaxExecPath which contains a version number. So if the number changes the project will be updated with a download
        val requiredFiles = listOf(
            "$kexHome/kex.ini",
            "$kexHome/kex.policy",
            "$kexHome/runtime-deps/modules.info",
            kexExecPath,
        )
        if (requiredFiles.map { File(it).exists() }.all { it }) {
            log.info("Specified kex jar found, skipping update")
            return true
        }

        log.info("Kex executable and helper files not found, downloading Kex")
        val stream =
            try {
                URL(kexUrl).openStream()
            } catch (e: Exception) {
                log.error("Error fetching latest kex custom release - $e")
                return false
            }

        // this can fail unexpectedly if a file with the same name as a required directory exists
        // inside the given kexHome path.
        ZipInputStream(stream).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    val file = File("$kexHome/${entry.name}")
                    file.parentFile.mkdirs() // makes any directories required
                    file.outputStream().use { output ->
                        zipInputStream.copyTo(output)
                    }
                }
        }
        log.info("Latest kex project successfully downloaded")
        return true
    }
}