package org.jetbrains.research.testspark.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.research.testspark.actions.evosuite.EvoSuitePanelFactory
import org.jetbrains.research.testspark.actions.llm.LLMSampleSelectorFactory
import org.jetbrains.research.testspark.actions.llm.LLMSetupPanelFactory
import org.jetbrains.research.testspark.actions.template.ToolPanelFactory
import org.jetbrains.research.testspark.bundles.TestSparkLabelsBundle
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.helpers.getCurrentListOfCodeTypes
import org.jetbrains.research.testspark.services.SettingsApplicationService
import org.jetbrains.research.testspark.tools.Manager
import org.jetbrains.research.testspark.tools.evosuite.EvoSuite
import org.jetbrains.research.testspark.tools.llm.Llm
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Represents an action to be performed in the TestSpark plugin.
 *
 * This class extends the AnAction class and is responsible for handling the action performed event.
 * It creates a dialog wrapper and displays it when the associated action is performed.
 */
class TestSparkAction : AnAction() {
    class VisibilityController {
        var isVisible = false
    }

    private val visibilityController = VisibilityController()

    /**
     * Handles the action performed event.
     *
     * This method is called when the associated action is performed.
     *
     * @param e The AnActionEvent object representing the action event.
     *           It provides information about the event, such as the source of the event and the project context.
     *           This parameter is required.
     */
    override fun actionPerformed(e: AnActionEvent) {
        TestSparkActionWindow(e, visibilityController)
    }

    /**
     * Updates the state of the action based on the provided event.
     *
     * @param e the AnActionEvent object representing the event
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = getCurrentListOfCodeTypes(e) != null
    }

    /**
     * Class representing the TestSparkActionWindow.
     *
     * @property e The AnActionEvent object.
     */
    class TestSparkActionWindow(val e: AnActionEvent, private val visibilityController: VisibilityController) : JFrame("TestSpark") {
        private val llmButton = JRadioButton("<html><b>${Llm().name}</b></html>")
        private val evoSuiteButton = JRadioButton("<html><b>${EvoSuite().name}</b></html>")
        private val testGeneratorButtonGroup = ButtonGroup()
        private val codeTypes = getCurrentListOfCodeTypes(e)!!
        private val codeTypeButtons: MutableList<JRadioButton> = mutableListOf()
        private val codeTypeButtonGroup = ButtonGroup()

        private val nextButton = JButton(TestSparkLabelsBundle.defaultValue("next"))

        private val cardLayout = CardLayout()

        private val llmSetupPanelFactory = LLMSetupPanelFactory()
        private val llmSampleSelectorFactory = LLMSampleSelectorFactory(e.project!!)
        private val evoSuitePanelFactory = EvoSuitePanelFactory()

        init {
            if (!visibilityController.isVisible) {
                visibilityController.isVisible = true
                isVisible = true

                val panel = JPanel(cardLayout)

                panel.add(getMainPanel(), "1")
                panel.add(createCardPanel(evoSuitePanelFactory), "2")
                panel.add(createCardPanel(llmSetupPanelFactory), "3")
                panel.add(createCardPanel(llmSampleSelectorFactory), "4")

                addListeners(panel)

                add(panel)

                pack()

                val dimension: Dimension = Toolkit.getDefaultToolkit().screenSize
                val x = (dimension.width - size.width) / 2
                val y = (dimension.height - size.height) / 2
                setLocation(x, y)
            }
        }

        private fun createCardPanel(toolPanelFactory: ToolPanelFactory): JPanel {
            val cardPanel = JPanel(BorderLayout())
            cardPanel.add(toolPanelFactory.getTitlePanel(), BorderLayout.NORTH)
            cardPanel.add(toolPanelFactory.getMiddlePanel(), BorderLayout.CENTER)
            cardPanel.add(toolPanelFactory.getBottomPanel(), BorderLayout.SOUTH)

            return cardPanel
        }

        /**
         * Returns the main panel for the test generator UI.
         * This panel contains options for selecting the test generator and the code type.
         * It also includes a button for proceeding to the next step.
         *
         * @return the main panel for the test generator UI
         */
        private fun getMainPanel(): JPanel {
            val mainPanel = JPanel()
            mainPanel.setLayout(BoxLayout(mainPanel, BoxLayout.Y_AXIS))

            val panelTitle = JPanel()
            val iconTitle = JLabel(TestSparkIcons.pluginIcon)
            val textTitle = JLabel("Welcome to TestSpark!")
            textTitle.font = Font("Monochrome", Font.BOLD, 20)
            panelTitle.add(iconTitle)
            panelTitle.add(textTitle)
            mainPanel.add(panelTitle)

            testGeneratorButtonGroup.add(llmButton)
            testGeneratorButtonGroup.add(evoSuiteButton)

            val testGeneratorPanel = JPanel()
            testGeneratorPanel.add(JLabel("Select the test generator:"))
            testGeneratorPanel.add(llmButton)
            testGeneratorPanel.add(evoSuiteButton)
            mainPanel.add(testGeneratorPanel)

            for (codeType in codeTypes) {
                val button = JRadioButton(codeType as String)
                codeTypeButtons.add(button)
                codeTypeButtonGroup.add(button)
            }

            val codesToTestPanel = JPanel()
            codesToTestPanel.add(JLabel("Select the code type:"))
            if (codeTypeButtons.size == 1) codeTypeButtons[0].isSelected = true
            for (button in codeTypeButtons) codesToTestPanel.add(button)
            mainPanel.add(codesToTestPanel)

            val nextButtonPanel = JPanel()
            nextButton.isEnabled = false
            nextButtonPanel.add(nextButton)
            mainPanel.add(nextButtonPanel)

            return mainPanel
        }

        /**
         * Adds listeners to various components in the given panel.
         *
         * @param panel the JPanel to add listeners to
         */
        private fun addListeners(panel: JPanel) {
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    visibilityController.isVisible = false
                }
            })

            llmButton.addActionListener {
                updateNextButton()
            }

            evoSuiteButton.addActionListener {
                updateNextButton()
            }

            for (button in codeTypeButtons) {
                button.addActionListener { updateNextButton() }
            }

            nextButton.addActionListener {
                if (llmButton.isSelected && !SettingsApplicationService.getInstance().state!!.llmSetupCheckBoxSelected && !SettingsApplicationService.getInstance().state!!.provideTestSamplesCheckBoxSelected) {
                    startLLMGeneration()
                } else if (llmButton.isSelected && !SettingsApplicationService.getInstance().state!!.llmSetupCheckBoxSelected) {
                    cardLayout.next(panel)
                    cardLayout.next(panel)
                    cardLayout.next(panel)
                    pack()
                } else if (llmButton.isSelected) {
                    cardLayout.next(panel)
                    cardLayout.next(panel)
                    pack()
                } else if (evoSuiteButton.isSelected && !SettingsApplicationService.getInstance().state!!.evosuiteSetupCheckBoxSelected) {
                    startEvoSuiteGeneration()
                } else {
                    cardLayout.next(panel)
                    pack()
                }
            }

            evoSuitePanelFactory.getBackButton().addActionListener {
                cardLayout.previous(panel)
                pack()
            }

            llmSetupPanelFactory.getBackButton().addActionListener {
                cardLayout.previous(panel)
                cardLayout.previous(panel)
                pack()
            }

            llmSetupPanelFactory.getFinishedButton().addActionListener {
                llmSetupPanelFactory.applyUpdates()
                if (SettingsApplicationService.getInstance().state!!.provideTestSamplesCheckBoxSelected) {
                    cardLayout.next(panel)
                    pack()
                } else {
                    startLLMGeneration()
                }
            }

            llmSampleSelectorFactory.getBackButton().addActionListener {
                if (SettingsApplicationService.getInstance().state!!.llmSetupCheckBoxSelected) {
                    cardLayout.previous(panel)
                } else {
                    cardLayout.previous(panel)
                    cardLayout.previous(panel)
                    cardLayout.previous(panel)
                }
                pack()
            }

            llmSampleSelectorFactory.getFinishedButton().addActionListener {
                llmSampleSelectorFactory.applyUpdates()
                startLLMGeneration()
            }

            evoSuitePanelFactory.getFinishedButton().addActionListener {
                evoSuitePanelFactory.applyUpdates()
                startEvoSuiteGeneration()
            }
        }

        private fun startEvoSuiteGeneration() {
            if (codeTypeButtons[0].isSelected) {
                Manager.generateTestsForClassByEvoSuite(e)
            } else if (codeTypeButtons[1].isSelected) {
                Manager.generateTestsForMethodByEvoSuite(e)
            } else if (codeTypeButtons[2].isSelected) {
                Manager.generateTestsForLineByEvoSuite(e)
            }
            visibilityController.isVisible = false
            dispose()
        }

        private fun startLLMGeneration() {
            if (codeTypeButtons[0].isSelected) {
                Manager.generateTestsForClassByLlm(e)
            } else if (codeTypeButtons[1].isSelected) {
                Manager.generateTestsForMethodByLlm(e)
            } else if (codeTypeButtons[2].isSelected) {
                Manager.generateTestsForLineByLlm(e)
            }
            visibilityController.isVisible = false
            dispose()
        }

        /**
         * Updates the state of the "Next" button based on the selected options.
         * The "Next" button is enabled only if a test generator button (llmButton or evoSuiteButton) and at least one
         * code type button (from codeTypeButtons) are selected.
         *
         * This method should be called whenever the mentioned above buttons are clicked.
         */
        private fun updateNextButton() {
            val isTestGeneratorButtonGroupSelected = llmButton.isSelected || evoSuiteButton.isSelected
            var isCodeTypeButtonGroupSelected = false
            for (button in codeTypeButtons) {
                isCodeTypeButtonGroupSelected = isCodeTypeButtonGroupSelected || button.isSelected
            }
            nextButton.isEnabled = isTestGeneratorButtonGroupSelected && isCodeTypeButtonGroupSelected

            if ((llmButton.isSelected && !SettingsApplicationService.getInstance().state!!.llmSetupCheckBoxSelected && !SettingsApplicationService.getInstance().state!!.provideTestSamplesCheckBoxSelected) ||
                (evoSuiteButton.isSelected && !SettingsApplicationService.getInstance().state!!.evosuiteSetupCheckBoxSelected)
            ) {
                nextButton.text = TestSparkLabelsBundle.defaultValue("ok")
            } else {
                nextButton.text = TestSparkLabelsBundle.defaultValue("next")
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
