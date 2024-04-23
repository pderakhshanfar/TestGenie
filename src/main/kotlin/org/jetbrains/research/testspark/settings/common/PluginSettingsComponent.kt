package org.jetbrains.research.testspark.settings.common

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jdesktop.swingx.JXTitledSeparator
import org.jetbrains.research.testspark.bundles.LabelsBundle
import org.jetbrains.research.testspark.bundles.SettingsBundle
import java.awt.Color
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * This class displays and captures changes to the values of the Settings entries.
 */
class PluginSettingsComponent {
    var panel: JPanel? = null

    // BuildPath options
    private var buildPathTextField = JTextField()

    // BuildCommand options
    private var buildCommandTextField = JTextField()

    // Show coverage options
    private val showCoverageCheckbox: JCheckBox = JCheckBox(LabelsBundle.defaultValue("showCoverage"))

    // Accessibility options
    private val accessibilitySeparator = JXTitledSeparator(LabelsBundle.defaultValue("accessibility"))
    private var colorPicker = JColorChooser()

    init {
        stylizePanel()

        createSettingsPanel()
    }

    /**
     * Create the main panel for Plugin settings page
     */
    private fun createSettingsPanel() {
        panel = FormBuilder.createFormBuilder()
            .addComponent(JXTitledSeparator(LabelsBundle.defaultValue("showCoverageDescription")), 15)
            .addComponent(showCoverageCheckbox, 10)
            .addComponent(JXTitledSeparator(LabelsBundle.defaultValue("environmentSettings")), 15)
            // Add buildPath option
            .addLabeledComponent(
                JBLabel(LabelsBundle.defaultValue("buildPath")),
                buildPathTextField,
                10,
                false,
            )
            // Add buildPath option
            .addLabeledComponent(
                JBLabel(LabelsBundle.defaultValue("buildCommand")),
                buildCommandTextField,
                10,
                false,
            )
            .addComponent(accessibilitySeparator, 15)
            .addComponent(JBLabel(LabelsBundle.defaultValue("colorPicker")), 15)
            .addComponent(colorPicker, 10)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    /**
     * Add stylistic additions to elements of Plugin settings panel (e.g. tooltips)
     * IMPORTANT: this is responsible for wrapping the text of a label. It must be created before createSettingsPanel()
     */
    private fun stylizePanel() {
        // Add description to telemetry path show coverage checkbox
        showCoverageCheckbox.toolTipText = SettingsBundle.defaultValue("showCoverage")

        // Add description to build Path
        buildPathTextField.toolTipText = SettingsBundle.defaultValue("buildPath")

        // Add description to build Command
        buildCommandTextField.toolTipText = SettingsBundle.defaultValue("buildCommand")

        // Get dimensions of visible rectangle
        val width = panel?.visibleRect?.width
        val height = panel?.visibleRect?.height

        // Simplify colorPicker
        colorPicker.removeChooserPanel(colorPicker.chooserPanels.component1())
        colorPicker.removeChooserPanel(colorPicker.chooserPanels.component2())
        colorPicker.removeChooserPanel(colorPicker.chooserPanels.component2())
        colorPicker.removeChooserPanel(colorPicker.chooserPanels.component2())
        colorPicker.chooserPanels.component1().isColorTransparencySelectionEnabled = false

        // Set colorPicker to wrap around dimensions
        colorPicker.preferredSize = Dimension(width ?: 100, height ?: 400)
    }

    var showCoverageCheckboxSelected: Boolean
        get() = showCoverageCheckbox.isSelected
        set(newStatus) {
            showCoverageCheckbox.isSelected = newStatus
        }

    var buildPath: String
        get() = buildPathTextField.text
        set(newConfig) {
            buildPathTextField.text = newConfig
        }

    var buildCommand: String
        get() = buildCommandTextField.text
        set(newConfig) {
            buildCommandTextField.text = newConfig
        }

    var colorRed: Int
        get() = colorPicker.color.red
        set(newStatus) {
            colorPicker.color = JBColor(
                SettingsBundle.defaultValue("colorName"),
                Color(newStatus, colorPicker.color.green, colorPicker.color.blue),
            )
        }

    var colorGreen: Int
        get() = colorPicker.color.green
        set(newStatus) {
            colorPicker.color = JBColor(
                SettingsBundle.defaultValue("colorName"),
                Color(colorPicker.color.red, newStatus, colorPicker.color.blue),
            )
        }
    var colorBlue: Int
        get() = colorPicker.color.blue
        set(newStatus) {
            colorPicker.color = JBColor(
                SettingsBundle.defaultValue("colorName"),
                Color(colorPicker.color.red, colorPicker.color.green, newStatus),
            )
        }
}