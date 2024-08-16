package org.jetbrains.research.testspark.tools.kex

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.research.testspark.bundles.kex.KexDefaultsBundle
import org.jetbrains.research.testspark.settings.kex.KexSettingsState
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class KexSettingsArguments(
    private val javaExecPath: String,
    private val javaVersion: Int,
    private val module: Module,
    private val target: String,
    private val resultName: String,
    private val kexSettingsState: KexSettingsState,
    private val kexExecPath: String,
    private val kexHome: String,
) {
    fun buildCommand(): MutableList<String> {
        return mutableListOf<String>(javaExecPath)
            .setJvmProperties()
            .setAddOpensArgs(javaVersion)
            .setKexParams()
            .setKexIniOptions()
    }

    private fun MutableList<String>.setJvmProperties(): MutableList<String> {
        return this.addAll(
            "-Xmx${KexDefaultsBundle.get("heapSize")}g",
            "-Djava.security.manager",
            "-Djava.security.policy==$kexHome/kex.policy",
            "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
        )
    }

    private fun MutableList<String>.setAddOpensArgs(javaVersion: Int): MutableList<String> {
        if (javaVersion > 8) {
            File("$kexHome/runtime-deps/modules.info").readLines().forEach { this.addAll("--add-opens", it) }
        }
        return this
    }

    /**
     * @see kex readme
     */
    private fun MutableList<String>.setKexParams(): MutableList<String> {
        return this.addAll(
            "-jar",
            kexExecPath,
            "--classpath",
            getBuildOutputDirectory(module)!!.toString(),
            "--target",
            "\"$target\"",
            "--output",
            resultName,
            "--mode",
            kexSettingsState.kexMode.toString(),
        )
    }

    /**
     * Kex uses a configuration file kex.ini.
     * This can effectively be modified on an invocation through the --option cmd line arg
     */
    private fun MutableList<String>.setKexIniOptions(): MutableList<String> {
        // Add options provided with help of settings ui
        val optionStrings = mutableListOf(
            listOf("kex", "minimizeTestSuite", KexDefaultsBundle.get("minimizeTestSuite")),
            listOf("testGen", "maxTests", kexSettingsState.maxTests.toString()),
            listOf("concolic", "timeLimit", "${kexSettingsState.timeLimit.inWholeSeconds}"),
            listOf("symbolic", "timeLimit", "${kexSettingsState.timeLimit.inWholeSeconds}"),
        )
            .map { it.joinToString(":") }
            .toMutableList()

        // adding explicitly provided user option
        // break into a list of options if multiple are provided
        if (kexSettingsState.otherOptions.isNotBlank()) {
            optionStrings.addAll(kexSettingsState.otherOptions.splitToSequence(' '))
        }

        // add --option before every option
        val separator = "--option"
        val interspersed = optionStrings.fold(mutableListOf<String>()) { acc, item ->
            acc.add(separator)
            acc.add(item)
            acc
        }
        this.addAll(interspersed)
        return this
    }

    /**
     * Retrieves the build output directory for the given module.
     * kex works correctly even with multimodule projects
     *
     * @param module The module for which to retrieve the build output directory.
     * @return The path to the build output directory, or null if it cannot be determined.
     */
    private fun getBuildOutputDirectory(module: Module): Path? {
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val compilerProjectExtension = moduleRootManager.getModuleExtension(CompilerModuleExtension::class.java)
        return compilerProjectExtension?.compilerOutputUrl?.let { Paths.get(VfsUtil.urlToPath(it)) }
    }
}

private fun <E> MutableList<E>.addAll(vararg elems: E): MutableList<E> {
    this.addAll(elems)
    return this
}
