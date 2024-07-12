package org.jetbrains.research.testspark.tools.kex.generation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import org.jetbrains.research.testspark.tools.ToolUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.application.PathManager
import org.jetbrains.research.testspark.bundles.kex.KexDefaultsBundle
import org.jetbrains.research.testspark.core.data.TestCase
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.IJReport
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.tools.kex.error.KexErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.StandardRequestManagerFactory
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.io.*

import java.nio.charset.Charset
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

class KexProcessManager(
    private val project: Project,
    private val projectPath: String
) : ProcessManager {

    private val kexProcessTimeout: Long = 12000000
    private val kexErrorManager: KexErrorManager = KexErrorManager()
    private val log = Logger.getInstance(this::class.java)

    private val kexVersion = KexDefaultsBundle.get("kexVersion")
    private val kexHome = KexDefaultsBundle.get("kexHome")
    private val pluginsPath = PathManager.getPluginsPath()

    //TODO don't assume kex is present and compiled. download if not?
    private var kexPath =
        "$kexHome${ToolUtils.sep}kex-runner${ToolUtils.sep}target${ToolUtils.sep}kex-runner-$kexVersion-jar-with-dependencies.jar"
    private val kexPolicy = "$kexHome${ToolUtils.sep}kex.policy"
    //KexProcessManager::class.java.classLoader.getResource("properties/kex/kex.policy")?.toString()


    override fun runTestGenerator(
        indicator: CustomProgressIndicator,
        codeType: FragmentToTestData,
        packageName: String,
        projectContext: ProjectContext,
        generatedTestsData: TestGenerationData,
        errorMonitor: ErrorMonitor
    ): UIContext? {
        try {
            if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

            val projectClassPath = projectContext.projectClassPath!!
            val classFQN = projectContext.classFQN!!
            val baseDir = generatedTestsData.baseDir!!
            val resultName = "${generatedTestsData.resultPath}${ToolUtils.sep}KexResult"

            Path(generatedTestsData.resultPath).createDirectories()

            //TODO cmd should have cases for codeType, which is just hardcoded to CodeType.CLASS here
//            val jvmArgs = mutableListOf<String>()
//
//            //TODO are the file separators made OS agnostic?
//            val modulesFile =
//                KexProcessManager::class.java.classLoader.getResourceAsStream("properties/kex/modules.info")
//                    ?.let { BufferedReader(InputStreamReader(it)) }
//            if (modulesFile == null) {
//                kexErrorManager.errorProcess(
//                    "testspark bug: modules file used for jvm args of kex doesn't exist",
//                    project,
//                    errorMonitor
//                )
//            } else {
//                modulesFile.lines().forEach() { line ->
//                    jvmArgs.add("--add-opens $line")
//                }
//            }
//            jvmArgs.add("--illegal-access=warn")
//
//            val cmd = mutableListOf<String>(
//                "java", //TODO Use project's java not system java. Add >v8 check
//                "-Xmx8g", //TODO 8g heapsize in properties bundle
//                "-Djava.security.manager",
//                "-Djava.security.policy==$kexPolicy",
//                "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
//            )
//
//            cmd.addAll(jvmArgs)
//            cmd.add("-jar $kexPath")
//            cmd.add("--classpath $projectClassPath")
//            cmd.add("--target $classFQN")
//            cmd.add("--output $resultName")
//            cmd.add("--mode concolic") //TODO make user option in settings

            val cmd = mutableListOf<String>(
                "python3", "./kex.py",
                "--classpath", "$projectClassPath/target/classes", // TODO how to reliably get the path to 'root of compiled files'? (only that works)
                "--target", classFQN,
                "--output", resultName,
                "--mode", "concolic"
            )

            val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
            log.info("Starting Kex with arguments: $cmdString")

            var kexOutStr: String = ""
            try {
                val proc = ProcessBuilder(cmd)
                    .directory(File(kexHome))
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()

//                proc.waitFor(kexProcessTimeout, TimeUnit.SECONDS)
                kexOutStr = proc.inputStream.bufferedReader().readText()


                log.info("OUTPUT FROM KEX:\n $kexOutStr")

            } catch (e: IOException) {
                e.printStackTrace()
            }

            System.err.println("PRINTING from STDERR:\n $kexOutStr")

//            val kexProcess = GeneralCommandLine(cmd)
//            kexProcess.charset = Charset.forName("UTF-8")
//            //kexProcess.setWorkDirectory(kexHome) //TODO consider also setting KEX_HOME env
//            val handler = OSProcessHandler(kexProcess)
//
//            handler.addProcessListener(object : ProcessAdapter() {
//                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
//                    if (ToolUtils.isProcessStopped(errorMonitor, indicator)) {
//                        handler.destroyProcess()
//                        return
//                    }
//                    kexErrorManager.addLineToKexOutput(event.text)
//                }
//            })
//
//            handler.startNotify()
//
//            if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null
//
//            if (!kexErrorManager.isProcessCorrect(
//                    handler,
//                    project,
//                    kexProcessTimeout,
//                    indicator,
//                    errorMonitor
//                )
//            ) return null


            log.info("Save generated test suite and test cases into the project workspace")
            val report = IJReport()
            val imports = mutableSetOf<String>()

            val generatedTestsDir = File("$resultName/tests/${classFQN.substringBeforeLast('.').replace('.', '/')}") //TODO does this include the .java?
            if (generatedTestsDir.exists() && generatedTestsDir.isDirectory) { //collect all generated tests into a report
                for ((index, file) in generatedTestsDir.listFiles()!!.withIndex()) {
                    val testCode = file.readText()
                    // collecting all test code
                    report.testCaseList[index] =
                        TestCase(index, file.name, file.readText(), setOf()) //TODO Maybe name includes path here?
                    // extracting just imports out of the test code
                    imports.addAll(ToolUtils.getImportsCodeFromTestSuiteCode(testCode, projectContext.classFQN!!))
                }
            } else {
                kexErrorManager.errorProcess("Generated tests don't exist. Must have had a problem running Kex", project, errorMonitor)
            }

            ToolUtils.transferToIJTestCases(report)
            ToolUtils.saveData(
                project,
                report,
                ToolUtils.getPackageFromTestSuiteCode(report.testCaseList[0]?.testCode),
                imports,
                projectContext.fileUrlAsString!!,
                generatedTestsData,
            )

            //from evosuite
//            ToolUtils.saveData(
//                project,
//                IJReport(testGenerationResult),
//                ToolUtils.getPackageFromTestSuiteCode(testGenerationResult.testSuiteCode),
//                ToolUtils.getImportsCodeFromTestSuiteCode(testGenerationResult.testSuiteCode, classFQN),
//                projectContext.fileUrlAsString!!,
//                generatedTestsData,
//            )

        } catch (e: Exception) {
            // TODO remove hardcoded string
            kexErrorManager.errorProcess(
                "An Exception occurred while executing Kex".format(e.message),
                project,
                errorMonitor
            )
            e.printStackTrace()
        }

        return UIContext(
            projectContext,
            generatedTestsData,
            StandardRequestManagerFactory(project).getRequestManager(project),
            errorMonitor
        )
    }

}