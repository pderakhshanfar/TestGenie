package org.jetbrains.research.testspark.settings.kex

import org.jetbrains.research.testspark.bundles.kex.KexDefaultsBundle
import org.jetbrains.research.testspark.data.kex.KexMode

/**
 * This class is the actual data class that stores the values of the Kex Settings entries.
 */
data class KexSettingsState(
    var kexVersion: String = DefaultKexSettingsState.kexVersion,
    var kexHome: String = DefaultKexSettingsState.kexHome,
    var kexMode: KexMode = DefaultKexSettingsState.kexMode,
    var otherOptions: String = DefaultKexSettingsState.otherOptions,
    var timeLimit: Int = DefaultKexSettingsState.timeLimit, // seconds
    var maxTests: Int = DefaultKexSettingsState.maxTests,
) {

    /**
     * Default values of SettingsKexState.
     */
    object DefaultKexSettingsState {
        val kexVersion: String = KexDefaultsBundle.get("kexVersion")
        val kexHome: String = KexDefaultsBundle.get("kexHome")
        val kexMode: KexMode = KexMode.Concolic
        val otherOptions: String = KexDefaultsBundle.get("otherOptions")
        val timeLimit: Int = KexDefaultsBundle.get("timeLimit").toInt() // seconds
        val maxTests = KexDefaultsBundle.get("maxTests").toInt()
    }
}
