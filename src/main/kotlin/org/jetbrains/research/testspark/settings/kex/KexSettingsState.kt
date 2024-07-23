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
    var option: String = DefaultKexSettingsState.option,
    var crashDepth: String = DefaultKexSettingsState.crashDepth,
    var crashTrace: String = DefaultKexSettingsState.crashTrace,
    var libraryTarget: String = DefaultKexSettingsState.libraryTarget,
) {

    /**
     * Default values of SettingsEvoSuiteState.
     */
    object DefaultKexSettingsState {
        val kexVersion: String = KexDefaultsBundle.get("kexVersion")
        var kexHome: String = KexDefaultsBundle.get("kexHome")
        var kexMode: KexMode = KexMode.Concolic
        var option: String = KexDefaultsBundle.get("option")
        var crashDepth: String = KexDefaultsBundle.get("crashDepth")
        var crashTrace: String = KexDefaultsBundle.get("crashTrace")
        var libraryTarget: String = KexDefaultsBundle.get("libraryTarget")
    }
}
