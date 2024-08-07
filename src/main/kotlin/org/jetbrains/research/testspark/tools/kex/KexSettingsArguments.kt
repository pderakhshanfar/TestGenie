package org.jetbrains.research.testspark.tools.kex

import org.jetbrains.research.testspark.settings.kex.KexSettingsState
import java.io.File

class KexSettingsArguments {
    fun buildCommand(
        javaExecPath: String,
        projectClassPath: String,
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
                "$projectClassPath/target/classes", // TODO how to reliably get the path to 'root of class files', of the class for which tests are generated
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
}
