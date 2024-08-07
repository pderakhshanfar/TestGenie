package org.jetbrains.research.testspark.tools.kex

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.settings.kex.KexSettingsState
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class KexSettingsArguments {
    fun buildCommand(
        javaExecPath: String,
        projectContext: ProjectContext,
        classFQN: String,
        resultName: String,
        kexSettingsState: KexSettingsState,
        kexExecPath: String,
        kexHome: String,
    ): MutableList<String> {
        val HEAP_SIZE = "8"
        val cmd = mutableListOf<String>(
            javaExecPath,
            "-Xmx${HEAP_SIZE}g", // TODO 8g heapsize in properties bundle
            "-Djava.security.manager",
            "-Djava.security.policy==$kexHome/kex.policy",
            "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
        )

        File("$kexHome/runtime-deps/modules.info").readLines().forEach { cmd.add("--add-opens"); cmd.add(it) }
        cmd.add("--illegal-access=warn")

        cmd.addAll(
            listOf(
                "-jar",
                kexExecPath,
                "--classpath",
                getBuildOutputDirectory(projectContext.cutModule!!)!!.toString(),
                "--target",
                classFQN,
                "--output",
                resultName,
                "--mode",
                kexSettingsState.kexMode.toString(),
            ),
        )

        // adding user provided command line arguments
        if (kexSettingsState.option.isNotBlank()) {
            cmd.add("--option")
            cmd.add(kexSettingsState.option)
        }
        return cmd
    }

    fun getBuildOutputDirectory(module: Module): Path? {
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val compilerProjectExtension = moduleRootManager.getModuleExtension(CompilerModuleExtension::class.java)
        return compilerProjectExtension?.compilerOutputUrl?.let { Paths.get(VfsUtil.urlToPath(it)) }
    }
}
