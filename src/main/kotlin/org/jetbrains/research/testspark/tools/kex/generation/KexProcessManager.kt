package org.jetbrains.research.testspark.tools.kex.generation

import org.jetbrains.research.testspark.tools.ToolUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
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
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

    private var kexPath =
        "$kexHome${ToolUtils.sep}kex-runner${ToolUtils.sep}target${ToolUtils.sep}kex-runner-$kexVersion-jar-with-dependencies.jar"


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

            val cmd = mutableListOf<String>(
                "python3",
                "./kex.py",
                "--classpath",
                "$projectClassPath/target/classes", // TODO how to reliably get the path to 'root of compiled files'? (only that works)
                "--target",
                classFQN,
                "--output",
                resultName,
                "--mode",
                "concolic"
            )

            val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
            log.info("Starting Kex with arguments: $cmdString")

            ensureKexExists()

            var kexOutStr: String = ""
            try {
                val proc = ProcessBuilder(cmd)
                    .directory(File(kexHome))
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()

                proc.waitFor(kexProcessTimeout, TimeUnit.SECONDS)
                kexOutStr = proc.inputStream.bufferedReader().readText()


                log.info("OUTPUT FROM KEX:\n $kexOutStr")

            } catch (e: IOException) {
                e.printStackTrace()
            }

            System.err.println("PRINTING from STDERR:\n $kexOutStr")

            log.info("Save generated test suite and test cases into the project workspace")
            val report = IJReport()
            val imports = mutableSetOf<String>()

            val generatedTestsDir = File(
                "$resultName/tests/${
                    classFQN.substringBeforeLast('.').replace('.', '/')
                }"
            ) //TODO does this include the .java?
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
                kexErrorManager.errorProcess(
                    "Generated tests don't exist. Must have had a problem running Kex",
                    project,
                    errorMonitor
                )
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

    private fun ensureKexExists() {
        val kexDir = File(kexHome)
        if (!kexDir.exists()) {
            kexDir.mkdirs()
        }

        val requiredFiles = listOf(
            "$kexHome/kex.ini",
            "$kexHome/kex.py",
            "$kexHome/kex.policy",
            "$kexHome/runtime-deps/modules.info",
            kexPath
        )
        if (requiredFiles.map { File(it).exists() }.all { it }) {
            log.info("Specified kex jar found, skipping update")
            return
        }

        log.info("Kex executable and helper files not found, downloading Kex")
        val downloadUrl =
            "https://github.com/vorpal-research/kex/releases/download/$kexVersion/kex-$kexVersion.zip"
        val stream =
            try {
                URL(downloadUrl).openStream()
            } catch (e: Exception) {
                log.error("Error fetching latest kex custom release - $e")
                return //TODO fail test generation here
            }

        //TODO this can fail unexpectedly if a file with the same name as a required directory exists
        // inside the given kexHome path.
        ZipInputStream(stream).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    val file = File("${kexHome}/${entry.name}")
                    file.parentFile.mkdirs() // makes any directories required
                    file.outputStream().use { output ->
                        zipInputStream.copyTo(output)
                    }
                }
        }
        log.info("Latest kex project successfully downloaded")
    }
}