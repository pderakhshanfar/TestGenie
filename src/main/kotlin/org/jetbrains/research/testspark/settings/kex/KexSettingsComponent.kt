package org.jetbrains.research.testspark.settings.kex

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.research.testspark.bundles.kex.KexDefaultsBundle
import org.jetbrains.research.testspark.bundles.kex.KexLabelsBundle
import org.jetbrains.research.testspark.bundles.kex.KexSettingsBundle
import org.jetbrains.research.testspark.settings.template.SettingsComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * This class displays and captures changes to the values of the Settings entries.
 */
class KexSettingsComponent  : SettingsComponent {
    var panel: JPanel? = null

    private var kexPathTextField = JTextField()
    override fun stylizePanel() {
        kexPathTextField.toolTipText = KexSettingsBundle.get("kexHome")
    }
    var kexPath: String
        get() = kexPathTextField.text
        set(newConfig) {
            kexPathTextField.text = newConfig
        }
    override fun createSettingsPanel() {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(KexDefaultsBundle.get("kexHome")), kexPathTextField, 10, false)
            .panel
    }

    override fun addListeners() {
        TODO("Not yet implemented")
    }
}