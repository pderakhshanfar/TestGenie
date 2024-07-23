package org.jetbrains.research.testspark.tools.kex

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.research.testspark.actions.controllers.TestGenerationController
import org.jetbrains.research.testspark.data.CodeType
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.langwrappers.PsiHelper
import org.jetbrains.research.testspark.langwrappers.PsiMethodWrapper
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.tools.Pipeline
import org.jetbrains.research.testspark.tools.kex.generation.KexProcessManager
import org.jetbrains.research.testspark.tools.template.Tool
import java.io.File

class Kex(override val name: String = "Kex") : Tool {
    private val log = Logger.getInstance(this::class.java)

        /**
     * Returns a new instance of KexProcessManager for the given project.
     *
     * @param project The IntelliJ IDEA project for which the KexProcessManager is created.
     * @return The KexProcessManager instance created for the given project.
     */
    private fun getKexProcessManager(project: Project): KexProcessManager {
        val projectClassPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path
        val settingsProjectState = project.service<PluginSettingsService>().state
        val buildPath = "$projectClassPath${File.separatorChar}${settingsProjectState.buildPath}"
        return KexProcessManager(project, buildPath)
    }

    /**
     * Generates tests for a class using Kex.
     *
     * @param project The current project.
     * @param psiFile The PsiFile containing the class to generate tests for.
     * @param caret The caret position within the PsiFile.
     * @param fileUrl The URL of the file being tested.
     * @param testSamplesCode The code to be used as test samples.
     */
    override fun generateTestsForClass(project: Project, psiHelper: PsiHelper, caretOffset: Int, fileUrl: String?, testSamplesCode: String, testGenerationController: TestGenerationController) {
        log.info("Starting tests generation for class by Kex")
        createPipeline(project, psiHelper, caretOffset, fileUrl, testGenerationController).runTestGeneration(
            getKexProcessManager(project),
            FragmentToTestData(
                CodeType.CLASS,
            ),
        )
    }

    override fun generateTestsForMethod(
        project: Project,
        psiHelper: PsiHelper,
        caretOffset: Int,
        fileUrl: String?,
        testSamplesCode: String,
        testGenerationController: TestGenerationController
    ) {
        log.info("Starting tests generation for method by Kex")
        val psiMethod: PsiMethodWrapper = psiHelper.getSurroundingMethod(caretOffset)!!
        createPipeline(project, psiHelper, caretOffset, fileUrl, testGenerationController).runTestGeneration(
            getKexProcessManager(project),
            FragmentToTestData(
                CodeType.METHOD,
                "${psiMethod.name}(${psiMethod.parameterTypes.joinToString(",")}):${psiMethod.returnType}",
            ),
        )
    }

    override fun generateTestsForLine(
        project: Project,
        psiHelper: PsiHelper,
        caretOffset: Int,
        fileUrl: String?,
        testSamplesCode: String,
        testGenerationController: TestGenerationController
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Creates a pipeline object for the given project, psiFile, caret, and fileUrl.
     * The packageName is determined based on the projectClassPath and the buildPath from the project settings.
     *
     * @param project The project for which to create the pipeline.
     * @param psiFile The psiFile associated with the pipeline.
     * @param caret The caret position in the psiFile.
     * @param fileUrl The URL of the file associated with the pipeline. Can be null.
     * @return The created pipeline object.
     */
    private fun createPipeline(project: Project, psiHelper: PsiHelper, caretOffset: Int, fileUrl: String?, testGenerationController: TestGenerationController): Pipeline {
        val projectClassPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path

        val settingsProjectState = project.service<PluginSettingsService>().state
        val packageName = "$projectClassPath/${settingsProjectState.buildPath}"

        return Pipeline(project, psiHelper, caretOffset, fileUrl, packageName, testGenerationController)
    }
}