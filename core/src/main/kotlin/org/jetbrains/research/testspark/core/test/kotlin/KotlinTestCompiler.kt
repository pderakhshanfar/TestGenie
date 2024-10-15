package org.jetbrains.research.testspark.core.test.kotlin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.research.testspark.core.exception.ClassFileNotFoundException
import org.jetbrains.research.testspark.core.test.ExecutionResult
import org.jetbrains.research.testspark.core.exception.KotlinCompilerNotFoundException
import org.jetbrains.research.testspark.core.test.TestCompiler
import org.jetbrains.research.testspark.core.utils.CommandLineRunner
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import java.io.File

class KotlinTestCompiler(
    libPaths: List<String>,
    junitLibPaths: List<String>,
    kotlinSDKHomeDirectory: String,
    private val javaHomeDirectoryPath: String,
) : TestCompiler(libPaths, junitLibPaths) {
    private val logger = KotlinLogging.logger { this::class.java }
    private val kotlinc: String

    // init block to find the kotlinc compiler
    init {
        // search for a proper kotlinc
        val kotlinCompiler = File(kotlinSDKHomeDirectory).walk()
            .filter {
                /**
                 * Tested on Windows 10, IntelliJ IDEA Community Edition 2023.1.4 (2023.1.4.IC-231.9225.26)
                 *
                 * Windows' kotlinc requires `java` command to be present in ENV (e.g., present in PATH).
                 * Otherwise, it won't be able to execute itself.
                 *
                 * Missing `java` in PATH does not yield runtime error but is considered
                 * as failed compilation because `kotlinc` will complain about
                 * `java` command missing in PATH.
                 *
                 */
                val isCompilerName = if (DataFilesUtil.isWindows()) {
                    it.name.equals("kotlinc")
                } else {
                    it.name.equals("kotlinc")
                }
                isCompilerName && it.isFile
            }.firstOrNull()

        if (kotlinCompiler == null) {
            val msg = "Cannot find Kotlin compiler 'kotlinc' at $kotlinSDKHomeDirectory"
            logger.error { msg }
            throw KotlinCompilerNotFoundException("Please make sure that the Kotlin plugin is installed and enabled. $msg.")
        }

        kotlinc = kotlinCompiler.absolutePath
    }

    override fun compileCode(path: String, projectBuildPath: String, workingDir: String): ExecutionResult {
        logger.info { "[KotlinTestCompiler] Compiling ${path.substringAfterLast('/')}" }

        val classPaths = "\"${getClassPaths(projectBuildPath)}\""

        // We need to ensure JAVA is in the path variable
        // See: https://github.com/JetBrains-Research/TestSpark/issues/410
        val setJavaPathOnWindows = "set PATH=%PATH%;$javaHomeDirectoryPath\\bin\\&&"

        // Compile file
        // See: https://github.com/JetBrains-Research/TestSpark/issues/402
        val kotlinc = if (DataFilesUtil.isWindows()) "$setJavaPathOnWindows\"$kotlinc\"" else "'$kotlinc'"

        val executionResult = CommandLineRunner.run(
            arrayListOf(
                /**
                 * Filepath may contain spaces, so we need to wrap it in quotes.
                 */
                kotlinc,
                "-cp",
                classPaths,
                path,
                /**
                 * Forcing kotlinc to save a classfile in the same place, as '.kt' file
                 */
                "-d",
                workingDir,
            ),
        )
        logger.info { "Exit code: '${executionResult.exitCode}'; Execution message: '${executionResult.executionMessage}'" }

        val classFilePath = path.removeSuffix(".kt") + ".class"
        if (executionResult.exitCode == 0 && !File(classFilePath).exists()) {
            throw ClassFileNotFoundException("Expected class file at $classFilePath after the compilation of file $path, but it does not exist.")
        }
        return executionResult
    }

    override fun getClassPaths(buildPath: String): String = commonPath.plus(buildPath)
}
