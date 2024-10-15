package org.jetbrains.research.testspark.core.test.java

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.research.testspark.core.exception.ClassFileNotFoundException
import org.jetbrains.research.testspark.core.exception.JavaCompilerNotFoundException
import org.jetbrains.research.testspark.core.test.ExecutionResult
import org.jetbrains.research.testspark.core.test.TestCompiler
import org.jetbrains.research.testspark.core.utils.CommandLineRunner
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import java.io.File

class JavaTestCompiler(
    libPaths: List<String>,
    junitLibPaths: List<String>,
    javaHomeDirectoryPath: String,
) : TestCompiler(libPaths, junitLibPaths) {
    private val logger = KotlinLogging.logger { this::class.java }
    private val javac: String

    // init block to find the javac compiler
    init {
        // find the proper javac
        val javaCompiler = File(javaHomeDirectoryPath).walk()
            .filter {
                val isCompilerName = if (DataFilesUtil.isWindows()) {
                    it.name.equals("javac.exe")
                } else {
                    it.name.equals("javac")
                }
                isCompilerName && it.isFile
            }
            .firstOrNull()

        if (javaCompiler == null) {
            val msg = "Cannot find Java compiler 'javac' at $javaHomeDirectoryPath"
            logger.error { msg }
            throw JavaCompilerNotFoundException("Ensure Java SDK is configured for the project. $msg.")
        }
        javac = javaCompiler.absolutePath
    }

    override fun compileCode(path: String, projectBuildPath: String, workingDir: String): ExecutionResult {
        val classPaths = "\"${getClassPaths(projectBuildPath)}\""
        // compile file
        // See: https://github.com/JetBrains-Research/TestSpark/issues/402
        val javac = if (DataFilesUtil.isWindows()) "\"$javac\"" else "'$javac'"

        val executionResult = CommandLineRunner.run(
            arrayListOf(
                /**
                 * Filepath may contain spaces, so we need to wrap it in quotes.
                 */
                javac,
                "-cp",
                classPaths,
                path,
                /**
                 * We don't have to provide -d option, since javac saves class files in the same place by default
                 */
            ),
        )
        logger.info { "Exit code: '${executionResult.exitCode}'; Execution message: '${executionResult.executionMessage}'" }

        val classFilePath = path.replace(".java", ".class")
        if (executionResult.exitCode == 0 && !File(classFilePath).exists()) {
            throw ClassFileNotFoundException("Expected class file at $classFilePath after the compilation of file $path, but it does not exist.")
        }
        return executionResult
    }

    override fun getClassPaths(buildPath: String): String {
        var path = commonPath.plus(buildPath)

        if (path.endsWith(separator)) path = path.removeSuffix(separator.toString())

        return path
    }
}
