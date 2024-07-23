package org.jetbrains.research.testspark.settings.kex

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jdesktop.swingx.JXTitledSeparator
import org.jetbrains.research.testspark.bundles.kex.KexLabelsBundle
import org.jetbrains.research.testspark.bundles.kex.KexSettingsBundle
import org.jetbrains.research.testspark.data.kex.KexMode
import org.jetbrains.research.testspark.settings.template.SettingsComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * This class displays and captures changes to the values of the Settings entries.
 */
class KexSettingsComponent  : SettingsComponent {
    var panel: JPanel? = null

    private var kexPathTextField = JTextField()
    private var kexModeSelector = ComboBox(KexMode.entries.toTypedArray())
    private var optionTextField = JTextField() //TODO comments for these
    private var crashDepthTextField = JTextField()
    private var crashTraceTextField = JTextField()
    private var libraryTargetTextField = JTextField()

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

    var crashDepth: String
        get() = crashDepthTextField.text
        set(x) {
            crashDepthTextField.text = x
        }
    var crashTrace: String
        get() = crashTraceTextField.text
        set(x) {
            crashTraceTextField.text = x
        }
    var libraryTarget: String
        get() = libraryTargetTextField.text
        set(x) {
            libraryTargetTextField.text = x
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
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("option")), optionTextField, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("crashDepth")), crashDepthTextField, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("crashTrace")), crashTraceTextField, 10, false)
            .addLabeledComponent(JBLabel(KexLabelsBundle.get("libraryTarget")), libraryTargetTextField, 10, false)
            .panel
    }

    override fun addListeners() {}
}