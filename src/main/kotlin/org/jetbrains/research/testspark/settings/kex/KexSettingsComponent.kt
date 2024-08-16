package org.jetbrains.research.testspark.settings.kex

import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jdesktop.swingx.JXTitledSeparator
import org.jetbrains.research.testspark.bundles.kex.KexLabelsBundle
import org.jetbrains.research.testspark.bundles.kex.KexSettingsBundle
import org.jetbrains.research.testspark.data.kex.KexMode
import org.jetbrains.research.testspark.services.KexSettingsService
import org.jetbrains.research.testspark.settings.template.SettingsComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * This class displays and captures changes to the values of the Settings entries.
 */
class KexSettingsComponent(private val project: Project) : SettingsComponent {
    var panel: JPanel? = null
    private val kexSettingsState: KexSettingsState
        get() = project.getService(KexSettingsService::class.java).state

    private var kexPathTextField = JTextField()
    private var kexModeSelector = ComboBox(KexMode.entries.toTypedArray())
    private var optionTextField = JTextField() // TODO comments for these

    // maximum test cases returned by kex when minimization is enabled (enabled by default)
    private var maxTestsField = JBIntSpinner(UINumericRange(kexSettingsState.maxTests, 1, Integer.MAX_VALUE))
    private var timeLimitField = JBIntSpinner(UINumericRange(kexSettingsState.timeLimit.inWholeSeconds.toInt(), 1, Integer.MAX_VALUE))

    var kexPath: String
        get() = kexPathTextField.text
        set(x) {
            kexPathTextField.text = x
        }

    var kexMode: KexMode
        get() = kexModeSelector.item
        set(x) {
            kexModeSelector.item = x
        }

    var option: String
        get() = optionTextField.text
        set(x) {
            optionTextField.text = x
        }

    var maxTests: Int
        get() = maxTestsField.number
        set(x) {
            maxTestsField.number = x
        }

    var timeLimit: Int
        get() = timeLimitField.number
        set(x) {
            timeLimitField.number = x
        }

    init {
        super.initComponent()
    }

    override fun stylizePanel() {
        kexPathTextField.toolTipText = KexSettingsBundle.get("kexHome")
    }
    override fun createSettingsPanel() {
        panel = FormBuilder.createFormBuilder()
            .addComponent(JXTitledSeparator(KexLabelsBundle.get("kexSettings")))
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("kexHome")), kexPathTextField, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("kexMode")), kexModeSelector, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("maxTests")), maxTestsField, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("timeLimit")), timeLimitField, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("option")), optionTextField, 10, false)
            .panel
    }

    override fun addListeners() {}
}
