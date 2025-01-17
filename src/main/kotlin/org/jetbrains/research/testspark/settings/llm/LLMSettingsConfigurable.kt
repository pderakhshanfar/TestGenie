package org.jetbrains.research.testspark.settings.llm

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.research.testspark.bundles.llm.LLMMessagesBundle
import org.jetbrains.research.testspark.data.llm.JsonEncoding
import org.jetbrains.research.testspark.helpers.PromptParserHelper
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.settings.template.SettingsConfigurable
import javax.swing.JComponent

/**
 * This class allows to configure some LLM-related settings via the Large Language Model page in the Settings dialog,
 *   observes the changes and manages the UI and state.
 */
class LLMSettingsConfigurable(private val project: Project) : SettingsConfigurable {
    private val llmSettingsState: LLMSettingsState
        get() = project.getService(LLMSettingsService::class.java).state

    private var settingsComponent: LLMSettingsComponent? = null

    /**
     * Creates a settings component that holds the panel with the settings entries, and returns this panel
     *
     * @return the panel used for displaying settings
     */
    override fun createComponent(): JComponent? {
        settingsComponent = LLMSettingsComponent(project)
        return settingsComponent!!.panel
    }

    /**
     * Sets the stored state values to the corresponding UI components. This method is called immediately after `createComponent` method.
     */
    override fun reset() {
        for (index in settingsComponent!!.llmPlatforms.indices) {
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.openAIName) {
                settingsComponent!!.llmPlatforms[index].token = llmSettingsState.openAIToken
                settingsComponent!!.llmPlatforms[index].model = llmSettingsState.openAIModel
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.grazieName) {
                settingsComponent!!.llmPlatforms[index].token = llmSettingsState.grazieToken
                settingsComponent!!.llmPlatforms[index].model = llmSettingsState.grazieModel
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.huggingFaceName) {
                settingsComponent!!.llmPlatforms[index].token = llmSettingsState.huggingFaceToken
                settingsComponent!!.llmPlatforms[index].model = llmSettingsState.huggingFaceModel
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.geminiName) {
                settingsComponent!!.llmPlatforms[index].token = llmSettingsState.geminiToken
                settingsComponent!!.llmPlatforms[index].model = llmSettingsState.geminiModel
            }
        }
        settingsComponent!!.currentLLMPlatformName = llmSettingsState.currentLLMPlatformName
        settingsComponent!!.maxLLMRequest = llmSettingsState.maxLLMRequest
        settingsComponent!!.maxPolyDepth = llmSettingsState.maxPolyDepth
        settingsComponent!!.maxInputParamsDepth = llmSettingsState.maxInputParamsDepth
        settingsComponent!!.classPrompts = llmSettingsState.classPrompts
        settingsComponent!!.methodPrompts = llmSettingsState.methodPrompts
        settingsComponent!!.linePrompts = llmSettingsState.linePrompts
        settingsComponent!!.classPromptNames = llmSettingsState.classPromptNames
        settingsComponent!!.methodPromptNames = llmSettingsState.methodPromptNames
        settingsComponent!!.linePromptNames = llmSettingsState.linePromptNames
        settingsComponent!!.classCurrentDefaultPromptIndex = llmSettingsState.classCurrentDefaultPromptIndex
        settingsComponent!!.methodCurrentDefaultPromptIndex = llmSettingsState.methodCurrentDefaultPromptIndex
        settingsComponent!!.lineCurrentDefaultPromptIndex = llmSettingsState.lineCurrentDefaultPromptIndex
        settingsComponent!!.junitVersion = llmSettingsState.junitVersion
        settingsComponent!!.junitVersionPriorityCheckBoxSelected = llmSettingsState.junitVersionPriorityCheckBoxSelected
        settingsComponent!!.llmSetupCheckBoxSelected = llmSettingsState.llmSetupCheckBoxSelected
        settingsComponent!!.provideTestSamplesCheckBoxSelected = llmSettingsState.provideTestSamplesCheckBoxSelected
        settingsComponent!!.defaultLLMRequests = llmSettingsState.defaultLLMRequests

        settingsComponent!!.updateTokenAndModel()
    }

    /**
     * Checks if the values of the entries in the settings state are different from the persisted values of these entries.
     *
     * @return whether any setting has been modified
     */
    override fun isModified(): Boolean {
        var modified = false
        for (index in settingsComponent!!.llmPlatforms.indices) {
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.openAIName) {
                modified = modified or (settingsComponent!!.llmPlatforms[index].token != llmSettingsState.openAIToken)
                modified = modified or (settingsComponent!!.llmPlatforms[index].model != llmSettingsState.openAIModel)
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.grazieName) {
                modified = modified or (settingsComponent!!.llmPlatforms[index].token != llmSettingsState.grazieToken)
                modified = modified or (settingsComponent!!.llmPlatforms[index].model != llmSettingsState.grazieModel)
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.huggingFaceName) {
                modified = modified or (settingsComponent!!.llmPlatforms[index].token != llmSettingsState.huggingFaceToken)
                modified = modified or (settingsComponent!!.llmPlatforms[index].model != llmSettingsState.huggingFaceModel)
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.geminiName) {
                modified = modified or (settingsComponent!!.llmPlatforms[index].token != llmSettingsState.geminiToken)
                modified = modified or (settingsComponent!!.llmPlatforms[index].model != llmSettingsState.geminiModel)
            }
        }
        modified = modified or (settingsComponent!!.currentLLMPlatformName != llmSettingsState.currentLLMPlatformName)
        modified = modified or (settingsComponent!!.maxLLMRequest != llmSettingsState.maxLLMRequest)
        modified = modified or (settingsComponent!!.maxPolyDepth != llmSettingsState.maxPolyDepth)
        modified = modified or (settingsComponent!!.maxInputParamsDepth != llmSettingsState.maxInputParamsDepth)

        modified = modified or (settingsComponent!!.classPrompts != llmSettingsState.classPrompts)
        modified = modified or (settingsComponent!!.methodPrompts != llmSettingsState.methodPrompts)
        modified = modified or (settingsComponent!!.linePrompts != llmSettingsState.linePrompts)

        modified = modified or (settingsComponent!!.classPromptNames != llmSettingsState.classPromptNames)
        modified = modified or (settingsComponent!!.methodPromptNames != llmSettingsState.methodPromptNames)
        modified = modified or (settingsComponent!!.linePromptNames != llmSettingsState.linePromptNames)

        modified =
            modified or (settingsComponent!!.classCurrentDefaultPromptIndex != llmSettingsState.classCurrentDefaultPromptIndex)
        modified =
            modified or (settingsComponent!!.methodCurrentDefaultPromptIndex != llmSettingsState.methodCurrentDefaultPromptIndex)
        modified =
            modified or (settingsComponent!!.lineCurrentDefaultPromptIndex != llmSettingsState.lineCurrentDefaultPromptIndex)

        // junit version
        modified = modified or (settingsComponent!!.junitVersion != llmSettingsState.junitVersion)
        modified = modified or (settingsComponent!!.junitVersionPriorityCheckBoxSelected != llmSettingsState.junitVersionPriorityCheckBoxSelected)

        modified = modified or (settingsComponent!!.llmSetupCheckBoxSelected != llmSettingsState.llmSetupCheckBoxSelected)
        modified =
            modified or (settingsComponent!!.provideTestSamplesCheckBoxSelected != llmSettingsState.provideTestSamplesCheckBoxSelected)

        modified = modified or (settingsComponent!!.defaultLLMRequests != llmSettingsState.defaultLLMRequests)

        return modified
    }

    /**
     * Persists the modified state after a user hit Apply button.
     */
    override fun apply() {
        if (!PromptParserHelper.isPromptValid(JsonEncoding.decode(settingsComponent!!.classPrompts)[settingsComponent!!.classCurrentDefaultPromptIndex]) ||
            !PromptParserHelper.isPromptValid(JsonEncoding.decode(settingsComponent!!.methodPrompts)[settingsComponent!!.methodCurrentDefaultPromptIndex]) ||
            !PromptParserHelper.isPromptValid(JsonEncoding.decode(settingsComponent!!.linePrompts)[settingsComponent!!.lineCurrentDefaultPromptIndex])
        ) {
            Messages.showErrorDialog(
                LLMMessagesBundle.get("defaultPromptIsNotValidMessage"),
                LLMMessagesBundle.get("defaultPromptIsNotValidTitle"),
            )
            return
        }
        for (index in settingsComponent!!.llmPlatforms.indices) {
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.openAIName) {
                llmSettingsState.openAIToken = settingsComponent!!.llmPlatforms[index].token
                llmSettingsState.openAIModel = settingsComponent!!.llmPlatforms[index].model
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.grazieName) {
                llmSettingsState.grazieToken = settingsComponent!!.llmPlatforms[index].token
                llmSettingsState.grazieModel = settingsComponent!!.llmPlatforms[index].model
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.huggingFaceName) {
                llmSettingsState.huggingFaceToken = settingsComponent!!.llmPlatforms[index].token
                llmSettingsState.huggingFaceModel = settingsComponent!!.llmPlatforms[index].model
            }
            if (settingsComponent!!.llmPlatforms[index].name == llmSettingsState.geminiName) {
                llmSettingsState.geminiToken = settingsComponent!!.llmPlatforms[index].token
                llmSettingsState.geminiModel = settingsComponent!!.llmPlatforms[index].model
            }
        }
        llmSettingsState.currentLLMPlatformName = settingsComponent!!.currentLLMPlatformName
        llmSettingsState.maxLLMRequest = settingsComponent!!.maxLLMRequest
        llmSettingsState.maxPolyDepth = settingsComponent!!.maxPolyDepth
        llmSettingsState.maxInputParamsDepth = settingsComponent!!.maxInputParamsDepth
        llmSettingsState.classPrompts = settingsComponent!!.classPrompts
        llmSettingsState.methodPrompts = settingsComponent!!.methodPrompts
        llmSettingsState.linePrompts = settingsComponent!!.linePrompts
        llmSettingsState.classPromptNames = settingsComponent!!.classPromptNames
        llmSettingsState.methodPromptNames = settingsComponent!!.methodPromptNames
        llmSettingsState.linePromptNames = settingsComponent!!.linePromptNames
        llmSettingsState.classCurrentDefaultPromptIndex = settingsComponent!!.classCurrentDefaultPromptIndex
        llmSettingsState.methodCurrentDefaultPromptIndex = settingsComponent!!.methodCurrentDefaultPromptIndex
        llmSettingsState.lineCurrentDefaultPromptIndex = settingsComponent!!.lineCurrentDefaultPromptIndex
        llmSettingsState.junitVersion = settingsComponent!!.junitVersion
        llmSettingsState.junitVersionPriorityCheckBoxSelected = settingsComponent!!.junitVersionPriorityCheckBoxSelected
        llmSettingsState.defaultLLMRequests = settingsComponent!!.defaultLLMRequests
        llmSettingsState.llmSetupCheckBoxSelected = settingsComponent!!.llmSetupCheckBoxSelected
        llmSettingsState.provideTestSamplesCheckBoxSelected = settingsComponent!!.provideTestSamplesCheckBoxSelected
        llmSettingsState.defaultLLMRequests = settingsComponent!!.defaultLLMRequests
    }

    /**
     * Returns the displayed name of the Settings tab.
     *
     * @return the name displayed in the menu (settings)
     */
    override fun getDisplayName(): String {
        return "TestSpark"
    }

    /**
     * Disposes the UI resources. It is called when a user closes the Settings dialog.
     */
    override fun disposeUIResources() {
        settingsComponent = null
    }
}
