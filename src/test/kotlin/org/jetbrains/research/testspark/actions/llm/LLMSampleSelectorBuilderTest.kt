package org.jetbrains.research.testspark.actions.llm

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.junit.jupiter.api.BeforeEach

class LLMSampleSelectorBuilderTest {

    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var openFile: PsiFile
    private lateinit var project: Project
    private lateinit var builder: LLMSampleSelectorBuilder

    @BeforeEach
    fun setUp() {
        val projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture> =
            IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("project")

        fixture = JavaTestFixtureFactory.getFixtureFactory()
            .createCodeInsightFixture(projectBuilder.fixture)
        fixture.setUp()

        addFilesFromJSONToFixture()

        project = fixture.project
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        builder = LLMSampleSelectorBuilder(project, SupportedLanguage.Java)
    }

    private fun addFilesFromJSONToFixture() {
        val jsonFileContent = getResourceAsText("/llm/sampleSelectorTestFiles.json")
        val type = object : TypeToken<Map<String, String>>() {}.type
        val fileMap = Gson().fromJson(jsonFileContent, type) as Map<String, String>
        fileMap.forEach { (fileName, fileContent) ->
            val psiFile = fixture.addFileToProject(fileName as String, fileContent as String)
            if (fileName == "test/dummy/CarTest.java") {
                openFile = psiFile
            }
        }
    }

    private fun getResourceAsText(path: String): String? =
        object {}.javaClass.getResource(path)?.readText()

//    @Test
//    fun collectTestSampleForCurrentFile() {
//        runReadAction { builder.collectTestSamplesForCurrentFile(openFile.virtualFile) }
//    }

//    @Test
//    fun collectTestSamples() {
//        runReadAction { builder.collectTestSamples(project) }
//    }
}
