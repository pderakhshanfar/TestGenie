package org.jetbrains.research.testspark.bundles.kex

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Loads the `resources` directory.
 */
object KexSettingsBundle : DynamicBundle(KexBundlePaths.settings) {

    /**
     * Gets the requested default value.
     */
    @Nls
    fun get(@PropertyKey(resourceBundle = KexBundlePaths.settings) key: String): String = getMessage(key)
}
