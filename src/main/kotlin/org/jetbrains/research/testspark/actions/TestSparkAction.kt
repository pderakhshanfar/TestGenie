package org.jetbrains.research.testspark.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import org.jetbrains.research.testspark.actions.controllers.TestGenerationController
import org.jetbrains.research.testspark.actions.controllers.VisibilityController
import org.jetbrains.research.testspark.actions.evosuite.EvoSuitePanelBuilder
import org.jetbrains.research.testspark.actions.llm.LLMSampleSelectorBuilder
import org.jetbrains.research.testspark.actions.llm.LLMSetupPanelBuilder
import org.jetbrains.research.testspark.actions.template.PanelBuilder
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.test.data.CodeType
import org.jetbrains.research.testspark.display.TestSparkDisplayManager
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.langwrappers.PsiHelper
import org.jetbrains.research.testspark.langwrappers.PsiHelperProvider
import org.jetbrains.research.testspark.services.EvoSuiteSettingsService
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.settings.evosuite.EvoSuiteSettingsState
import org.jetbrains.research.testspark.settings.llm.LLMSettingsState
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import org.jetbrains.research.testspark.tools.evosuite.EvoSuite
import org.jetbrains.research.testspark.tools.kex.Kex
import org.jetbrains.research.testspark.tools.llm.Llm
import org.jetbrains.research.testspark.tools.template.Tool
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingConstants

/**
 * Represents an action to be performed in the TestSpark plugin.
 *
 * This class extends the AnAction class and is responsible for handling the action performed event.
 * It creates a dialog wrapper and displays it when the associated action is performed.
 */
class TestSparkAction : AnAction() {
    // Controllers
    private val visibilityController = VisibilityController()
    private val testGenerationController = TestGenerationController()

    private val testSparkDisplayManager = TestSparkDisplayManager()
    private val testsExecutionResultManager = TestsExecutionResultManager()

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
        TestSparkActionWindow(e, visibilityController, testGenerationController, testSparkDisplayManager, testsExecutionResultManager)
    }

    /**
     * Updates the state of the action based on the provided event.
     *
     * @param e `AnActionEvent` object representing the event
     */
    override fun update(e: AnActionEvent) {
        val file = e.dataContext.getData(CommonDataKeys.PSI_FILE)

        if (file == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val psiHelper = PsiHelperProvider.getPsiHelper(file)
        e.presentation.isEnabledAndVisible = (psiHelper != null) && psiHelper.availableForGeneration(e)
    }

    /**
     * Class representing the TestSparkActionWindow.
     *
     * @property e The AnActionEvent object.
     */
    class TestSparkActionWindow(
        private val e: AnActionEvent,
        private val visibilityController: VisibilityController,
        private val testGenerationController: TestGenerationController,
        private val testSparkDisplayManager: TestSparkDisplayManager,
        private val testsExecutionResultManager: TestsExecutionResultManager,
    ) :
        JFrame("TestSpark") {
        private val project: Project = e.project!!

        private val llmSettingsState: LLMSettingsState
            get() = project.getService(LLMSettingsService::class.java).state
        private val evoSuiteSettingsState: EvoSuiteSettingsState
            get() = project.getService(EvoSuiteSettingsService::class.java).state

        private val llmButton = JRadioButton("<html><b>${Llm().name}</b></html>")
        private val evoSuiteButton = JRadioButton("<html><b>${EvoSuite().name}</b></html>")
        private val kexButton = JRadioButton("<html><b>${Kex().name}</b></html>")
        private val testGeneratorButtonGroup = ButtonGroup()
        private val kexForLineCodeTypeErrMsg = JLabel() // The error displayed when if kex and line code type are chosen

        private val psiHelper: PsiHelper
            get() {
                val file = e.dataContext.getData(CommonDataKeys.PSI_FILE)!!
                val psiHelper = PsiHelperProvider.getPsiHelper(file)
                if (psiHelper == null) {
                    // TODO exception
                }
                return psiHelper!!
            }

        private val codeTypes = psiHelper.getCurrentListOfCodeTypes(e)
        private val caretOffset: Int = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret!!.offset
        private val fileUrl = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)!!.presentableUrl

        private val codeTypeButtons: MutableList<Pair<CodeType, JRadioButton>> = mutableListOf()
        private val codeTypeButtonGroup = ButtonGroup()

        private val nextButton = JButton(PluginLabelsBundle.get("next"))

        private val cardLayout = CardLayout()
        private val llmSetupPanelFactory = LLMSetupPanelBuilder(e, project)
        private val llmSampleSelectorFactory = LLMSampleSelectorBuilder(project, psiHelper.language)
        private val evoSuitePanelFactory = EvoSuitePanelBuilder(project)

        init {
            if (!visibilityController.isVisible) {
                visibilityController.isVisible = true
                isVisible = true

                val panel = JPanel(cardLayout)

                panel.add(getMainPanel(), "1")
                panel.add(createCardPanel(evoSuitePanelFactory), "2")
                panel.add(createCardPanel(llmSetupPanelFactory), "3")

                panel.add(
                    JBScrollPane(
                        createCardPanel(llmSampleSelectorFactory),
                        JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                    ),
                    "4",
                )

                addListeners(panel)

                add(panel)

                pack()

                val dimension: Dimension = Toolkit.getDefaultToolkit().screenSize
                val x = (dimension.width - size.width) / 2
                val y = (dimension.height - size.height) / 2
                setLocation(x, y)
            } else {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Generation Error")
                    .createNotification(
                        PluginMessagesBundle.get("generationWindowWarningTitle"),
                        PluginMessagesBundle.get("generationWindowWarningMessage"),
                        NotificationType.WARNING,
                    )
                    .notify(e.project)
            }
        }

        private fun createCardPanel(toolPanelBuilder: PanelBuilder): JPanel {
            val cardPanel = JPanel(BorderLayout())
            cardPanel.add(toolPanelBuilder.getTitlePanel(), BorderLayout.NORTH)
            cardPanel.add(toolPanelBuilder.getMiddlePanel(), BorderLayout.CENTER)
            cardPanel.add(toolPanelBuilder.getBottomPanel(), BorderLayout.SOUTH)

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
            val panelTitle = JPanel()
            val textTitle = JLabel("Welcome to TestSpark!")
            textTitle.font = Font("Monochrome", Font.BOLD, 20)
            panelTitle.add(JLabel(TestSparkIcons.pluginIcon))
            panelTitle.add(textTitle)

            testGeneratorButtonGroup.add(llmButton)
            testGeneratorButtonGroup.add(evoSuiteButton)
            testGeneratorButtonGroup.add(kexButton)

            val testGeneratorPanel = JPanel()
            testGeneratorPanel.add(JLabel("Select the test generator:"))
            testGeneratorPanel.add(llmButton)
            testGeneratorPanel.add(evoSuiteButton)
            testGeneratorPanel.add(kexButton)

            for ((codeType, codeTypeName) in codeTypes) {
                val button = JRadioButton(codeTypeName)
                codeTypeButtons.add(codeType to button)
                codeTypeButtonGroup.add(button)
            }

            val codesToTestPanel = JPanel()
            codesToTestPanel.add(JLabel("Select the code type:"))
            if (codeTypeButtons.size == 1) {
                // A single button is selected by default
                codeTypeButtons[0].second.isSelected = true
            }
            for ((_, button) in codeTypeButtons) codesToTestPanel.add(button)

            val middlePanel = FormBuilder.createFormBuilder()
                .setFormLeftIndent(10)
                .addComponent(
                    testGeneratorPanel,
                    10,
                )
                .addComponent(
                    codesToTestPanel,
                    10,
                )
                .panel

            val nextButtonPanel = JPanel()
            nextButtonPanel.layout = BoxLayout(nextButtonPanel, BoxLayout.Y_AXIS)
            nextButton.isEnabled = false
            nextButton.alignmentX = Component.CENTER_ALIGNMENT
            kexForLineCodeTypeErrMsg.alignmentX = Component.CENTER_ALIGNMENT
            kexForLineCodeTypeErrMsg.horizontalAlignment = SwingConstants.CENTER
            nextButtonPanel.add(kexForLineCodeTypeErrMsg)
            nextButtonPanel.add(Box.createVerticalStrut(10)) // Add some space between label and button
            nextButtonPanel.add(nextButton)

            val cardPanel = JPanel(BorderLayout())
            cardPanel.add(panelTitle, BorderLayout.NORTH)
            cardPanel.add(middlePanel, BorderLayout.CENTER)
            cardPanel.add(nextButtonPanel, BorderLayout.SOUTH)

            return cardPanel
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

            kexButton.addActionListener {
                updateNextButton()
            }

            for ((_, button) in codeTypeButtons) {
                button.addActionListener {
                    llmSetupPanelFactory.setPromptEditorType(button.text)
                    updateNextButton()
                }
            }

            nextButton.addActionListener {
                if (llmButton.isSelected && !llmSettingsState.llmSetupCheckBoxSelected && !llmSettingsState.provideTestSamplesCheckBoxSelected) {
                    startLLMGeneration()
                } else if (llmButton.isSelected && !llmSettingsState.llmSetupCheckBoxSelected) {
                    cardLayout.next(panel)
                    cardLayout.next(panel)
                    cardLayout.next(panel)
                    pack()
                } else if (llmButton.isSelected) {
                    cardLayout.next(panel)
                    cardLayout.next(panel)
                    pack()
                } else if (kexButton.isSelected) {
                    startKexGeneration()
                } else if (evoSuiteButton.isSelected && !evoSuiteSettingsState.evosuiteSetupCheckBoxSelected) {
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
                if (llmSettingsState.provideTestSamplesCheckBoxSelected) {
                    cardLayout.next(panel)
                } else {
                    startLLMGeneration()
                }
            }

            llmSampleSelectorFactory.getAddButton().addActionListener {
                size = Dimension(width, 500)
            }

            llmSampleSelectorFactory.getBackButton().addActionListener {
                if (llmSettingsState.llmSetupCheckBoxSelected) {
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

        private fun startUnitTestGenerationTool(tool: Tool) {
            if (!testGenerationController.isGeneratorRunning(project)) {
                val testSamplesCode = llmSampleSelectorFactory.getTestSamplesCode()

                for ((codeType, button) in codeTypeButtons) {
                    if (button.isSelected) {
                        when (codeType) {
                            CodeType.CLASS -> tool.generateTestsForClass(
                                project,
                                psiHelper,
                                caretOffset,
                                fileUrl,
                                testSamplesCode,
                                testGenerationController,
                                testSparkDisplayManager,
                                testsExecutionResultManager,
                            )
                            CodeType.METHOD -> tool.generateTestsForMethod(
                                project,
                                psiHelper,
                                caretOffset,
                                fileUrl,
                                testSamplesCode,
                                testGenerationController,
                                testSparkDisplayManager,
                                testsExecutionResultManager,
                            )
                            CodeType.LINE -> tool.generateTestsForLine(
                                project,
                                psiHelper,
                                caretOffset,
                                fileUrl,
                                testSamplesCode,
                                testGenerationController,
                                testSparkDisplayManager,
                                testsExecutionResultManager,
                            )
                        }
                        break
                    }
                }
            }

            visibilityController.isVisible = false
            dispose()
        }

        private fun startKexGeneration() = startUnitTestGenerationTool(tool = Kex())
        private fun startEvoSuiteGeneration() = startUnitTestGenerationTool(tool = EvoSuite())
        private fun startLLMGeneration() = startUnitTestGenerationTool(tool = Llm())

        /**
         * Updates the state of the "Next" button based on the selected options.
         * The "Next" button is enabled only if a test generator button (llmButton or evoSuiteButton) and at least one
         * code type button (from codeTypeButtons) are selected.
         *
         * This method should be called whenever the mentioned above buttons are clicked.
         */
        private fun updateNextButton() {
            val isTestGeneratorButtonGroupSelected = llmButton.isSelected || evoSuiteButton.isSelected || kexButton.isSelected
            val isCodeTypeButtonGroupSelected = codeTypeButtons.any { it.second.isSelected }
            val kexForCodeLineType =
                kexButton.isSelected && codeTypeButtons.any { (codeType, button) -> codeType == CodeType.LINE && button.isSelected }
            if (kexForCodeLineType) {
                kexForLineCodeTypeErrMsg.text =
                    "<html><b><font color='orange'>* Kex cannot generate tests for a single line. Please change your selection</font></b></html>"
            } else {
                kexForLineCodeTypeErrMsg.text = ""
            }

            nextButton.isEnabled =
                isTestGeneratorButtonGroupSelected && isCodeTypeButtonGroupSelected && !kexForCodeLineType

            if ((llmButton.isSelected && !llmSettingsState.llmSetupCheckBoxSelected && !llmSettingsState.provideTestSamplesCheckBoxSelected) ||
                (evoSuiteButton.isSelected && !evoSuiteSettingsState.evosuiteSetupCheckBoxSelected) ||
                kexButton.isSelected
            ) {
                nextButton.text = PluginLabelsBundle.get("ok")
            } else {
                nextButton.text = PluginLabelsBundle.get("next")
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
