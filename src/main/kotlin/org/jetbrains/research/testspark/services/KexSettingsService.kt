package org.jetbrains.research.testspark.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.settings.kex.KexSettingsState

/**
 * This class is responsible for storing the application-level settings persistently. It uses SettingsApplicationState class for that.
 */
@Service(Service.Level.PROJECT)
@State(name = "KexSettingsState", storages = [Storage("KexSettings.xml")])
class KexSettingsService : PersistentStateComponent<KexSettingsState> {
    private var kexSettingsState: KexSettingsState = KexSettingsState()
    override fun getState(): KexSettingsState {
        return kexSettingsState
    }

    override fun loadState(state: KexSettingsState) {
        kexSettingsState = state
    }

    /**
     * Returns the service object with a static call.
     */

    companion object {
        fun service(project: Project) = project.getService(KexSettingsService::class.java).state
    }
}