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
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.stream

/**
 * A selector for samples for the LLM.
 */
class LLMSampleSelector {
    private val testNames = mutableSetOf(DEFAULT_TEST_NAME)
    private val initialTestCodes = mutableListOf(createTestSampleClass("", DEFAULT_TEST_CODE))
    private var testSamplesCode: String = ""

    /**
     * Retrieves the test samples code.
     *
     * @return The test samples code.
     */
    fun getTestSamplesCode(): String = testSamplesCode

    /**
     * Provides the list of test names.
     *
     * @return The list of test names.
     */
    fun getTestNames(): MutableList<String> = testNames.toMutableList()

    /**
     * Provides the initial test codes.
     *
     * @return The initial test codes
     */
    fun getInitialTestCodes(): MutableList<String> = initialTestCodes

    fun appendTestSampleCode(index: Int, code: String) {
        testSamplesCode += "Test sample number ${index + 1}\n```\n${code}\n```\n"
    }

    /**
     * Collects the test samples for the LLM from the current project.
     */
    fun collectTestSamples(project: Project) {
        val currentDocument = FileEditorManager.getInstance(project).selectedTextEditor?.document
        val currentFile = currentDocument?.let { FileDocumentManager.getInstance().getFile(it) }

        collectTestSamplesForCurrentFile(currentFile!!, project)

        if (testNames.size == 1) {
            // Only the default test name is there, thus we did not find any tests related to the current file;
            // collect all test samples and provide them to the user instead
            collectTestSamplesFromProject(project)
        }
    }

    /**
     * Collects all test methods as samples from a given {@link Project}.
     *
     * @param project The project to retrieve all test samples from.
     */
    private fun collectTestSamplesFromProject(project: Project) {
        val projectFileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).fileIndex

        projectFileIndex.iterateContent { file ->
            if (isJavaFileTypes(file)) {
                try {
                    val psiJavaFile = findJavaFileFromProject(file, project)
                    val psiClass = retrievePsiClass(psiJavaFile)
                    val imports = retrieveImportStatements(psiJavaFile, psiClass)
                    psiClass.allMethods.forEach { method -> processCandidateMethod(method, imports, psiClass) }
                } catch (_: Exception) {
                }
            }
            true
        }
    }

    /**
     * Collect the test samples relevant for the current file.
     *
     * These test samples are those methods that call a method in the current file of a project.
     *
     * @param currentFile The current file.
     * @param project The project.
     */
    private fun collectTestSamplesForCurrentFile(currentFile: VirtualFile, project: Project) {
        val projectScope = ProjectAndLibrariesScope(project)
        if (isJavaFileTypes(currentFile)) {
            val psiJavaFile = findJavaFileFromProject(currentFile, project)
            val psiClass = retrievePsiClass(psiJavaFile)
            psiClass.methods.forEach { method ->
                processMethod(psiClass, method, projectScope)
            }
        }
    }

    /**
     * Returns, whether the file type is a Java file type.
     *
     * @param file The file object.
     * @return True, if the file is a Java file, false otherwise.
     */
    private fun isJavaFileTypes(file: VirtualFile): Boolean {
        val javaFileType: FileType = FileTypeManager.getInstance().getFileTypeByExtension("java")
        return file.fileType === javaFileType
    }

    /**
     * Finds a {@link PsiJavaFile} from a {@link Project} and a {@link VirtualFile}.
     *
     * @param file The virtual file object.
     * @param project The project instance.
     * @return The PSI Java file for the given file and project.
     */
    private fun findJavaFileFromProject(file: VirtualFile, project: Project): PsiJavaFile {
        val psiManager = PsiManager.getInstance(project)
        return psiManager.findFile(file) as PsiJavaFile
    }

    /**
     * Processes a method and searches for methods that reference this method.
     *
     * @param psiClass The class that defines the method.
     * @param psiMethod The method from which the search for references starts.
     * @param scope The scope of the search
     */
    private fun processMethod(psiClass: PsiClass, psiMethod: PsiMethod, scope: SearchScope) {
        ReferencesSearch.search(psiMethod, scope).forEach { reference ->
            val enclosingMethod = PsiTreeUtil.getParentOfType(reference.element, PsiMethod::class.java)
            if (enclosingMethod != null) {
                processEnclosingMethod(enclosingMethod, psiClass)
            }
        }
    }

    /**
     * Processes an enclosing method of a statement.
     *
     * @param enclosingMethod The enclosing method.
     * @param psiClass The class that defines the method.
     */
    private fun processEnclosingMethod(enclosingMethod: PsiMethod, psiClass: PsiClass) {
        val enclosingClass = enclosingMethod.containingClass
        val enclosingFile = (enclosingMethod.containingFile as PsiJavaFile)
        val imports = retrieveImportStatements(enclosingFile, enclosingClass!!)
        processCandidateMethod(enclosingMethod, imports, psiClass)
    }

    /**
     * Retrieves the {@link PsiClass} instance from a {@link PsiJavaFile}.
     *
     * @param psiJavaFile The PSI Java file object
     * @return The PSI class object.
     */
    fun retrievePsiClass(psiJavaFile: PsiJavaFile): PsiClass {
        return psiJavaFile.classes[
            psiJavaFile.classes.stream().map { it.name }.toArray()
                .indexOf(psiJavaFile.name.removeSuffix(".java")),
        ]
    }

    /**
     * Retrieves the import statements for a {@link PsiJavaFile} and {@link PsiClass}.
     *
     * @param psiJavaFile The PSI Java file object.
     * @param psiClass The PSI class object.
     * @return A string of import statements.
     */
    fun retrieveImportStatements(psiJavaFile: PsiJavaFile, psiClass: PsiClass): String {
        var imports = psiJavaFile.importList?.allImportStatements?.map { it.text }?.toList()
            ?.joinToString("\n") ?: ""
        if (psiClass.qualifiedName != null && psiClass.qualifiedName!!.contains(".")) {
            imports += "\nimport ${psiClass.qualifiedName?.substringBeforeLast(".") + ".*"};"
        }
        return imports
    }

    /**
     * Processes a {@link PsiMethod} as a candidate.
     *
     * If it is a candidate, i.e., it is annotated as a JUnit test, add it to {@link #testNames} and
     * {@link #initialTestCodes}, respectively.
     *
     * @param psiMethod The PSI method object.
     * @param imports The imports required for the code generation.
     * @param psiClass The PSI class object.
     */
    private fun processCandidateMethod(psiMethod: PsiMethod, imports: String, psiClass: PsiClass) {
        val annotations = psiMethod.annotations
        annotations.forEach { annotation ->
            if (annotation.qualifiedName == "org.junit.jupiter.api.Test" ||
                annotation.qualifiedName == "org.junit.Test"
            ) {
                val code: String = createTestSampleClass(imports, psiMethod.text)
                testNames.add(createMethodName(psiClass, psiMethod))
                initialTestCodes.add(code)
            }
        }
    }

    /**
     * Creates a class from the imports and the method codes as a test sample.
     *
     * @param imports The imports required for the code.
     * @param methodCode The code of the method.
     * @return A class wrapping the given method.
     */
    fun createTestSampleClass(imports: String, methodCode: String): String {
        var normalizedImports = imports
        if (normalizedImports.isNotBlank()) normalizedImports += "\n\n"
        return normalizedImports +
            "public class TestSample {\n" +
            "    $methodCode\n" +
            "}"
    }

    /**
     * Creates a fully-qualified method name from a class and method instance.
     *
     * @param psiClass The class object.
     * @param method The method object.
     * @return A fully-qualified method name.
     */
    fun createMethodName(psiClass: PsiClass, method: PsiMethod): String =
        "<html>${psiClass.qualifiedName}#${method.name}</html>"

    companion object {
        const val DEFAULT_TEST_NAME = "<html>provide manually</html>"
        const val DEFAULT_TEST_CODE = "// provide test method code here"
    }
}
