package org.jetbrains.research.testspark.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import org.jetbrains.research.testspark.actions.controllers.RunnerController
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.custom.IJProgressIndicator
import org.jetbrains.research.testspark.helpers.PsiHelper
import org.jetbrains.research.testspark.services.ErrorService
import org.jetbrains.research.testspark.display.TestSparkDisplayFactory
import org.jetbrains.research.testspark.services.TestsExecutionResultService
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.util.UUID

/**
 * Pipeline class represents a pipeline for generating tests in a project.
 *
 * @param project the project in which the pipeline is executed
 * @param psiFile the PSI file in which the pipeline is executed
 * @param caretOffset the offset of the caret position in the PSI file
 * @param fileUrl the URL of the file being processed, if applicable
 * @param packageName the package name of the file being processed
 */
class Pipeline(
    private val project: Project,
    psiFile: PsiFile,
    caretOffset: Int,
    fileUrl: String?,
    private val packageName: String,
    private val runnerController: RunnerController,
) {
    val projectContext: ProjectContext = ProjectContext()
    val generatedTestsData = TestGenerationData()

    init {
        val cutPsiClass = PsiHelper.getSurroundingClass(psiFile, caretOffset)!!

        // get generated test path
        val testResultDirectory = "${FileUtilRt.getTempDirectory()}${ToolUtils.sep}testSparkResults$ToolUtils.sep"
        val id = UUID.randomUUID().toString()
        val testResultName = "test_gen_result_$id"

        ApplicationManager.getApplication().runWriteAction {
            projectContext.projectClassPath = ProjectRootManager.getInstance(project).contentRoots.first().path
            projectContext.fileUrlAsString = fileUrl
            projectContext.cutPsiClass = cutPsiClass
            projectContext.classFQN = cutPsiClass.qualifiedName!!
            projectContext.cutModule =
                ProjectFileIndex.getInstance(project).getModuleForFile(cutPsiClass.containingFile.virtualFile)!!
        }

        generatedTestsData.resultPath = ToolUtils.getResultPath(id, testResultDirectory)
        generatedTestsData.baseDir = "${testResultDirectory}$testResultName-validation"
        generatedTestsData.testResultName = testResultName

        DataFilesUtil.makeTmp(FileUtilRt.getTempDirectory())
        DataFilesUtil.makeDir(generatedTestsData.baseDir!!)
    }

    /**
     * Builds the project and launches generation on a separate thread.
     */
    fun runTestGeneration(processManager: ProcessManager, codeType: FragmentToTestData) {
        clear(project)
        val projectBuilder = ProjectBuilder(project)

        var editor: Editor? = null
        var uiContext: UIContext? = null

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, PluginMessagesBundle.get("testGenerationMessage")) {
                override fun run(indicator: ProgressIndicator) {
                    val ijIndicator = IJProgressIndicator(indicator)

                    if (ToolUtils.isProcessStopped(project, ijIndicator)) return

                    if (projectBuilder.runBuild(ijIndicator)) {
                        if (ToolUtils.isProcessStopped(project, ijIndicator)) return

                        uiContext = processManager.runTestGenerator(
                            ijIndicator,
                            codeType,
                            packageName,
                            projectContext,
                            generatedTestsData,
                        )
                    }

                    if (ToolUtils.isProcessStopped(project, ijIndicator)) return

                    ijIndicator.stop()
                }

                override fun onFinished() {
                    super.onFinished()
                    runnerController.finished()
                    uiContext?.let {
                        updateEditor(it.testGenerationOutput.fileUrl)

                        if (editor != null) {
                            TestSparkDisplayFactory(project).displayTestCases(it.testGenerationOutput.testGenerationResultList[0]!!, editor!!, it)
                        }
                    }
                }

                /**
                 * Utility function that returns the editor for a specific file url,
                 * in case it is opened in the IDE
                 */
                private fun updateEditor(fileUrl: String) {
                    val documentManager = FileDocumentManager.getInstance()
                    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/360004480599/comments/360000703299
                    FileEditorManager.getInstance(project).selectedEditors.map { it as TextEditor }.map { it.editor }
                        .map {
                            val currentFile = documentManager.getFile(it.document)
                            if (currentFile != null) {
                                if (currentFile.presentableUrl == fileUrl) {
                                    editor = it
                                }
                            }
                        }
                }
            })
    }

    private fun clear(project: Project) { // should be removed totally!
        project.service<ErrorService>().clear()
        project.service<TestsExecutionResultService>().clear()
    }
}
