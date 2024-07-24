package org.jetbrains.research.testspark.settings.kex

import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.services.KexSettingsService
import org.jetbrains.research.testspark.settings.template.SettingsConfigurable
import javax.swing.JComponent

class KexSettingsConfigurable(private val project: Project) : SettingsConfigurable {
        private val kexSettingsState: KexSettingsState
        get() = project.getService(KexSettingsService::class.java).state

    var settingsComponent: KexSettingsComponent? = null

    /**
     * Creates a settings component that holds the panel with the settings entries, and returns this panel
     *
     * @return the panel used for displaying settings
     */
    override fun createComponent(): JComponent? {
        settingsComponent = KexSettingsComponent()
        return settingsComponent!!.panel
    }

    /**
     * Sets the stored state values to the corresponding UI components. This method is called immediately after `createComponent` method.
     */
    override fun reset() {
        settingsComponent!!.kexPath = kexSettingsState.kexHome
        settingsComponent!!.kexMode = kexSettingsState.kexMode
        settingsComponent!!.option = kexSettingsState.option
    }

    /**
     * Checks if the values of the entries in the settings state are different from the persisted values of these entries.
     *
     * @return whether any setting has been modified
     */
    override fun isModified(): Boolean {
        return settingsComponent!!.kexPath != kexSettingsState.kexHome
                || settingsComponent!!.kexMode != kexSettingsState.kexMode
                || settingsComponent!!.option != kexSettingsState.option
    }

    /**
     * Persists the modified state after a user hit Apply button.
     */
    override fun apply() {
        kexSettingsState.kexHome = settingsComponent!!.kexPath
        kexSettingsState.kexMode = settingsComponent!!.kexMode
        kexSettingsState.option = settingsComponent!!.option
    }

    /**
     * Returns the displayed name of the Settings tab.
     *
     * @return the name displayed in the menu (settings)
     */
    override fun getDisplayName(): String {
        return "TestSpark"
    }

    /**
     * Disposes the UI resources. It is called when a user closes the Settings dialog.
     */
    override fun disposeUIResources() {
        settingsComponent = null
    }
}