package org.jetbrains.research.testspark.settings.llm

import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.research.testspark.bundles.llm.LLMDefaultsBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.data.JUnitVersionConverter

/**
 * This class is the actual data class that stores the values of the LLM Settings entries.
 */
data class LLMSettingsState(
    var openAIName: String = DefaultLLMSettingsState.openAIName,
    var openAIToken: String = DefaultLLMSettingsState.openAIToken,
    var openAIModel: String = DefaultLLMSettingsState.openAIModel,
    var grazieName: String = DefaultLLMSettingsState.grazieName,
    var grazieToken: String = DefaultLLMSettingsState.grazieToken,
    var grazieModel: String = DefaultLLMSettingsState.grazieModel,
    var huggingFaceName: String = DefaultLLMSettingsState.huggingFaceName,
    var huggingFaceToken: String = DefaultLLMSettingsState.huggingFaceToken,
    var huggingFaceModel: String = DefaultLLMSettingsState.huggingFaceModel,
    var geminiName: String = DefaultLLMSettingsState.geminiName,
    var geminiToken: String = DefaultLLMSettingsState.geminiToken,
    var geminiModel: String = DefaultLLMSettingsState.geminiModel,
    var currentLLMPlatformName: String = DefaultLLMSettingsState.currentLLMPlatformName,
    var maxLLMRequest: Int = DefaultLLMSettingsState.maxLLMRequest,
    var maxInputParamsDepth: Int = DefaultLLMSettingsState.maxInputParamsDepth,
    var maxPolyDepth: Int = DefaultLLMSettingsState.maxPolyDepth,
    var classPrompts: String = DefaultLLMSettingsState.classPrompts,
    var methodPrompts: String = DefaultLLMSettingsState.methodPrompts,
    var linePrompts: String = DefaultLLMSettingsState.linePrompts,
    var classPromptNames: String = DefaultLLMSettingsState.classPromptNames,
    var methodPromptNames: String = DefaultLLMSettingsState.methodPromptNames,
    var linePromptNames: String = DefaultLLMSettingsState.linePromptNames,
    var classCurrentDefaultPromptIndex: Int = DefaultLLMSettingsState.classCurrentDefaultPromptIndex,
    var methodCurrentDefaultPromptIndex: Int = DefaultLLMSettingsState.methodCurrentDefaultPromptIndex,
    var lineCurrentDefaultPromptIndex: Int = DefaultLLMSettingsState.lineCurrentDefaultPromptIndex,
    var defaultLLMRequests: String = DefaultLLMSettingsState.defaultLLMRequests,
    @OptionTag(converter = JUnitVersionConverter::class) var junitVersion: JUnitVersion = DefaultLLMSettingsState.junitVersion,
    var junitVersionPriorityCheckBoxSelected: Boolean = DefaultLLMSettingsState.junitVersionPriorityCheckBoxSelected,
    var provideTestSamplesCheckBoxSelected: Boolean = DefaultLLMSettingsState.provideTestSamplesCheckBoxSelected,
    var llmSetupCheckBoxSelected: Boolean = DefaultLLMSettingsState.llmSetupCheckBoxSelected,
) {

    /**
     * Default values of SettingsLLMState.
     */
    object DefaultLLMSettingsState {
        val openAIName: String = LLMDefaultsBundle.get("openAIName")
        val openAIToken: String = LLMDefaultsBundle.get("openAIToken")
        val openAIModel: String = LLMDefaultsBundle.get("openAIModel")
        val grazieName: String = LLMDefaultsBundle.get("grazieName")
        val grazieToken: String = LLMDefaultsBundle.get("grazieToken")
        val grazieModel: String = LLMDefaultsBundle.get("grazieModel")
        val huggingFaceName: String = LLMDefaultsBundle.get("huggingFaceName")
        val huggingFaceToken: String = LLMDefaultsBundle.get("huggingFaceToken")
        val huggingFaceModel: String = LLMDefaultsBundle.get("huggingFaceModel")
        val geminiName: String = LLMDefaultsBundle.get("geminiName")
        val geminiToken: String = LLMDefaultsBundle.get("geminiToken")
        val geminiModel: String = LLMDefaultsBundle.get("geminiModel")
        var currentLLMPlatformName: String = LLMDefaultsBundle.get("openAIName")
        val maxLLMRequest: Int = LLMDefaultsBundle.get("maxLLMRequest").toInt()
        val maxInputParamsDepth: Int = LLMDefaultsBundle.get("maxInputParamsDepth").toInt()
        val maxPolyDepth: Int = LLMDefaultsBundle.get("maxPolyDepth").toInt()
        val classPrompts: String = LLMDefaultsBundle.get("classPrompt")
        val methodPrompts: String = LLMDefaultsBundle.get("methodPrompt")
        val linePrompts: String = LLMDefaultsBundle.get("linePrompt")
        var classPromptNames = LLMDefaultsBundle.get("classPromptName")
        var methodPromptNames = LLMDefaultsBundle.get("methodPromptName")
        var linePromptNames = LLMDefaultsBundle.get("linePromptName")
        var classCurrentDefaultPromptIndex = LLMDefaultsBundle.get("classCurrentDefaultPromptIndex").toInt()
        var methodCurrentDefaultPromptIndex = LLMDefaultsBundle.get("methodCurrentDefaultPromptIndex").toInt()
        var lineCurrentDefaultPromptIndex = LLMDefaultsBundle.get("lineCurrentDefaultPromptIndex").toInt()
        val defaultLLMRequests: String = LLMDefaultsBundle.get("defaultLLMRequests")
        val junitVersion: JUnitVersion = JUnitVersion.JUnit5
        val junitVersionPriorityCheckBoxSelected: Boolean = LLMDefaultsBundle.get("junitVersionPriority").toBoolean()
        val provideTestSamplesCheckBoxSelected: Boolean = LLMDefaultsBundle.get("provideTestSamples").toBoolean()
        val llmSetupCheckBoxSelected: Boolean = LLMDefaultsBundle.get("llmSetup").toBoolean()
    }
}
