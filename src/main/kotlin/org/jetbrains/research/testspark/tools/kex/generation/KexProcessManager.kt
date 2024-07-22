package org.jetbrains.research.testspark.tools.kex.generation

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.BlockStmt
import org.jetbrains.research.testspark.tools.ToolUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.research.testspark.bundles.kex.KexDefaultsBundle
import org.jetbrains.research.testspark.bundles.kex.KexMessagesBundle
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
import java.util.zip.ZipInputStream
import com.intellij.openapi.roots.ProjectRootManager
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

    private var kexExecPath =
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
            val resultName = "${generatedTestsData.resultPath}${ToolUtils.sep}KexResult"
            val projectSdk = ProjectRootManager.getInstance(project).projectSdk!!
            val javaExecPath = ToolUtils.osJoin(projectSdk.homePath!!, "bin", "java")

            val x = "\\d+".toRegex().find(projectSdk.versionString!!)!!.value.toInt()
            if (x < 8) {
                //TODO error
            }
            Path(generatedTestsData.resultPath).createDirectories()

            //TODO cmd should have cases for codeType, which is just hardcoded to CodeType.CLASS here


            ensureKexExists()

            val cmd = mutableListOf<String>(
                javaExecPath,
                "-Xmx8g", //TODO 8g heapsize in properties bundle
                "-Djava.security.manager",
                "-Djava.security.policy==$kexHome/kex.policy",
                "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
            )

            File("$kexHome/runtime-deps/modules.info").readLines().forEach { cmd.add("--add-opens"); cmd.add(it) }
            cmd.add("--illegal-access=warn")

            cmd.addAll(
                listOf(
                    "-jar", kexExecPath,
                    "--classpath", "$projectClassPath/target/classes", // TODO how to reliably get the path to 'root of class files', of the class for which tests are generated
                    "--target", classFQN,
                    "--output", resultName,
                    "--mode", "concolic" //TODO make user option in settings
                )
            )

            val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
            log.info("Starting Kex with arguments: $cmdString")

            try {
                val pb = ProcessBuilder(cmd)
                    .directory(File(kexHome))
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)

                pb.environment()["KEX_HOME"] = kexHome
                val proc = pb.start()
                proc.waitFor(kexProcessTimeout, TimeUnit.SECONDS)
                val kexOutStr = proc.inputStream.bufferedReader().readText()


                log.info("OUTPUT FROM KEX:\n $kexOutStr")
            System.err.println("PRINTING from STDERR:\n $kexOutStr")

            } catch (e: IOException) {
                e.printStackTrace()
            }

            log.info("Save generated test suite and test cases into the project workspace")
            val report = IJReport()
            val imports = mutableSetOf<String>()
            var packageStr = ""

            val generatedTestsDir = File(
                "$resultName/tests/${
                    classFQN.substringBeforeLast('.').replace('.', '/')
                }"
            )
            if (generatedTestsDir.exists() && generatedTestsDir.isDirectory) { //collect all generated tests into a report
                for ((index, file) in generatedTestsDir.listFiles()!!.withIndex()) {
                    val testCode = file.readText()


                    if (file.name.contains("Equality") || file.name.contains("Reflection")) {
                        // collecting all methods
                        generatedTestsData.otherInfo += "${getHelperClassBody(testCode)}\n"
                    } else {
                        //merge @before and @test annotated methods into a single method
                        report.testCaseList[index] =
                            TestCase(
                                index,
                                file.name.substringBefore('.'),
                                extractTestMethod(file, index).toString(),
                                setOf()
                            )
                    }
                    // extracting just imports out of the test code
                    // remove imports from helper classes
                    imports.addAll(ToolUtils.getImportsCodeFromTestSuiteCode(testCode, projectContext.classFQN!!)
                        .filterNot {
                            it.contains("import static org.example.EqualityUtils.*;")
                                    || it.contains("import static org.example.ReflectionUtils.*")
                        })
                    packageStr =
                        ToolUtils.getPackageFromTestSuiteCode(testCode) //TODO remove repeatedly setting with same value
                }
            } else {
                kexErrorManager.errorProcess(
                    KexMessagesBundle.get("TestsDontExist"),
                    project,
                    errorMonitor
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
        } catch (e: Exception) {
            kexErrorManager.errorProcess(
                KexMessagesBundle.get("KexErrorCommon").format(e.message),
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

    //TODO  This method could use the parser
    private fun getHelperClassBody(testCode: String) =
        (StaticJavaParser.parse(testCode).findFirst(ClassOrInterfaceDeclaration::class.java)
            .get()).toString().substringAfter('{').substringBeforeLast('}')

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
                .filterNot { it.isPublic }//drops the timeout
                .map { it.toString() }
                .map { StaticJavaParser.parseStatement(it) }
        )
        fields.addAll(methodStmts[1])
        fields.addAll(methodStmts[2])
        methods[2].setBody(BlockStmt(fields))
        methods[2].setName("test$id")
        return methods[2]
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
            kexExecPath
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