package org.jetbrains.research.testspark.services

import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.containers.stream
import com.intellij.util.ui.JBUI
import org.jetbrains.research.testspark.TestSparkLabelsBundle
import org.jetbrains.research.testspark.TestSparkToolTipsBundle
import org.jetbrains.research.testspark.data.Report
import org.jetbrains.research.testspark.data.TestCase
import org.jetbrains.research.testspark.display.TestCaseDocumentCreator
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.display.TopButtonsPanel
import org.jetbrains.research.testspark.display.createButton
import org.jetbrains.research.testspark.editor.Workspace
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.border.MatteBorder

class TestCaseDisplayService(private val project: Project) {

    private var mainPanel: JPanel = JPanel()

    private val topButtonsPanel = TopButtonsPanel(project)

    private var applyButton: JButton = JButton(TestSparkLabelsBundle.defaultValue("applyButton"))

    private var allTestCasePanel: JPanel = JPanel()

    private var scrollPane: JBScrollPane = JBScrollPane(
        allTestCasePanel,
        JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    )

    private var testCasePanels: HashMap<String, JPanel> = HashMap()
    private var originalTestCases: HashMap<String, String> = HashMap()

    private var testsSelected: Int = 0

    // Default color for the editors in the tool window
    private var defaultEditorColor: Color? = null
    private var defaultBorder: Border? = null

    // Content Manager to be able to add / remove tabs from tool window
    private var contentManager: ContentManager? = null

    // Variable to keep reference to the coverage visualisation content
    private var content: Content? = null

    private var currentJacocoCoverageBundle: CoverageSuitesBundle? = null

    init {
        allTestCasePanel.layout = BoxLayout(allTestCasePanel, BoxLayout.Y_AXIS)
        mainPanel.layout = BorderLayout()

        mainPanel.add(topButtonsPanel.getPanel(), BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(applyButton, BorderLayout.SOUTH)

        applyButton.addActionListener { applyTests() }
    }

    /**
     * Sets the JaCoCo report for the coverage suites bundle.
     *
     * @param coverageSuitesBundle The coverage suites bundle to set the JaCoCo report for.
     */
    fun setJacocoReport(coverageSuitesBundle: CoverageSuitesBundle) {
        currentJacocoCoverageBundle = coverageSuitesBundle
    }

    /**
     * Creates the complete panel in the "Generated Tests" tab,
     * and adds the "Generated Tests" tab to the sidebar tool window.
     *
     * @param editor editor instance where coverage should be
     *               visualized
     * @param cacheLazyPipeline the runner that was instantiated but not used to create the test suite
     *                        due to a cache hit, or null if there was a cache miss
     */
    fun showGeneratedTests(editor: Editor) {
        displayTestCases(project.service<Workspace>().testJob!!.report, editor)
        createToolWindowTab()
    }

    /**
     * Fill the panel with the generated test cases. Remove all previously shown test cases.
     * Add Tests and their names to a List of pairs (used for highlighting)
     *
     * @param testReport The report from which each testcase should be displayed
     * @param editor editor instance where coverage should be
     *               visualized
     */
    private fun displayTestCases(testReport: Report, editor: Editor) {
        allTestCasePanel.removeAll()
        testCasePanels.clear()
        originalTestCases.clear()

        testReport.testCaseList.values.forEach {
            val testCase = it
            val testCasePanel = JPanel()
            testCasePanel.layout = BorderLayout()

            // Fix Windows line separators
            val testCodeFormatted = testCase.testCode.replace("\r\n", "\n")
            originalTestCases[testCase.testName] = testCodeFormatted

            // Add a checkbox to select the test
            val checkbox = JCheckBox()
            checkbox.isSelected = true
            testCasePanel.add(checkbox, BorderLayout.WEST)

            // Toggle coverage when checkbox is clicked
            checkbox.addItemListener {
                project.messageBus.syncPublisher(COVERAGE_SELECTION_TOGGLE_TOPIC)
                    .testGenerationResult(testCase.testName, checkbox.isSelected, editor)

                // Update the number of selected tests
                testsSelected -= (1 - 2 * checkbox.isSelected.compareTo(false))

                topButtonsPanel.updateTopLabels()
            }

            val upperPanel = JPanel()
            val errorLabel = JLabel(TestSparkIcons.showError)
            updateErrorLabel(errorLabel, testCase.testName)
            val copyButton = createButton(TestSparkIcons.copy, TestSparkLabelsBundle.defaultValue("copyTip"))
            val likeButton = createButton(TestSparkIcons.like, TestSparkLabelsBundle.defaultValue("likeTip"))
            val dislikeButton = createButton(TestSparkIcons.dislike, TestSparkLabelsBundle.defaultValue("dislikeTip"))
            upperPanel.layout = BoxLayout(upperPanel, BoxLayout.X_AXIS)
            upperPanel.add(Box.createRigidArea(Dimension(checkbox.preferredSize.width, checkbox.preferredSize.height)))
            upperPanel.add(errorLabel)
            upperPanel.add(Box.createHorizontalGlue())
            upperPanel.add(copyButton)
            upperPanel.add(Box.createRigidArea(Dimension(5, 0)))
            upperPanel.add(likeButton)
            upperPanel.add(Box.createRigidArea(Dimension(5, 0)))
            upperPanel.add(dislikeButton)
            upperPanel.add(Box.createRigidArea(Dimension(10, 0)))
            testCasePanel.add(upperPanel, BorderLayout.NORTH)

            // Add an editor to modify the test source code
            val languageTextField = LanguageTextField(
                Language.findLanguageByID("JAVA"),
                editor.project,
                testCodeFormatted,
                TestCaseDocumentCreator(
                    project.service<JavaClassBuilderService>().getClassWithTestCaseName(testCase.testName),
                ),
                false,
            )

            // Add test case title
            val middlePanel = JPanel()
            middlePanel.layout = BoxLayout(middlePanel, BoxLayout.Y_AXIS)
            middlePanel.add(Box.createRigidArea(Dimension(0, 5)))
            middlePanel.add(languageTextField)
            middlePanel.add(Box.createRigidArea(Dimension(0, 5)))

            testCasePanel.add(middlePanel, BorderLayout.CENTER)

            // Create "Remove" button to remove the test from cache
            val removeButton = createRemoveButton(testCase, editor, testCasePanel)

            // Create "Reset" button to reset the changes in the source code of the test
            val resetButton = createResetButton()

            // Create "Reset" button to reset the changes to last run in the source code of the test
            val resetToLastRunButton = createResetToLastRunButton()

            // Create "Run tests" button to remove the test from cache
            val runTestButton = createRunTestButton()

            // Set border
            languageTextField.border = getBorder(testCase.testName)

            addListeners(
                resetButton,
                resetToLastRunButton,
                runTestButton,
                likeButton,
                dislikeButton,
                copyButton,
                errorLabel,
                languageTextField,
                checkbox,
                testCase,
                languageTextField.border,
            )

            val bottomPanel = JPanel()
            bottomPanel.layout = BoxLayout(bottomPanel, BoxLayout.X_AXIS)
            bottomPanel.add(Box.createRigidArea(Dimension(checkbox.preferredSize.width, checkbox.preferredSize.height)))
            bottomPanel.add(runTestButton)
            bottomPanel.add(Box.createHorizontalGlue())
            bottomPanel.add(resetButton)
            bottomPanel.add(Box.createRigidArea(Dimension(5, 0)))
            bottomPanel.add(resetToLastRunButton)
            bottomPanel.add(Box.createRigidArea(Dimension(5, 0)))
            bottomPanel.add(removeButton)
            bottomPanel.add(Box.createRigidArea(Dimension(10, 0)))

            testCasePanel.add(bottomPanel, BorderLayout.SOUTH)

            // Add panel to parent panel
            testCasePanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
            allTestCasePanel.add(testCasePanel)
            allTestCasePanel.add(Box.createRigidArea(Dimension(0, 10)))
            allTestCasePanel.add(JSeparator(SwingConstants.HORIZONTAL))
            allTestCasePanel.add(Box.createRigidArea(Dimension(0, 10)))
            testCasePanels[testCase.testName] = testCasePanel

            // Update the number of selected tests (all tests are selected by default)
            testsSelected = testCasePanels.size
            topButtonsPanel.updateTopLabels()
        }
    }

    /**
     * Highlight the mini-editor in the tool window whose name corresponds with the name of the test provided
     *
     * @param name name of the test whose editor should be highlighted
     */
    fun highlightTestCase(name: String) {
        val myPanel = testCasePanels[name] ?: return
        openToolWindowTab()
        scrollToPanel(myPanel)

        val editor = getEditor(name) ?: return
        val settingsProjectState = project.service<SettingsProjectService>().state
        val highlightColor =
            JBColor(
                TestSparkToolTipsBundle.defaultValue("colorName"),
                Color(
                    settingsProjectState.colorRed,
                    settingsProjectState.colorGreen,
                    settingsProjectState.colorBlue,
                    30,
                ),
            )
        if (editor.background.equals(highlightColor)) return
        defaultEditorColor = editor.background
        editor.background = highlightColor
        returnOriginalEditorBackground(editor)
    }

    /**
     * Method to open the toolwindow tab with generated tests if not already open.
     */
    private fun openToolWindowTab() {
        val toolWindowManager = ToolWindowManager.getInstance(project).getToolWindow("TestSpark")
        contentManager = toolWindowManager!!.contentManager
        if (content != null) {
            toolWindowManager.show()
            toolWindowManager.contentManager.setSelectedContent(content!!)
        }
    }

    /**
     * Scrolls to the highlighted panel.
     *
     * @param myPanel the panel to scroll to
     */
    private fun scrollToPanel(myPanel: JPanel) {
        var sum = 0
        for (panel in testCasePanels.values) {
            if (panel == myPanel) {
                break
            } else {
                sum += panel.height
            }
        }
        val scroll = scrollPane.verticalScrollBar
        scroll.value = (scroll.minimum + scroll.maximum) * sum / allTestCasePanel.height
    }

    /**
     * Removes all coverage highlighting from the editor.
     */
    private fun removeAllHighlights() {
        project.service<Workspace>().editor?.markupModel?.removeAllHighlighters()
    }

    /**
     * Reset the provided editors color to the default (initial) one after 10 seconds
     * @param editor the editor whose color to change
     */
    private fun returnOriginalEditorBackground(editor: EditorTextField) {
        Thread {
            Thread.sleep(10000)
            editor.background = defaultEditorColor
        }.start()
    }

    /**
     * Highlight tests failing dynamic validation
     *
     * @param names set of test names that fail
     */
    fun markFailingTestCases(names: Set<String>) {
        for (testCase in testCasePanels) {
            if (names.contains(testCase.key)) {
                val editor = getEditor(testCase.key) ?: return
                val highlightColor = JBColor(TestSparkToolTipsBundle.defaultValue("colorName"), Color(255, 0, 0, 90))
                defaultBorder = editor.border
                editor.border = BorderFactory.createLineBorder(highlightColor, 3)
            } else {
                val editor = getEditor(testCase.key) ?: return
                editor.border = JBUI.Borders.empty()
            }
        }
    }

    /**
     * Highlight a range of editors
     * @param names list of test names to pass to highlight function
     */
    fun highlightCoveredMutants(names: List<String>) {
        names.forEach {
            highlightTestCase(it)
        }
    }

    /**
     * Show a dialog where the user can select what test class the tests should be applied to,
     * and apply the selected tests to the test class.
     */
    private fun applyTests() {
        // Filter the selected test cases
        val selectedTestCasePanels = testCasePanels.filter { (it.value.getComponent(0) as JCheckBox).isSelected }
        val selectedTestCases = selectedTestCasePanels.map { it.key }

        // Get the test case components (source code of the tests)
        val testCaseComponents = selectedTestCases
            .map { getEditor(it)!! }
            .map { it.document.text }

        // Descriptor for choosing folders and java files
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false)

        // Apply filter with folders and java files with main class
        descriptor.withFileFilter { file ->
            file.isDirectory || (
                file.extension?.lowercase(Locale.getDefault()) == "java" && (
                    PsiManager.getInstance(project).findFile(file!!) as PsiJavaFile
                    ).classes.stream().map { it.name }
                    .toArray()
                    .contains(
                        (PsiManager.getInstance(project).findFile(file) as PsiJavaFile).name.removeSuffix(".java"),
                    )
                )
        }

        val fileChooser = FileChooser.chooseFiles(
            descriptor,
            project,
            LocalFileSystem.getInstance().findFileByPath(project.basePath!!),
        )

        // Cancel button pressed
        if (fileChooser.isEmpty()) return

        // Chosen files by user
        val chosenFile = fileChooser[0]

        // Virtual file of a final java file
        var virtualFile: VirtualFile? = null
        // PsiClass of a final java file
        var psiClass: PsiClass? = null
        // PsiJavaFile of a final java file
        var psiJavaFile: PsiJavaFile? = null
        if (chosenFile.isDirectory) {
            // Input new file data
            var className: String
            var fileName: String
            var filePath: String
            // Waiting for correct file name input
            while (true) {
                val jOptionPane =
                    JOptionPane.showInputDialog(
                        null,
                        TestSparkLabelsBundle.defaultValue("optionPaneMessage"),
                        TestSparkLabelsBundle.defaultValue("optionPaneTitle"),
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        null,
                    )

                // Cancel button pressed
                jOptionPane ?: return

                // Get class name from user
                className = jOptionPane as String

                // Set file name and file path
                fileName = "${className.split('.')[0]}.java"
                filePath = "${chosenFile.path}/$fileName"

                // Check the correctness of a class name
                if (!Regex("[A-Z][a-zA-Z0-9]*(.java)?").matches(className)) {
                    showErrorWindow(TestSparkLabelsBundle.defaultValue("incorrectFileNameMessage"))
                    continue
                }

                // Check the existence of a file with this name
                if (File(filePath).exists()) {
                    showErrorWindow(TestSparkLabelsBundle.defaultValue("fileAlreadyExistsMessage"))
                    continue
                }
                break
            }

            // Create new file and set services of this file
            WriteCommandAction.runWriteCommandAction(project) {
                chosenFile.createChildData(null, fileName)
                virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")!!
                psiJavaFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as PsiJavaFile)
                psiClass = PsiElementFactory.getInstance(project).createClass(className.split(".")[0])

                if (project.service<Workspace>().testGenerationData.runWith.isNotEmpty()) {
                    psiClass!!.modifierList!!.addAnnotation("RunWith(${project.service<Workspace>().testGenerationData.runWith})")
                }

                psiJavaFile!!.add(psiClass!!)
            }
        } else {
            // Set services of the chosen file
            virtualFile = chosenFile
            psiJavaFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as PsiJavaFile)
            psiClass = psiJavaFile!!.classes[
                psiJavaFile!!.classes.stream().map { it.name }.toArray()
                    .indexOf(psiJavaFile!!.name.removeSuffix(".java")),
            ]
        }

        // Add tests to the file
        WriteCommandAction.runWriteCommandAction(project) {
            appendTestsToClass(testCaseComponents, psiClass!!, psiJavaFile!!)
        }

        // The scheduled tests will be submitted in the background
        // (they will be checked every 5 minutes and also when the project is closed)
        scheduleTelemetry(selectedTestCases)

        // Remove the selected test cases from the cache and the tool window UI
        removeSelectedTestCases(selectedTestCasePanels)

        // Open the file after adding
        FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptor(project, virtualFile!!),
            true,
        )
    }

    private fun showErrorWindow(message: String) {
        JOptionPane.showMessageDialog(
            null,
            message,
            TestSparkLabelsBundle.defaultValue("errorWindowTitle"),
            JOptionPane.ERROR_MESSAGE,
        )
    }

    /**
     * Retrieve the editor corresponding to a particular test case
     *
     * @param testCase the name of the test case
     * @return the editor corresponding to the test case, or null if it does not exist
     */
    private fun getEditor(testCase: String): EditorTextField? {
        val middlePanelComponent = testCasePanels[testCase]?.getComponent(2) ?: return null
        val middlePanel = middlePanelComponent as JPanel
        return middlePanel.getComponent(1) as EditorTextField
    }

    /**
     * Append the provided test cases to the provided class.
     *
     * @param testCaseComponents the test cases to be appended
     * @param selectedClass the class which the test cases should be appended to
     * @param outputFile the output file for tests
     */
    private fun appendTestsToClass(testCaseComponents: List<String>, selectedClass: PsiClass, outputFile: PsiJavaFile) {
        // block document
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(
            PsiDocumentManager.getInstance(project).getDocument(outputFile)!!,
        )

        // insert tests to a code
        testCaseComponents.forEach {
            PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
                selectedClass.rBrace!!.textRange.startOffset,
                // Fix Windows line separators
                project.service<JavaClassBuilderService>().getTestMethodFromClassWithTestCaseName(
                    it.replace("\r\n", "\n").replace("verifyException(", "// verifyException("),
                ) + "\n",
            )
        }

        // insert other info to a code
        PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
            selectedClass.rBrace!!.textRange.startOffset,
            project.service<Workspace>().testGenerationData.otherInfo + "\n",
        )

        // insert imports to a code
        PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
            outputFile.importList?.startOffset ?: outputFile.packageStatement?.startOffset ?: 0,
            project.service<Workspace>().testGenerationData.importsCode.joinToString("\n") + "\n\n",
        )

        // insert package to a code
        outputFile.packageStatement ?: PsiDocumentManager.getInstance(project).getDocument(outputFile)!!
            .insertString(
                0,
                if (project.service<Workspace>().testGenerationData.packageLine.isEmpty()) {
                    ""
                } else {
                    "package ${project.service<Workspace>().testGenerationData.packageLine};\n"
                },
            )
    }

    /**
     * Creates a new toolWindow tab for the coverage visualisation.
     */
    private fun createToolWindowTab() {
        // Remove generated tests tab from content manager if necessary
        val toolWindowManager = ToolWindowManager.getInstance(project).getToolWindow("TestSpark")
        contentManager = toolWindowManager!!.contentManager
        if (content != null) {
            contentManager!!.removeContent(content!!, true)
        }

        // If there is no generated tests tab, make it
        val contentFactory: ContentFactory = ContentFactory.getInstance()
        content = contentFactory.createContent(
            mainPanel,
            TestSparkLabelsBundle.defaultValue("generatedTests"),
            true,
        )
        contentManager!!.addContent(content!!)

        // Focus on generated tests tab and open toolWindow if not opened already
        contentManager!!.setSelectedContent(content!!)
        toolWindowManager.show()
    }

    /**
     * Closes the tool window and destroys the content of the tab.
     */
    private fun closeToolWindow() {
        contentManager?.removeContent(content!!, true)
        ToolWindowManager.getInstance(project).getToolWindow("TestSpark")?.hide()
        val coverageVisualisationService = project.service<CoverageVisualisationService>()
        coverageVisualisationService.closeToolWindowTab()
    }

    /**
     * Creates a button to remove a test from the cache and from the UI.
     *
     * @param test the test case
     * @param editor the currently opened editor
     * @param testCasePanel the test case panel
     * @return the created button
     */
    private fun createRemoveButton(test: TestCase, editor: Editor, testCasePanel: JPanel): JButton {
        val removeButton = createButton(TestSparkIcons.remove, TestSparkLabelsBundle.defaultValue("removeTip"))
        removeButton.addActionListener {
            // Remove the highlighting of the test
            project.messageBus.syncPublisher(COVERAGE_SELECTION_TOGGLE_TOPIC)
                .testGenerationResult(test.testName, false, editor)

            // Update the number of selected test cases if necessary
            if ((testCasePanel.getComponent(0) as JCheckBox).isSelected) testsSelected -= 1

            // Remove the test case from the cache
            removeTestCase(test.testName)

            // Update the UI of the tool window tab
            allTestCasePanel.updateUI()

            // Passed tests update
            project.service<TestsExecutionResultService>().removeFromFailingTest(test.testName)
            topButtonsPanel.updateTopLabels()

            // If no more tests are remaining, close the tool window
            if (testCasePanels.size == 0) closeToolWindow()
        }
        return removeButton
    }

    /**
     * Removes the selected tests from the cache, removes all the highlights from the editor and closes the tool window.
     * This function is called when the user clicks "Apply to test suite" button,
     *  and it is also called with all test cases as selected when the user clicks "Remove All" button.
     *
     * @param selectedTestCasePanels the panels of the selected tests
     */
    private fun removeSelectedTestCases(selectedTestCasePanels: Map<String, JPanel>) {
        selectedTestCasePanels.forEach { removeTestCase(it.key) }
        removeAllHighlights()
        closeToolWindow()
    }

    fun clear() {
        // Remove the tests
        val testCasePanelsToRemove = testCasePanels.toMap()
        removeSelectedTestCases(testCasePanelsToRemove)
    }

    /**
     * A helper method to remove a test case from the cache and from the UI.
     *
     * @param testName the name of the test
     */
    private fun removeTestCase(testName: String) {
        // Remove the test from the cache
        project.service<TestCaseCachingService>()
            .invalidateFromCache(project.service<Workspace>().testGenerationData.fileUrl, originalTestCases[testName]!!)

        // Remove the test panel from the UI
        allTestCasePanel.remove(testCasePanels[testName])

        // Remove the test panel
        testCasePanels.remove(testName)
    }

    /**
     * Creates a button to reset the changes in the test source code.
     *
     * @return the created button
     */
    private fun createResetButton(): JButton {
        val resetButton = createButton(TestSparkIcons.reset, TestSparkLabelsBundle.defaultValue("resetTip"))
        resetButton.isEnabled = false
        return resetButton
    }

    /**
     * Creates a button to reset the changes in the test source code.
     *
     * @return the created button
     */
    private fun createResetToLastRunButton(): JButton {
        val resetButton =
            createButton(TestSparkIcons.resetToLastRun, TestSparkLabelsBundle.defaultValue("resetToLastRunTip"))
        resetButton.isEnabled = false
        return resetButton
    }

    /**
     * Creates a button to reset the changes in the test source code.
     *
     * @return the created button
     */
    private fun createRunTestButton(): JButton {
        val runTestButton = JButton("Run", TestSparkIcons.runTest)
        runTestButton.isEnabled = false
        runTestButton.isOpaque = false
        runTestButton.isContentAreaFilled = false
        runTestButton.isBorderPainted = true
        runTestButton.preferredSize = Dimension(60, 30)
        return runTestButton
    }

    /**
     * A helper method to add a listener to the test document (in the tool window panel)
     *   that enables reset button when the editor is changed.
     *
     * @param resetButton the button to reset changes in the test
     * @param languageTextField the text field editor with the test
     * @param checkbox the checkbox to select the test
     */
    private fun addListeners(
        resetButton: JButton,
        resetToLastRunButton: JButton,
        runTestButton: JButton,
        likeButton: JButton,
        dislikeButton: JButton,
        copyButton: JButton,
        errorLabel: JLabel,
        languageTextField: EditorTextField,
        checkbox: JCheckBox,
        testCase: TestCase,
        initialBorder: Border,
    ) {
        languageTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val lastRunCode =
                    project.service<Workspace>().testJob!!.report.testCaseList[testCase.testName]!!.testCode
                languageTextField.editor!!.markupModel.removeAllHighlighters()

                resetButton.isEnabled = languageTextField.document.text != testCase.testCode
                resetToLastRunButton.isEnabled = languageTextField.document.text != lastRunCode
                runTestButton.isEnabled =
                    languageTextField.document.text != lastRunCode && languageTextField.document.text != testCase.testCode

                languageTextField.border =
                    when (languageTextField.document.text) {
                        testCase.testCode -> initialBorder
                        lastRunCode -> getBorder(testCase.testName)
                        else -> JBUI.Borders.empty()
                    }

                updateErrorLabel(errorLabel, testCase.testName)

                val modifiedLineIndexes = getModifiedLines(
                    lastRunCode.split("\n"),
                    languageTextField.document.text.split("\n"),
                )

                for (index in modifiedLineIndexes) {
                    languageTextField.editor!!.markupModel.addLineHighlighter(
                        DiffColors.DIFF_MODIFIED,
                        index,
                        HighlighterLayer.FIRST,
                    )
                }

                // select checkbox
                checkbox.isSelected = true
            }
        })

        resetButton.addActionListener {
            WriteCommandAction.runWriteCommandAction(project) {
                languageTextField.document.setText(testCase.testCode)
                project.service<Workspace>().updateTestCase(testCase)
                resetButton.isEnabled = false
                if ((initialBorder as MatteBorder).matteColor == JBColor.GREEN) {
                    project.service<TestsExecutionResultService>().removeFromFailingTest(testCase.testName)
                } else {
                    project.service<TestsExecutionResultService>()
                        .addFailedTest(testCase.testName, errorLabel.toolTipText)
                }
                resetToLastRunButton.isEnabled = false
                runTestButton.isEnabled = false
                languageTextField.border = initialBorder
                updateErrorLabel(errorLabel, testCase.testName)
                languageTextField.editor!!.markupModel.removeAllHighlighters()

                topButtonsPanel.updateTopLabels()
            }
        }

        resetToLastRunButton.addActionListener {
            WriteCommandAction.runWriteCommandAction(project) {
                languageTextField.document.setText(project.service<Workspace>().testJob!!.report.testCaseList[testCase.testName]!!.testCode)
                resetToLastRunButton.isEnabled = false
                runTestButton.isEnabled = false
                languageTextField.border = getBorder(testCase.testName)
                updateErrorLabel(errorLabel, testCase.testName)
                languageTextField.editor!!.markupModel.removeAllHighlighters()

                topButtonsPanel.updateTopLabels()
            }
        }

        runTestButton.addActionListener {
            project.service<Workspace>().updateTestCase(
                project.service<TestCoverageCollectorService>()
                    .updateDataWithTestCase(languageTextField.document.text, testCase.testName),
            )
            resetToLastRunButton.isEnabled = false
            runTestButton.isEnabled = false
            languageTextField.border = getBorder(testCase.testName)
            updateErrorLabel(errorLabel, testCase.testName)
            languageTextField.editor!!.markupModel.removeAllHighlighters()

            topButtonsPanel.updateTopLabels()
        }

        likeButton.addActionListener {
            if (likeButton.icon == TestSparkIcons.likeSelected) {
                likeButton.icon = TestSparkIcons.like
            } else if (likeButton.icon == TestSparkIcons.like) {
                likeButton.icon = TestSparkIcons.likeSelected
            }
            dislikeButton.icon = TestSparkIcons.dislike
//            TODO add implementation
        }

        dislikeButton.addActionListener {
            if (dislikeButton.icon == TestSparkIcons.dislikeSelected) {
                dislikeButton.icon = TestSparkIcons.dislike
            } else if (dislikeButton.icon == TestSparkIcons.dislike) {
                dislikeButton.icon = TestSparkIcons.dislikeSelected
            }
            likeButton.icon = TestSparkIcons.like
//            TODO add implementation
        }

        copyButton.addActionListener {
            val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(languageTextField.document.text), null)
        }
    }

    /**
     * Returns the indexes of lines that are modified between two lists of strings.
     *
     * @param source The source list of strings.
     * @param target The target list of strings.
     * @return The indexes of modified lines.
     */
    fun getModifiedLines(source: List<String>, target: List<String>): List<Int> {
        val dp = Array(source.size + 1) { IntArray(target.size + 1) }

        for (i in 1..source.size) {
            for (j in 1..target.size) {
                if (source[i - 1] == target[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        var i = source.size
        var j = target.size

        val modifiedLineIndexes = mutableListOf<Int>()

        while (i > 0 && j > 0) {
            if (source[i - 1] == target[j - 1]) {
                i--
                j--
            } else if (dp[i][j] == dp[i - 1][j]) {
                i--
            } else if (dp[i][j] == dp[i][j - 1]) {
                modifiedLineIndexes.add(j - 1)
                j--
            }
        }

        while (j > 0) {
            modifiedLineIndexes.add(j - 1)
            j--
        }

        modifiedLineIndexes.reverse()

        return modifiedLineIndexes
    }

    /**
     * Schedules the telemetry for the selected and modified tests.
     *
     * @param selectedTestCases the test cases selected by the user
     */
    private fun scheduleTelemetry(selectedTestCases: List<String>) {
        val telemetryService = project.service<TestSparkTelemetryService>()
        telemetryService.scheduleTestCasesForTelemetry(
            selectedTestCases.map {
                val modified = getEditor(it)!!.text
                val original = originalTestCases[it]!!

                TestSparkTelemetryService.ModifiedTestCase(original, modified)
            }.filter { it.modified != it.original },
        )
    }

    /**
     * Returns the border for a given test case.
     *
     * @param testCaseName the name of the test case
     * @return the border for the test case
     */
    private fun getBorder(testCaseName: String): Border {
        val size = 3
        return if (project.service<TestsExecutionResultService>().isTestCaseFailing(testCaseName)) {
            MatteBorder(size, size, size, size, JBColor.RED)
        } else {
            MatteBorder(size, size, size, size, JBColor.GREEN)
        }
    }

    /**
     * Updates the error label with a new message.
     *
     * @param errorLabel the label to be updated with the error message
     */
    private fun updateErrorLabel(errorLabel: JLabel, testCaseName: String) {
        val error = project.service<TestsExecutionResultService>().getError(testCaseName)
        if (error.isBlank()) {
            errorLabel.isVisible = false
        } else {
            errorLabel.isVisible = true
            errorLabel.toolTipText = error
        }
    }

    /**
     * Retrieves the list of test case panels.
     *
     * @return The list of test case panels.
     */
    fun getTestCasePanels() = testCasePanels

    /**
     * Retrieves the currently selected tests.
     *
     * @return The list of tests currently selected.
     */
    fun getTestsSelected() = testsSelected

    /**
     * Sets the number of tests selected.
     *
     * @param testsSelected The number of tests selected.
     */
    fun setTestsSelected(testsSelected: Int) { this.testsSelected = testsSelected }
}
