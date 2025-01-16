package org.jetbrains.research.testspark.actions.llm

import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder
import org.jetbrains.research.testspark.actions.template.PanelBuilder
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import java.awt.Font
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton

class LLMSampleSelectorBuilder(private val project: Project, private val language: SupportedLanguage) : PanelBuilder {
    // init components
    private val selectionTypeButtons: MutableList<JRadioButton> = mutableListOf(
        JRadioButton(PluginLabelsBundle.get("provideTestSample")),
        JRadioButton(PluginLabelsBundle.get("noTestSample")),
    )
    private val selectionTypeButtonGroup = ButtonGroup()
    private val radioButtonsPanel = JPanel()

    private val testSamplePanelFactories: MutableList<TestSamplePanelBuilder> = mutableListOf()
    private val sampleSelector = LLMSampleSelector()

    private val addButtonPanel = JPanel()
    private val addButton = JButton(PluginLabelsBundle.get("addTestSample"))

    private val nextButton = JButton(PluginLabelsBundle.get("ok"))
    private val backLlmButton = JButton(PluginLabelsBundle.get("back"))

    private var formBuilder = FormBuilder.createFormBuilder()
        .setFormLeftIndent(10)
        .addComponent(JPanel(), 0)
        .addComponent(radioButtonsPanel, 10)
        .addComponent(addButtonPanel, 10)

    private var middlePanel = formBuilder.panel

    init {
        addListeners()
    }

    override fun getTitlePanel(): JPanel {
        val textTitle = JLabel(PluginLabelsBundle.get("llmSampleSelectorFactory"))
        textTitle.font = Font("Monochrome", Font.BOLD, 20)

        val titlePanel = JPanel()
        titlePanel.add(textTitle)

        return titlePanel
    }

    override fun getMiddlePanel(): JPanel {
        for (button in selectionTypeButtons) {
            selectionTypeButtonGroup.add(button)
            radioButtonsPanel.add(button)
        }

        selectionTypeButtons[1].isSelected = true

        addButtonPanel.add(addButton)

        enabledComponents(false)

        middlePanel.revalidate()

        return middlePanel
    }

    override fun getBottomPanel(): JPanel {
        val bottomPanel = JPanel()
        backLlmButton.isOpaque = false
        backLlmButton.isContentAreaFilled = false
        bottomPanel.add(backLlmButton)
        nextButton.isOpaque = false
        nextButton.isContentAreaFilled = false
        bottomPanel.add(nextButton)

        return bottomPanel
    }

    override fun getBackButton() = backLlmButton

    override fun getFinishedButton() = nextButton

    override fun applyUpdates() {
        if (selectionTypeButtons[0].isSelected) {
            for (index in testSamplePanelFactories.indices) {
                sampleSelector.appendTestSampleCode(index, testSamplePanelFactories[index].getCode())
            }
        }
    }

    /**
     * Retrieves the add button.
     *
     * @return The add button.
     */
    fun getAddButton(): JButton = addButton

    /**
     * Retrieves the test samples code.
     *
     * @return The test samples code.
     */
    fun getTestSamplesCode(): String = sampleSelector.getTestSamplesCode()

    /**
     * Adds action listeners to the selectionTypeButtons array to enable the nextButton if any button is selected.
     */
    private fun addListeners() {
        selectionTypeButtons[0].addActionListener {
            updateNextButton()
            enabledComponents(true)
        }

        selectionTypeButtons[1].addActionListener {
            updateNextButton()
            enabledComponents(false)
        }

        addButton.addActionListener {
            sampleSelector.collectTestSamples(project)
            val testSamplePanelBuilder = TestSamplePanelBuilder(
                project,
                middlePanel,
                sampleSelector.getTestNames(),
                sampleSelector.getInitialTestCodes(),
                language,
            )
            testSamplePanelFactories.add(testSamplePanelBuilder)
            val testSamplePanel = testSamplePanelBuilder.getTestSamplePanel()
            val codeScrollPanel = testSamplePanelBuilder.getCodeScrollPanel()
            formBuilder = formBuilder
                .addComponent(testSamplePanel, 10)
                .addComponent(codeScrollPanel, 10)
            middlePanel = formBuilder.panel
            middlePanel.revalidate()

            testSamplePanelBuilder.getRemoveButton().addActionListener {
                testSamplePanelFactories.remove(testSamplePanelBuilder)
                middlePanel.remove(testSamplePanel)
                middlePanel.remove(codeScrollPanel)
                middlePanel.revalidate()

                updateNextButton()
            }

            updateNextButton()
        }
    }

    /**
     * Updates next button.
     */
    private fun updateNextButton() {
        if (selectionTypeButtons[0].isSelected) {
            nextButton.isEnabled = testSamplePanelFactories.isNotEmpty()
        } else {
            nextButton.isEnabled = true
        }
    }

    /**
     * Enables and disables the components in the panel in case of type button selection.
     */
    private fun enabledComponents(isEnabled: Boolean) {
        addButton.isEnabled = isEnabled

        for (testSamplePanelFactory in testSamplePanelFactories) {
            testSamplePanelFactory.enabledComponents(isEnabled)
        }
    }
}
