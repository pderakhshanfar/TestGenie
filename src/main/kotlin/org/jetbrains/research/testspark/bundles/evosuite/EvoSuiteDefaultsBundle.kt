package org.jetbrains.research.testspark.bundles.evosuite

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Loads the `resources` directory.
 */
object EvoSuiteDefaultsBundle : DynamicBundle(EvoSuiteBundlePaths.defaults) {

    /**
     * Gets the requested default value.
     */
    @Nls
    fun get(@PropertyKey(resourceBundle = EvoSuiteBundlePaths.defaults) key: String): String = getMessage(key)
        // In Intellij Platform version 2, the DynamicBundle returns the whole path and the value at the end in plugin verification.
        // Each is separated by "|" (e.g., "|b|properties.llm.LLMDefaults|k|maxLLMRequest|3")
        // if we do not split them here, the process will throw java.lang.NumberFormatException
        .split("|").last()
}
