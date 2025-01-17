package org.jetbrains.research.testspark.tools.llm.generation.gemini

import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.llm.LLMDefaultsBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.TestSuiteParser
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.helpers.LLMHelper
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.settings.llm.LLMSettingsState
import org.jetbrains.research.testspark.tools.llm.generation.JUnitTestsAssembler
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GeminiRequestManagerTest {

    private lateinit var project: Project
    private lateinit var testsAssembler: TestsAssembler

    private val apiKey: String = System.getenv("GOOGLE_API_KEY")!!

    private val indicator = mock(CustomProgressIndicator::class.java)
    private val errorMonitor = mock(ErrorMonitor::class.java)

    @BeforeEach
    fun setUp() {
        project = mock(Project::class.java)
        val settingsService = mock(LLMSettingsService::class.java)
        val settingsState = mock(LLMSettingsState::class.java)
        `when`(settingsState.currentLLMPlatformName).thenReturn(LLMDefaultsBundle.get("geminiName"))
        `when`(settingsState.geminiName).thenReturn(LLMDefaultsBundle.get("geminiName"))
        `when`(settingsState.geminiToken).thenReturn(apiKey)
        `when`(settingsState.geminiModel).thenReturn("gemini-1.5-flash")
        `when`(settingsService.state).thenReturn(settingsState)
        `when`(project.getService(LLMSettingsService::class.java)).thenReturn(settingsService)

        testsAssembler = JUnitTestsAssembler(
            indicator,
            mock(TestGenerationData::class.java),
            mock(TestSuiteParser::class.java),
            JUnitVersion.JUnit5,
        )
    }

    @Test
    fun `test request manager implementation for Google Gemini`() {
        val manager = GeminiRequestManager(project)
        val prompt = """
            You are a Java tester.  Provide a test case that covers the following code snippet:
            
            ```java
            package com.example;
            public class Foo {
              public int sign(int x) {
                if (x > 0) return 1
                if (x < 0) return -1
                return 0
              }
            }
            ```
        """.trimIndent()
        manager.request(
            SupportedLanguage.Java,
            prompt,
            indicator,
            "com.example",
            testsAssembler,
            false,
            errorMonitor,
        )

        val result = manager.send(prompt, indicator, testsAssembler, errorMonitor)
        val llmResult = testsAssembler.getContent()
        assertNotNull(result)
        assertNotNull(llmResult)
    }

    @Test
    fun `test the retrieved Gemini models`() {
        val models = LLMHelper.getGeminiModels(apiKey)
        assertTrue(models.isNotEmpty())
    }
}
