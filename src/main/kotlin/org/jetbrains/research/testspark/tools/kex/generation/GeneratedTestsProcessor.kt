package org.jetbrains.research.testspark.tools.kex.generation

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.Statement
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.kex.KexMessagesBundle
import org.jetbrains.research.testspark.core.data.TestCase
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.generation.llm.getImportsCodeFromTestSuiteCode
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.data.IJReport
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.kex.error.KexErrorManager
import java.io.File

class GeneratedTestsProcessor(
    private val project: Project,
    private val errorMonitor: ErrorMonitor,
    private val kexErrorManager: KexErrorManager,
) {

    companion object {
        const val EQUALITY_UTILS = "EqualityUtils"
        const val REFLECTION_UTILS = "ReflectionUtils"

        // can be any arbitrary name. used when no package is provided
        const val DEFAULT_PACKAGE_NAME = "example"
    }

    fun process(
        resultName: String,
        classFQN: String,
        generatedTestsData: TestGenerationData,
        projectContext: ProjectContext,
    ) {
        val report = IJReport()
        val imports = mutableSetOf<String>()
        val packageStr = classFQN.substringBeforeLast('.', missingDelimiterValue = DEFAULT_PACKAGE_NAME)

        val generatedTestsDir = File(
            ToolUtils.osJoin(resultName, "tests", packageStr.replace('.', '/')),
        )
        if (generatedTestsDir.exists() && generatedTestsDir.isDirectory) { // collect all generated tests into a report
            for ((index, file) in generatedTestsDir.listFiles()!!.withIndex()) {
                val testCode = file.readText()

                if (file.name.equals("$EQUALITY_UTILS.java") || file.name.equals("$REFLECTION_UTILS.java")) {
                    // collecting all methods
                    generatedTestsData.otherInfo += "${getHelperClassBody(testCode)}\n"
                } else {
                    // merge @before and @test annotated methods into a single method
                    val testMethod = extractTestMethod(file, index) ?: continue
                    report.testCaseList[index] =
                        TestCase(
                            index,
                            file.name.substringBefore('.'),
                            testMethod.toString(),
                            setOf(),
                        )
                }
                // extracting just imports out of the test code
                // remove any imports referring to the helper classes
                imports.addAll(
                    getImportsCodeFromTestSuiteCode(testCode, projectContext.classFQN!!)
                        .filterNot { it.contains(EQUALITY_UTILS) || it.contains(REFLECTION_UTILS) },
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
     * Precondition: there is only one class or interface per helper file (ie, EqualityUtils or ReflectionUtils)
     */
    private fun getHelperClassBody(testCode: String) =
        testCode.substringAfter('{').substringBeforeLast('}')

    /**
     * Merge the fields, @Before, @Test into a single @Test annotated method
     */
    private fun extractTestMethod(file: File, id: Int): MethodDeclaration? {
        val compilationUnit = StaticJavaParser.parse(file)
        val methods = compilationUnit.findAll(MethodDeclaration::class.java)
        val beforeAnnMethod = methods.getMethodWithAnnotation("@Before", file.name) ?: return null
        val testAnnMethod = methods.getMethodWithAnnotation("@Test", file.name) ?: return null

        val collatedMethodBody = NodeList<Statement>()

        // convert fields to local variables
        collatedMethodBody.addAll(
            compilationUnit.findAll(FieldDeclaration::class.java)
                .filter { it.annotations.isEmpty() } // drops the unwanted timeout field
                .map { it.setModifiers(NodeList()) } // remove all modifiers (including access specifiers like public and private)
                .map { it.toString() }
                .map { StaticJavaParser.parseStatement(it) },
        )
        collatedMethodBody.addAll(beforeAnnMethod.statements())
        collatedMethodBody.addAll(testAnnMethod.statements())
        testAnnMethod.setBody(BlockStmt(collatedMethodBody))
        testAnnMethod.setName("test$id")
        return testAnnMethod
    }

    private fun MethodDeclaration.statements(): NodeList<Statement>? =
        body.map { it.statements }.orElse(NodeList())

    private fun MutableList<MethodDeclaration>.getMethodWithAnnotation(ann: String, fileName: String): MethodDeclaration? =
        firstOrNull { it.annotations.map { it.toString() }.contains(ann) }.apply {
            if (this == null) {
                kexErrorManager.warningProcess(
                    """${KexMessagesBundle.get("unexpectedGeneratedTestStructure")}
                        There is no $ann annotated method in file $fileName
                    """.trimMargin(),
                    project,
                )
            }
        }
}
