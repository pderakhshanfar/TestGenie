package org.jetbrains.research.testspark.settings.llm

import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.FormBuilder
import org.jdesktop.swingx.JXTitledSeparator
import org.jetbrains.research.testspark.bundles.llm.LLMLabelsBundle
import org.jetbrains.research.testspark.bundles.llm.LLMSettingsBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.generation.llm.prompt.PromptKeyword
import org.jetbrains.research.testspark.data.llm.JsonEncoding
import org.jetbrains.research.testspark.data.llm.PromptEditorType
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.display.utils.IconButtonCreator
import org.jetbrains.research.testspark.helpers.LLMHelper
import org.jetbrains.research.testspark.helpers.PromptParserHelper
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.settings.template.SettingsComponent
import org.jetbrains.research.testspark.tools.llm.generation.LLMPlatform
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class LLMSettingsComponent(private val project: Project) : SettingsComponent {
    private val llmSettingsState: LLMSettingsState
        get() = project.getService(LLMSettingsService::class.java).state

    var panel: JPanel? = null

    // LLM Token
    private var llmUserTokenField = JTextField(30)

    // Models
    private var modelSelector = ComboBox(arrayOf(""))
    private var platformSelector = ComboBox(arrayOf(llmSettingsState.openAIName, llmSettingsState.huggingFaceName, llmSettingsState.geminiName))

    // Default LLM Requests
    private var defaultLLMRequestsSeparator =
        JXTitledSeparator(LLMLabelsBundle.get("defaultLLMRequestsSeparator"))
    private var commonDefaultLLMRequestsPanel = JPanel()
    private val defaultLLMRequestPanels = mutableListOf<JPanel>()
    private val addDefaultLLMRequestsButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT))

    // JUnit versions
    private var junitVersionSeparator = JXTitledSeparator(LLMLabelsBundle.get("junitVersion"))
    private val junitVersionPriorityCheckBox: JCheckBox = JCheckBox(LLMLabelsBundle.get("junitVersionPriorityCheckBox"), true)
    private var junitVersionSelector = ComboBox(JUnitVersion.entries.map { it }.toTypedArray())

    // Prompt Editor
    private var promptSeparator = JXTitledSeparator(LLMLabelsBundle.get("PromptSeparator"))

    private val promptClassTemplateFactory: PromptTemplateFactory = PromptTemplateFactory(llmSettingsState, PromptEditorType.CLASS)
    private val promptMethodTemplateFactory: PromptTemplateFactory = PromptTemplateFactory(llmSettingsState, PromptEditorType.METHOD)
    private val promptLineTemplateFactory: PromptTemplateFactory = PromptTemplateFactory(llmSettingsState, PromptEditorType.LINE)

    private var promptEditorTabbedPane = createTabbedPane()

    // Maximum number of LLM requests
    private var maxLLMRequestsField = JBIntSpinner(UINumericRange(llmSettingsState.maxLLMRequest, 1, 20))

    // The depth of input parameters used in class under tests
    private var maxInputParamsDepthField = JBIntSpinner(UINumericRange(llmSettingsState.maxInputParamsDepth, 1, 5))

    // Maximum polymorphism depth
    private var maxPolyDepthField = JBIntSpinner(UINumericRange(llmSettingsState.maxPolyDepth, 1, 5))

    private val provideTestSamplesCheckBox: JCheckBox =
        JCheckBox(LLMLabelsBundle.get("provideTestSamplesCheckBox"), true)

    private val llmSetupCheckBox: JCheckBox = JCheckBox(LLMLabelsBundle.get("llmSetupCheckBox"), true)

    val llmPlatforms: List<LLMPlatform> = LLMHelper.getLLLMPlatforms()

    var currentLLMPlatformName: String
        get() = platformSelector.item
        set(newAlg) {
            platformSelector.item = newAlg
        }

    var maxLLMRequest: Int
        get() = maxLLMRequestsField.number
        set(value) {
            maxLLMRequestsField.number = value
        }

    var maxInputParamsDepth: Int
        get() = maxInputParamsDepthField.number
        set(value) {
            maxInputParamsDepthField.number = value
        }

    var maxPolyDepth: Int
        get() = maxPolyDepthField.number
        set(value) {
            maxPolyDepthField.number = value
        }

    var classPrompts: String
        get() = promptClassTemplateFactory.getCommonPrompt()
        set(value) = promptClassTemplateFactory.setCommonPrompt(value)

    var methodPrompts: String
        get() = promptMethodTemplateFactory.getCommonPrompt()
        set(value) = promptMethodTemplateFactory.setCommonPrompt(value)

    var linePrompts: String
        get() = promptLineTemplateFactory.getCommonPrompt()
        set(value) = promptLineTemplateFactory.setCommonPrompt(value)

    var classPromptNames: String
        get() = promptClassTemplateFactory.getCommonName()
        set(value) = promptClassTemplateFactory.setCommonName(value)

    var methodPromptNames: String
        get() = promptMethodTemplateFactory.getCommonName()
        set(value) = promptMethodTemplateFactory.setCommonName(value)

    var linePromptNames: String
        get() = promptLineTemplateFactory.getCommonName()
        set(value) = promptLineTemplateFactory.setCommonName(value)

    var classCurrentDefaultPromptIndex: Int
        get() = promptClassTemplateFactory.getCurrentDefaultPromptIndex()
        set(value) = promptClassTemplateFactory.setCurrentDefaultPromptIndex(value)

    var methodCurrentDefaultPromptIndex: Int
        get() = promptMethodTemplateFactory.getCurrentDefaultPromptIndex()
        set(value) = promptMethodTemplateFactory.setCurrentDefaultPromptIndex(value)

    var lineCurrentDefaultPromptIndex: Int
        get() = promptLineTemplateFactory.getCurrentDefaultPromptIndex()
        set(value) = promptLineTemplateFactory.setCurrentDefaultPromptIndex(value)

    var junitVersionPriorityCheckBoxSelected: Boolean
        get() = junitVersionPriorityCheckBox.isSelected
        set(newStatus) {
            junitVersionPriorityCheckBox.isSelected = newStatus
        }

    var junitVersion: JUnitVersion
        get() = junitVersionSelector.item
        set(value) {
            junitVersionSelector.item = value
        }

    var defaultLLMRequests: String
        get() = JsonEncoding.encode(
            defaultLLMRequestPanels.filter { (it.getComponent(0) as JTextField).text.isNotBlank() }
                .map { (it.getComponent(0) as JTextField).text } as MutableList<String>,
        )
        set(value) {
            fillDefaultLLMRequestsPanel(JsonEncoding.decode(value))
        }

    var llmSetupCheckBoxSelected: Boolean
        get() = llmSetupCheckBox.isSelected
        set(newStatus) {
            llmSetupCheckBox.isSelected = newStatus
        }

    var provideTestSamplesCheckBoxSelected: Boolean
        get() = provideTestSamplesCheckBox.isSelected
        set(newStatus) {
            provideTestSamplesCheckBox.isSelected = newStatus
        }

    init {
        super.initComponent()
    }

    override fun stylizePanel() {
        LLMHelper.stylizeMainComponents(platformSelector, modelSelector, llmUserTokenField, llmPlatforms, llmSettingsState)

        fillDefaultLLMRequestsPanel(JsonEncoding.decode(llmSettingsState.defaultLLMRequests))
        fillAddDefaultLLMRequestsButtonPanel()
        fillJunitComponents()

        maxLLMRequestsField.toolTipText = LLMSettingsBundle.get("maximumNumberOfRequests")
        maxInputParamsDepthField.toolTipText = LLMSettingsBundle.get("parametersDepth")
        maxPolyDepthField.toolTipText = LLMSettingsBundle.get("maximumPolyDepth")
        promptSeparator.toolTipText = LLMSettingsBundle.get("promptEditor")
        provideTestSamplesCheckBox.toolTipText = LLMSettingsBundle.get("provideTestSamples")
    }

    override fun createSettingsPanel() {
        panel = FormBuilder.createFormBuilder()
            .addComponent(JXTitledSeparator(LLMLabelsBundle.get("LLMSettings")))
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
                JBLabel(LLMLabelsBundle.get("parametersDepth")),
                maxInputParamsDepthField,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("maximumPolyDepth")),
                maxPolyDepthField,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("maximumNumberOfRequests")),
                maxLLMRequestsField,
                10,
                false,
            )
            .addComponent(llmSetupCheckBox, 10)
            .addComponent(provideTestSamplesCheckBox, 10)
            .addComponent(defaultLLMRequestsSeparator, 15)
            .addComponent(commonDefaultLLMRequestsPanel, 15)
            .addComponent(addDefaultLLMRequestsButtonPanel, 15)
            .addComponent(junitVersionSeparator, 15)
            .addComponent(junitVersionPriorityCheckBox, 15)
            .addLabeledComponent(
                JBLabel(LLMLabelsBundle.get("preferredJUnitVersion")),
                junitVersionSelector,
                10,
                false,
            )
            .addComponent(promptSeparator, 15)
            .addComponent(promptEditorTabbedPane, 15)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun addListeners() {
        LLMHelper.addLLMPanelListeners(
            platformSelector,
            modelSelector,
            llmUserTokenField,
            llmPlatforms,
            llmSettingsState,
        )
    }

    fun updateTokenAndModel() {
        for (llmPlatform in llmPlatforms) {
            if (currentLLMPlatformName == llmPlatform.name) {
                llmUserTokenField.text = llmPlatform.token
                if (modelSelector.isEnabled) modelSelector.selectedItem = llmPlatform.model
            }
        }
    }

    private fun createTabbedPane(): JBTabbedPane {
        val tabbedPane = JBTabbedPane()

        // Add tabs for each testing level
        addPromptEditorTab(tabbedPane, PromptEditorType.CLASS)
        addPromptEditorTab(tabbedPane, PromptEditorType.METHOD)
        addPromptEditorTab(tabbedPane, PromptEditorType.LINE)

        return tabbedPane
    }

    private fun addPromptEditorTab(tabbedPane: JBTabbedPane, promptEditorType: PromptEditorType) {
        // initiate the panel
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // Prompt
        val promptTemplateFactory: PromptTemplateFactory = when (promptEditorType) {
            PromptEditorType.CLASS -> promptClassTemplateFactory
            PromptEditorType.METHOD -> promptMethodTemplateFactory
            PromptEditorType.LINE -> promptLineTemplateFactory
        }

        panel.add(promptTemplateFactory.getUpperButtonsPanel())
        panel.add(promptTemplateFactory.getEditorTextField())
        panel.add(promptTemplateFactory.getLowerButtonsPanel())

        // add buttons for inserting keywords to the prompt editor
        addPromptButtons(panel)
        // add the panel as a new tab
        tabbedPane.addTab(promptEditorType.text, panel)
    }

    private fun addPromptButtons(panel: JPanel) {
        val keywords = PromptParserHelper.getKeywords()
        val mandatoryKeywords = keywords.filter { it.mandatory }
        val optionalKeywords = keywords.filter { !it.mandatory }

        val mandatoryPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        mandatoryPanel.add(JLabel("Mandatory:"))
        panel.add(mandatoryPanel)
        mandatoryKeywords.forEach {
            panel.add(createButtonPanel(it, panel))
        }

        val optionalPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        optionalPanel.add(JLabel("Optional:"))
        panel.add(optionalPanel)
        optionalKeywords.forEach {
            panel.add(createButtonPanel(it, panel))
        }
    }

    private fun createButtonPanel(keyword: PromptKeyword, panel: JPanel): JPanel {
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val editorTextField = panel.getComponent(1) as EditorTextField
        val button = JButton(keyword.variable)
        button.foreground = JBColor.ORANGE
        button.font = Font("Monochrome", Font.BOLD, 12)

        // add actionListener for button
        button.addActionListener { _ ->
            val editor = editorTextField.editor

            editor?.let { e ->
                val offset = e.caretModel.offset
                val document = editorTextField.document
                WriteCommandAction.runWriteCommandAction(e.project) {
                    document.insertString(offset, keyword.variable)
                }
            }
        }

        // add button and it's description to buttons panel
        buttonPanel.add(button)
        buttonPanel.add(JBLabel(keyword.description))

        return buttonPanel
    }

    /**
     * Fills the addDefaultLLMRequestsButtonPanel with a button for adding default LLM requests.
     */
    private fun fillAddDefaultLLMRequestsButtonPanel() {
        val addDefaultLLMRequestsButton = JButton(PluginLabelsBundle.get("addRequest"), TestSparkIcons.add)

        addDefaultLLMRequestsButton.isOpaque = false
        addDefaultLLMRequestsButton.isContentAreaFilled = false
        addDefaultLLMRequestsButton.addActionListener {
            addDefaultLLMRequestToPanel("")

            addDefaultLLMRequestsButtonPanel.revalidate()
        }

        addDefaultLLMRequestsButtonPanel.add(addDefaultLLMRequestsButton)
    }

    private fun fillJunitComponents() {
        junitVersionSelector.item = llmSettingsState.junitVersion
        junitVersionPriorityCheckBox.isSelected = llmSettingsState.junitVersionPriorityCheckBoxSelected
    }

    /**
     * Clears the default LLM request panels and fills the commonDefaultLLMRequestsPanel with the given list of default LLM requests.
     *
     * @param defaultLLMRequestsList The list of default LLM requests to fill the panel with.
     */
    private fun fillDefaultLLMRequestsPanel(defaultLLMRequestsList: List<String>) {
        defaultLLMRequestPanels.clear()
        commonDefaultLLMRequestsPanel.removeAll()

        commonDefaultLLMRequestsPanel.layout = BoxLayout(commonDefaultLLMRequestsPanel, BoxLayout.Y_AXIS)

        for (defaultLLMRequest in defaultLLMRequestsList) {
            addDefaultLLMRequestToPanel(defaultLLMRequest)
        }

        commonDefaultLLMRequestsPanel.revalidate()
    }

    /**
     * Adds a default LLM request to the panel.
     *
     * @param defaultLLMRequest the default LLM request to be added
     */
    private fun addDefaultLLMRequestToPanel(defaultLLMRequest: String) {
        val defaultLLMRequestPanel = JPanel(FlowLayout(FlowLayout.LEFT))

        val textField = JTextField(defaultLLMRequest)
        textField.columns = 30
        defaultLLMRequestPanel.add(textField)

        val removeButton = IconButtonCreator.getButton(TestSparkIcons.remove, PluginLabelsBundle.get("removeRequest"))
        defaultLLMRequestPanel.add(removeButton)

        commonDefaultLLMRequestsPanel.add(defaultLLMRequestPanel)
        defaultLLMRequestPanels.add(defaultLLMRequestPanel)

        removeButton.addActionListener {
            textField.text = ""
            commonDefaultLLMRequestsPanel.remove(defaultLLMRequestPanel)
            commonDefaultLLMRequestsPanel.revalidate()
        }
    }
}
