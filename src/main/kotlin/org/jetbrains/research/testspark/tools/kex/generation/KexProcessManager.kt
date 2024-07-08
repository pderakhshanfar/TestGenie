package org.jetbrains.research.testspark.tools.kex.generation

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import org.jetbrains.research.testspark.tools.ToolUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.evosuite.utils.CompactReport
import com.intellij.openapi.application.PathManager
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
import java.io.BufferedReader

import java.nio.file.Paths
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.script.experimental.jvm.impl.getResourcePathForClass

class KexProcessManager(
    private val project: Project,
    private val projectPath: String
) : ProcessManager {

    private val kexErrorManager: KexErrorManager = KexErrorManager()
    private val log = Logger.getInstance(this::class.java)

    private val pluginsPath = PathManager.getPluginsPath()
    private var kexPath =
        "$pluginsPath${ToolUtils.sep}TestSpark${ToolUtils.sep}lib${ToolUtils.sep}kex-runner-0.0.7-jar-with-dependencies.jar"
    //TODO take from project
    private val kexPolicy = KexProcessManager::class.java.getResource("properties/kex/kex.policy")?.readText()
        //"/homes/ef322/kex.policy"
//    private var modulesFile = PathManager.getResourceRoot(KexProcessManager::class.java, "")
        //"$pluginsPath${ToolUtils.sep}TestSpark${ToolUtils.sep}src${ToolUtils.sep}main${ToolUtils.sep}resources${ToolUtils.sep}properties${ToolUtils.sep}kex"

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
            val resultName = "${generatedTestsData.resultPath}${ToolUtils.sep}EvoSuiteResult" //TODO change to kex

            Path(generatedTestsData.resultPath).createDirectories()

            //TODO cmd should have cases for codeType, which is just hardcoded to CodeType.CLASS here
            val jvmArgs = mutableListOf<String>()

            //TODO log an error and exit if path doesn't exist
            val modulesFile =
                KexProcessManager::class.java.classLoader.getResourceAsStream("properties/kex/modules.info")
                    ?.let { BufferedReader(InputStreamReader(it)) }
            if (modulesFile == null) {
                kexErrorManager.errorProcess("testspark bug: modules file used for jvm args of kex doesn't exist", project, errorMonitor)
            } else {
                modulesFile.lines().forEach() { line ->
                    val split = line.split("--add-opens")
                    if (split.size > 1) {
                        jvmArgs.addAll(split.subList(1, split.size))
                    }
                }
            }
            jvmArgs.add("--illegal-access=warn")

            val cmd = mutableListOf<String>(
                "java", //TODO Use project's java not system java. Add >v8 check
                "-Xmx8g", //TODO 8g heapsize in properties bundle
                "-Djava.security.manager",
                "-Djava.security.policy==$kexPolicy",
                "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
            )

            cmd.addAll(jvmArgs)
            cmd.add("-jar $kexPath")
            cmd.add("--classpath $projectClassPath")
            cmd.add("--target $classFQN")
            cmd.add("--output $resultName")
            cmd.add("--mode concolic") //TODO make user option in settings

            val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
            log.info("Starting EvoSuite with arguments: $cmdString")

            val kexProcess = GeneralCommandLine(cmd)
            kexProcess.charset = Charset.forName("UTF-8")
            kexProcess.setWorkDirectory(projectPath)
            val handler = OSProcessHandler(kexProcess)


            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (ToolUtils.isProcessStopped(errorMonitor, indicator)) {
                        handler.destroyProcess()
                        return
                    }
                }
            })

            handler.startNotify()

            if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

            val gson = Gson()
            val reader = JsonReader(FileReader(resultName))

            val testGenerationResult: CompactReport = gson.fromJson(reader, CompactReport::class.java)

            ToolUtils.saveData(
                project,
                IJReport(testGenerationResult),
                ToolUtils.getPackageFromTestSuiteCode(testGenerationResult.testSuiteCode),
                ToolUtils.getImportsCodeFromTestSuiteCode(testGenerationResult.testSuiteCode, classFQN),
                projectContext.fileUrlAsString!!,
                generatedTestsData,
            )


                } catch (e: Exception) {
            // TODO remove hardcoded string
            kexErrorManager.errorProcess("An Exception occurred while executing Kex".format(e.message), project, errorMonitor)
            e.printStackTrace()
        }

        return UIContext(projectContext, generatedTestsData, StandardRequestManagerFactory(project).getRequestManager(project), errorMonitor)
    }

}