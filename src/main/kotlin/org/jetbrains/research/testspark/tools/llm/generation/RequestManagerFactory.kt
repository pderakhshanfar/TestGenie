package org.jetbrains.research.testspark.tools.llm.generation

import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.core.generation.llm.network.RequestManager
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.settings.llm.LLMSettingsState
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import org.jetbrains.research.testspark.tools.llm.generation.gemini.GeminiRequestManager
import org.jetbrains.research.testspark.tools.llm.generation.grazie.GrazieRequestManager
import org.jetbrains.research.testspark.tools.llm.generation.hf.HuggingFaceRequestManager
import org.jetbrains.research.testspark.tools.llm.generation.openai.OpenAIRequestManager

interface RequestManagerFactory {
    fun getRequestManager(project: Project): RequestManager
}

class StandardRequestManagerFactory(private val project: Project) : RequestManagerFactory {
    private val llmSettingsState: LLMSettingsState
        get() = project.getService(LLMSettingsService::class.java).state

    override fun getRequestManager(project: Project): RequestManager {
        return when (val platform = LlmSettingsArguments(project).currentLLMPlatformName()) {
            llmSettingsState.openAIName -> OpenAIRequestManager(project)
            llmSettingsState.grazieName -> GrazieRequestManager(project)
            llmSettingsState.huggingFaceName -> HuggingFaceRequestManager(project)
            llmSettingsState.geminiName -> GeminiRequestManager(project)
            else -> throw IllegalStateException("Unknown selected platform: $platform")
        }
    }
}
