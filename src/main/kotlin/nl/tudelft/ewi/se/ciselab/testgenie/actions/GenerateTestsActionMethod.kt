package nl.tudelft.ewi.se.ciselab.testgenie.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import nl.tudelft.ewi.se.ciselab.testgenie.evosuite.Runner
import nl.tudelft.ewi.se.ciselab.testgenie.helpers.generateMethodDescriptor

/**
 * This class contains all the logic related to generating tests for a method.
 * No actual generation happens in this class, rather it is responsible for displaying the action option to the user when it is available,
 *   getting the information about the selected method and passing it to (EvoSuite) Runner.
 */
class GenerateTestsActionMethod : AnAction() {
    private val logger: Logger = Logger.getInstance(this.javaClass)

    /**
     * Creates and calls (EvoSuite) Runner to generate tests for a method when the action is invoked.
     *
     * @param e an action event that contains useful information and corresponds to the action invoked by the user
     */
    override fun actionPerformed(e: AnActionEvent) {
        val evoSuiteRunner: Runner = createEvoSuiteRunner(e) ?: return

        val psiFile: PsiFile = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        val caret: Caret = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return

        val psiMethod: PsiMethod = getSurroundingMethod(psiFile, caret) ?: return
        val methodDescriptor = generateMethodDescriptor(psiMethod)

        logger.info("Selected method is $methodDescriptor")

        evoSuiteRunner.forMethod(methodDescriptor).runTestGeneration()
    }

    /**
     * Makes the action visible only if a method has been selected.
     * It also updates the action name depending on which method has been selected.
     *
     * @param e an action event that contains useful information and corresponds to the action invoked by the user
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = false

        val caret: Caret = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return
        val psiFile: PsiFile = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return

        val psiMethod: PsiMethod = getSurroundingMethod(psiFile, caret) ?: return

        e.presentation.isEnabledAndVisible = true
        e.presentation.text = "Generate Tests For ${getMethodDisplayName(psiMethod)}"
    }
}
