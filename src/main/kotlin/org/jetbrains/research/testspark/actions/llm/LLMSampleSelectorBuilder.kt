package org.jetbrains.research.testspark.actions.llm

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.stream
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

    private val defaultTestName = "<html>provide manually</html>"
    private val defaultTestCode = "// provide test method code here"
    private val testNames = mutableSetOf(defaultTestName)
    private val initialTestCodes = mutableListOf(createTestSampleClass("", defaultTestCode))
    private val testSamplePanelFactories: MutableList<TestSamplePanelBuilder> = mutableListOf()
    private var testSamplesCode: String = ""

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
                testSamplesCode += "Test sample number ${index + 1}\n```\n${testSamplePanelFactories[index].getCode()}\n```\n"
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
    fun getTestSamplesCode(): String = testSamplesCode

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
            collectTestSamples()
            val testSamplePanelBuilder =
                TestSamplePanelBuilder(project, middlePanel, testNames.toMutableList(), initialTestCodes, language)
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

    private fun collectTestSamples() {
        val currentDocument = FileEditorManager.getInstance(project).selectedTextEditor?.document
        val currentFile = currentDocument?.let { FileDocumentManager.getInstance().getFile(it) }
        collectTestSamplesForCurrentFile(currentFile)

        if (testNames.size == 1) {
            // Only the default test name is there, thus we did not find any tests related to the
            // current file; collect all test samples and provide them to the user instead
            collectTestSamples(project)
        }
    }

    fun collectTestSamplesForCurrentFile(currentFile: VirtualFile?) {
        val javaFileType: FileType = FileTypeManager.getInstance().getFileTypeByExtension("java")
        val projectScope = ProjectAndLibrariesScope(project)
        if (currentFile!!.fileType === javaFileType) {
            val psiJavaFile = (PsiManager.getInstance(project).findFile(currentFile!!) as PsiJavaFile)
            val psiClass = psiJavaFile.classes[
                psiJavaFile.classes.stream().map { it.name }.toArray()
                    .indexOf(psiJavaFile.name.removeSuffix(".java")),
            ]
            psiClass.methods.forEach { method ->
                ReferencesSearch.search(method, projectScope).forEach { reference ->
                    val enclosingMethod = PsiTreeUtil.getParentOfType(
                        reference.element,
                        PsiMethod::class.java,
                    )
                    if (enclosingMethod != null) {
                        val enclosingClass = enclosingMethod.containingClass
                        val enclosingFile = (enclosingMethod.containingFile as PsiJavaFile)
                        val imports = retrieveImportStatements(enclosingFile, enclosingClass!!)
                        processCandidateMethod(enclosingMethod, imports, psiClass)
                    }
                }
            }
        }
    }

    private fun retrieveImportStatements(psiJavaFile: PsiJavaFile, psiClass: PsiClass): String {
        var imports = psiJavaFile.importList?.allImportStatements?.map { it.text }?.toList()
            ?.joinToString("\n") ?: ""
        if (psiClass.qualifiedName != null && psiClass.qualifiedName!!.contains(".")) {
            imports += "\nimport ${psiClass.qualifiedName?.substringBeforeLast(".") + ".*"};"
        }
        return imports
    }

    private fun processCandidateMethod(psiMethod: PsiMethod, imports: String, psiClass: PsiClass) {
        val annotations = psiMethod.annotations
        annotations.forEach { annotation ->
            if (annotation.qualifiedName == "org.junit.jupiter.api.Test" ||
                annotation.qualifiedName == "org.junit.Test"
            ) {
                val code: String = createTestSampleClass(imports, psiMethod.text)
                if (testNames.add(createMethodName(psiClass, psiMethod))) {
                    initialTestCodes.add(code)
                }
            }
        }
    }

    /**
     * Retrieves a list of test samples from the given project.
     *
     * @return A list of strings, representing the names of the test samples.
     */
    fun collectTestSamples(project: Project) {
        val projectFileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        val javaFileType: FileType = FileTypeManager.getInstance().getFileTypeByExtension("java")

        projectFileIndex.iterateContent { file ->
            if (file.fileType === javaFileType) {
                try {
                    val psiJavaFile = (PsiManager.getInstance(project).findFile(file) as PsiJavaFile)
                    val psiClass = psiJavaFile.classes[
                        psiJavaFile.classes.stream().map { it.name }.toArray()
                            .indexOf(psiJavaFile.name.removeSuffix(".java")),
                    ]
                    var imports = retrieveImportStatements(psiJavaFile, psiClass)
                    psiClass.allMethods.forEach { method ->
                        val annotations = method.modifierList.annotations
                        annotations.forEach { annotation ->
                            if (annotation.qualifiedName == "org.junit.jupiter.api.Test" || annotation.qualifiedName == "org.junit.Test") {
                                val code: String = createTestSampleClass(imports, method.text)
                                testNames.add(createMethodName(psiClass, method))
                                initialTestCodes.add(code)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            true
        }
    }

    private fun createTestSampleClass(imports: String, methodCode: String): String {
        var normalizedImports = imports
        if (normalizedImports.isNotBlank()) normalizedImports += "\n\n"
        return normalizedImports +
            "public class TestSample {\n" +
            "   $methodCode\n" +
            "}"
    }

    private fun createMethodName(psiClass: PsiClass, method: PsiMethod): String =
        "<html>${psiClass.qualifiedName}#${method.name}</html>"
}
