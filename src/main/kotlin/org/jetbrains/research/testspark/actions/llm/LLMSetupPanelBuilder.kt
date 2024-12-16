package org.jetbrains.research.testspark.actions.llm

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.research.testspark.actions.template.PanelBuilder
import org.jetbrains.research.testspark.bundles.llm.LLMLabelsBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.data.llm.JsonEncoding
import org.jetbrains.research.testspark.data.llm.PromptEditorType
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.display.custom.JUnitCombobox
import org.jetbrains.research.testspark.helpers.LLMHelper
import org.jetbrains.research.testspark.helpers.PromptParserHelper
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.settings.llm.LLMSettingsState
import org.jetbrains.research.testspark.tools.llm.generation.LLMPlatform
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class LLMSetupPanelBuilder(e: AnActionEvent, private val project: Project) : PanelBuilder {
    private val llmSettingsState: LLMSettingsState
        get() = project.getService(LLMSettingsService::class.java).state

    // init components
    private val defaultModulesArray = arrayOf("")
    private var modelSelector = ComboBox(defaultModulesArray)
    private var llmUserTokenField = JTextField(30)
    private var platformSelector = ComboBox(arrayOf(llmSettingsState.openAIName, llmSettingsState.huggingFaceName, llmSettingsState.geminiName))
    private val backLlmButton = JButton(PluginLabelsBundle.get("back"))
    private val okLlmButton = JButton(PluginLabelsBundle.get("next"))
    private val junitSelector = JUnitCombobox(e)

    private val llmPlatforms: List<LLMPlatform> = LLMHelper.getLLLMPlatforms()

    private var promptEditorType: PromptEditorType = PromptEditorType.CLASS
    private val promptTemplateNames = ComboBox(arrayOf(""))
    private var prompts: String = llmSettingsState.classPrompts
    private var promptNames: String = llmSettingsState.classPromptNames
    private var currentDefaultPromptIndex: Int = llmSettingsState.classCurrentDefaultPromptIndex
    private var showCodeJLabel: JLabel = JLabel(TestSparkIcons.showCode)

    init {
        LLMHelper.addLLMPanelListeners(
            platformSelector,
            modelSelector,
            llmUserTokenField,
            llmPlatforms,
            llmSettingsState,
        )

        addListeners()
    }

    override fun getTitlePanel(): JPanel {
        val textTitle = JLabel(PluginLabelsBundle.get("llmSetup"))
        textTitle.font = Font("Monochrome", Font.BOLD, 20)

        val titlePanel = JPanel()
        titlePanel.add(textTitle)

        return titlePanel
    }

    override fun getMiddlePanel(): JPanel {
        LLMHelper.stylizeMainComponents(platformSelector, modelSelector, llmUserTokenField, llmPlatforms, llmSettingsState)

        updatePromptSelectionPanel()

        return FormBuilder.createFormBuilder()
            .setFormLeftIndent(10)
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("llmPlatform")),
                platformSelector,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("llmToken")),
                llmUserTokenField,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("model")),
                modelSelector,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("junitVersion")),
                junitSelector,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("selectPrompt")),
                getPromptSelectionPanel(),
                10,
                false,
            )
            .panel
    }

    override fun getBottomPanel(): JPanel {
        val bottomPanel = JPanel()

        backLlmButton.isOpaque = false
        backLlmButton.isContentAreaFilled = false
        bottomPanel.add(backLlmButton)

        okLlmButton.isOpaque = false
        okLlmButton.isContentAreaFilled = false
        if (!llmSettingsState.provideTestSamplesCheckBoxSelected) {
            okLlmButton.text = PluginLabelsBundle.get("ok")
        }
        bottomPanel.add(okLlmButton)

        return bottomPanel
    }

    override fun getBackButton() = backLlmButton

    override fun getFinishedButton() = okLlmButton

    override fun applyUpdates() {
        llmSettingsState.currentLLMPlatformName = platformSelector.selectedItem!!.toString()
        for (index in llmPlatforms.indices) {
            if (llmPlatforms[index].name == llmSettingsState.openAIName) {
                llmSettingsState.openAIToken = llmPlatforms[index].token
                llmSettingsState.openAIModel = llmPlatforms[index].model
            }
            if (llmPlatforms[index].name == llmSettingsState.grazieName) {
                llmSettingsState.grazieToken = llmPlatforms[index].token
                llmSettingsState.grazieModel = llmPlatforms[index].model
            }
            if (llmPlatforms[index].name == llmSettingsState.huggingFaceName) {
                llmSettingsState.huggingFaceToken = llmPlatforms[index].token
                llmSettingsState.huggingFaceModel = llmPlatforms[index].model
            }
            if (llmPlatforms[index].name == llmSettingsState.geminiName) {
                llmSettingsState.geminiToken = llmPlatforms[index].token
                llmSettingsState.geminiModel = llmPlatforms[index].model
            }
        }
        llmSettingsState.junitVersion = junitSelector.selectedItem!! as JUnitVersion

        when (promptEditorType) {
            PromptEditorType.CLASS -> llmSettingsState.classCurrentDefaultPromptIndex = JsonEncoding.decode(promptNames).indexOf(promptTemplateNames.selectedItem!!.toString())
            PromptEditorType.METHOD -> llmSettingsState.methodCurrentDefaultPromptIndex = JsonEncoding.decode(promptNames).indexOf(promptTemplateNames.selectedItem!!.toString())
            PromptEditorType.LINE -> llmSettingsState.lineCurrentDefaultPromptIndex = JsonEncoding.decode(promptNames).indexOf(promptTemplateNames.selectedItem!!.toString())
        }
    }

    /**
     * Set promptEditorType variable.
     */
    fun setPromptEditorType(codeType: String) {
        if (codeType.contains("class") || codeType.contains("interface")) promptEditorType = PromptEditorType.CLASS
        if (codeType.contains("method") || codeType.contains("constructor")) promptEditorType = PromptEditorType.METHOD
        if (codeType.contains("line")) promptEditorType = PromptEditorType.LINE

        updatePromptSelectionPanel()
    }

    /**
     * Update prompts, promptNames, currentDefaultPromptIndex vars.
     */
    private fun updatePromptSelectionPanel() {
        when (promptEditorType) {
            PromptEditorType.CLASS -> {
                prompts = llmSettingsState.classPrompts
                promptNames = llmSettingsState.classPromptNames
                currentDefaultPromptIndex = llmSettingsState.classCurrentDefaultPromptIndex
            }
            PromptEditorType.METHOD -> {
                prompts = llmSettingsState.methodPrompts
                promptNames = llmSettingsState.methodPromptNames
                currentDefaultPromptIndex = llmSettingsState.methodCurrentDefaultPromptIndex
            }
            PromptEditorType.LINE -> {
                prompts = llmSettingsState.linePrompts
                promptNames = llmSettingsState.linePromptNames
                currentDefaultPromptIndex = llmSettingsState.lineCurrentDefaultPromptIndex
            }
        }

        val names = JsonEncoding.decode(promptNames)
        var normalizedNames = arrayOf<String>()
        for (i in names.indices) {
            val prompt = JsonEncoding.decode(prompts)[i]
            if (PromptParserHelper.isPromptValid(prompt)) {
                normalizedNames += names[i]
            }
        }

        promptTemplateNames.model = DefaultComboBoxModel(normalizedNames)
        promptTemplateNames.selectedItem = names[currentDefaultPromptIndex]
    }

    /**
     * @return prompt selection panel
     */
    private fun getPromptSelectionPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))

        panel.add(promptTemplateNames)
        panel.add(showCodeJLabel)

        return panel
    }

    /**
     * Add listener to a promptTemplateNames.
     */
    private fun addListeners() {
        promptTemplateNames.addActionListener {
            showCodeJLabel.toolTipText = JsonEncoding.decode(prompts)[JsonEncoding.decode(promptNames).indexOf(promptTemplateNames.selectedItem!!.toString())]
        }
    }
}
