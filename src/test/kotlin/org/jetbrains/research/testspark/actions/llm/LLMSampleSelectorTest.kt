package org.jetbrains.research.testspark.actions.llm

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.containers.stream
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.lifecycle.BeforeTry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class LLMSampleSelectorTest {

    private lateinit var selector: LLMSampleSelector
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var openFile: PsiJavaFile

    private val sampleTestCode = """
        public class TestSample {
            ${LLMSampleSelector.DEFAULT_TEST_CODE}
        }
    """.trimIndent()

    @BeforeEach
    @BeforeTry
    fun setUpSelector() {
        selector = LLMSampleSelector()
    }

    @BeforeEach
    fun setUpProject() {
        val projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture> =
            IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("project")

        fixture = JavaTestFixtureFactory.getFixtureFactory()
            .createCodeInsightFixture(projectBuilder.fixture)
        fixture.setUp()

        addFilesFromJSONToFixture()
    }

    private fun classFromFile(psiJavaFile: PsiJavaFile): PsiClass {
        return runReadAction {
            psiJavaFile.classes[
                psiJavaFile.classes.stream().map { it.name }.toArray()
                    .indexOf(psiJavaFile.name.removeSuffix(".java")),
            ]
        }
    }

    private fun methodsFromClass(psiClass: PsiClass): Array<out PsiMethod> {
        return psiClass.methods
    }

    private fun addFilesFromJSONToFixture() {
        val jsonFileContent = getResourceAsText("/llm/sampleSelectorTestFiles.json")
        val type = object : TypeToken<Map<String, String>>() {}.type
        val fileMap = Gson().fromJson(jsonFileContent, type) as Map<String, String>
        fileMap.forEach { (fileName, fileContent) ->
            val psiFile = fixture.addFileToProject(fileName, fileContent)
            if (fileName == "test/dummy/CarTest.java") {
                openFile = psiFile as PsiJavaFile
            }
        }
    }

    private fun getResourceAsText(path: String): String? =
        object {}.javaClass.getResource(path)?.readText()

    @Test
    fun `test the initial test names`() {
        val expected = mutableListOf(LLMSampleSelector.DEFAULT_TEST_NAME)
        val actual = selector.getTestNames()
        assertContentEquals(expected, actual)
    }

    @Test
    fun `test the initial test code`() {
        val expected = mutableListOf(sampleTestCode)
        val actual = selector.getInitialTestCodes()
        assertContentEquals(expected, actual)
    }

    @Property
    fun `test the append of test sample code`(@ForAll index: Int, @ForAll code: String) {
        selector.appendTestSampleCode(index, code)
        val expected = "Test sample number ${index + 1}\n```\n$code\n```\n"
        val actual = selector.getTestSamplesCode()
        assertEquals(expected, actual)
    }

    @Test
    fun `test the class retrieval from a Java file`() {
        val expectedName = "CarTest"
        val actual = runReadAction { selector.retrievePsiClass(openFile) }
        assertEquals(expectedName, actual.name)
    }

    @Test
    fun `test the import-statement retrieval`() {
        val expected = """
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            import dummy.*;
        """.trimIndent()
        val actual = runReadAction {
            val file = openFile
            selector.retrieveImportStatements(file, classFromFile(file))
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `test the create test-sample class`() {
        val expected = "import org.junit.jupiter.api.Test;\n\n$sampleTestCode"
        val actual = selector.createTestSampleClass(
            "import org.junit.jupiter.api.Test;",
            LLMSampleSelector.DEFAULT_TEST_CODE,
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `test the expected method name`() {
        val expected = "<html>dummy.CarTest#testCar</html>"
        val cls = classFromFile(openFile)
        val actual = selector.createMethodName(cls, methodsFromClass(cls)[0])
        assertEquals(expected, actual)
    }
}
