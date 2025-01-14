package org.jetbrains.research.testspark.toolwindow

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.settings.evosuite.EvoSuiteSettingsConfigurable
import org.jetbrains.research.testspark.settings.kex.KexSettingsConfigurable
import org.jetbrains.research.testspark.settings.llm.LLMSettingsConfigurable
import org.jetbrains.research.testspark.settings.plugin.PluginSettingsConfigurable
import java.awt.Desktop
import java.awt.Font
import java.net.URI
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent

/**
 * This class stores the main panel and the UI of the "Parameters" tool window tab.
 */
class DescriptionTab(private val project: Project) {
    private val panelTitle = JPanel()
    private val iconTitle = JLabel(TestSparkIcons.pluginIcon)
    private val textTitle = JLabel(PluginLabelsBundle.get("quickAccess"))

    private val testSparkDescription = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        addHyperlinkListener { evt ->
            if (HyperlinkEvent.EventType.ACTIVATED == evt.eventType) {
                Desktop.getDesktop().browse(evt.url.toURI())
            }
        }
    }

    private val testSparkLLMDescription = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        addHyperlinkListener { evt ->
            if (HyperlinkEvent.EventType.ACTIVATED == evt.eventType) {
                Desktop.getDesktop().browse(evt.url.toURI())
            }
        }
    }

    private val testSparkEvoSuiteDescription = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        addHyperlinkListener { evt ->
            if (HyperlinkEvent.EventType.ACTIVATED == evt.eventType) {
                Desktop.getDesktop().browse(evt.url.toURI())
            }
        }
    }

    private val testSparkKexDescription = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        addHyperlinkListener { evt ->
            if (HyperlinkEvent.EventType.ACTIVATED == evt.eventType) {
                Desktop.getDesktop().browse(evt.url.toURI())
            }
        }
    }

    private val testSparkDisclaimerDescription = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        addHyperlinkListener { evt ->
            if (HyperlinkEvent.EventType.ACTIVATED == evt.eventType) {
                Desktop.getDesktop().browse(evt.url.toURI())
            }
        }
    }

    // Link to LLM settings
    private val llmSettingsButton = JButton(PluginLabelsBundle.get("llmSettingsLink"), TestSparkIcons.settings)

    // Link to EvoSuite settings
    private val evoSuiteSettingsButton = JButton(PluginLabelsBundle.get("evoSuiteSettingsLink"), TestSparkIcons.settings)

    // Link to Kex settings
    private val kexSettingsButton = JButton(PluginLabelsBundle.get("kexSettingsLink"), TestSparkIcons.settings)

    // Link to open settings
    private val settingsButton = JButton(PluginLabelsBundle.get("settingsLink"), TestSparkIcons.settings)

    // Link to documentation
    private val documentationButton = JButton(PluginLabelsBundle.get("documentationLink"), TestSparkIcons.documentation)

    // Tool Window panel
    private var toolWindowPanel: JPanel = JPanel()

    init {
        textTitle.font = Font("Monochrome", Font.BOLD, 20)

        panelTitle.setLayout(BoxLayout(panelTitle, BoxLayout.X_AXIS))
        panelTitle.add(iconTitle)
        panelTitle.add(textTitle)

        // Create the main panel and set the font of the title
        toolWindowPanel = createToolWindowPanel()

        // llm settings button setup
        llmSettingsButton.isOpaque = false
        llmSettingsButton.isContentAreaFilled = false
        llmSettingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LLMSettingsConfigurable::class.java)
        }

        // EvoSuite settings button setup
        evoSuiteSettingsButton.isOpaque = false
        evoSuiteSettingsButton.isContentAreaFilled = false
        evoSuiteSettingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, EvoSuiteSettingsConfigurable::class.java)
        }

        // Kex settings button setup
        kexSettingsButton.isOpaque = false
        kexSettingsButton.isContentAreaFilled = false
        kexSettingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, KexSettingsConfigurable::class.java)
        }

        // Settings button setup
        settingsButton.isOpaque = false
        settingsButton.isContentAreaFilled = false
        settingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginSettingsConfigurable::class.java)
        }

        // Documentation button setup
        documentationButton.isOpaque = false
        documentationButton.isContentAreaFilled = false
        documentationButton.addActionListener {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("https://github.com/JetBrains-Research/TestSpark"))
            }
        }

        // testSpark description setup
        testSparkDescription.isOpaque = false
        testSparkDescription.text = getCommonDescriptionText(getContent().preferredSize.width)

        // testSpark LLM description setup
        testSparkLLMDescription.isOpaque = false
        testSparkLLMDescription.text = getLLMDescriptionText(getContent().preferredSize.width)

        // testSpark EvoSuite description setup
        testSparkEvoSuiteDescription.isOpaque = false
        testSparkEvoSuiteDescription.text = getEvoSuiteDescriptionText(getContent().preferredSize.width)

        // testSpark Kex description setup
        testSparkKexDescription.isOpaque = false
        testSparkKexDescription.text = getKexDescriptionText(getContent().preferredSize.width)

        // testSpark disclaimer description setup
        testSparkDisclaimerDescription.isOpaque = false
        testSparkDisclaimerDescription.text = getDisclaimerText(getContent().preferredSize.width)
    }

    /**
     * Creates the entire tool window panel.
     */
    private fun createToolWindowPanel() = FormBuilder.createFormBuilder()
        // Add indentations from the left border and between the lines, and add title
        .setFormLeftIndent(30)
        .addVerticalGap(25)
        .addComponent(panelTitle)
        .addComponent(testSparkDescription, 10)
        .addComponent(testSparkLLMDescription, 10)
        .addComponent(llmSettingsButton, 10)
        .addComponent(testSparkEvoSuiteDescription, 20)
        .addComponent(evoSuiteSettingsButton, 10)
        .addComponent(testSparkKexDescription, 20)
        .addComponent(kexSettingsButton, 10)
        .addComponent(testSparkDisclaimerDescription, 20)
        .addComponent(settingsButton, 10)
        .addComponent(documentationButton, 10)
        // Add the main panel
        .addComponentFillVertically(JPanel(), 20)
        .panel

    /**
     * Gets the panel that is the main wrapper component of the tool window.
     * The panel is put into a scroll pane so that all the parameters can fit.
     *
     * @return the created tool window pane wrapped into a scroll pane
     */
    fun getContent(): JComponent {
        return JBScrollPane(toolWindowPanel)
    }

    /**
     * Returns the common description text for TestSpark plugin.
     *
     * @param width The width used to set the style of the HTML body.
     * @return The common description text formatted as HTML.
     */
    private fun getCommonDescriptionText(width: Int): String {
        return "<html><body style='width: ${(1.25 * width).toInt()} px;'><font face=Monochrome>" +
            "Welcome and thank you for using TestSpark!<br>" +
            "This plugin let you to generate tests for Java classes, method, and single lines.<br>" +
            "TestSpark is currently developed and maintained by <a href=https://lp.jetbrains.com/research/ictl/>ICTL at JetBrains Research</a>.<br>" +
            "We are currently supporting two types of test generation:<br><br></font></body></html>"
    }

    /**
     * Returns the description text for LLM-based test generation.
     *
     * @param width The width of the text container.
     * @return The formatted HTML description text for LLM-based test generation.
     */
    private fun getLLMDescriptionText(width: Int): String {
        return "<html><body style='width: ${(0.65 * width).toInt()} px;'><font face=Monochrome>" +
            "<strong>LLM-based test generation</strong><br><br>" +
            "Needs <a href=https://openai.com>OpenAI</a> or JetBrains AI Assistant platform (currently, accessible to JetBrains employees) tokens. To use this test generation, you need to enter your token and select your model in the settings.</font></body></html>"
    }

    /**
     * Returns the descriptive text for EvoSuite test generation.
     *
     * @param width The width of the text body in pixels.
     * @return The formatted HTML string containing the description of Search-based test generation using EvoSuite.
     */
    private fun getEvoSuiteDescriptionText(width: Int): String {
        return "<html><body style='width: ${(0.65 * width).toInt()} px;'><font face=Monochrome>" +
            "<strong>Search-based test generation</strong><br><br>" +
            "Uses <a href=https://www.evosuite.org>EvoSuite</a>. You can generate tests with this tool locally.<br>" +
            "However, it only supports projects implemented by Java versions 8 to 11.</font></body></html>"
    }

    /**
     * Returns the descriptive text for Kex test generation.
     *
     * @param width The width of the text body in pixels.
     * @return The formatted HTML string containing the description of test generation through symbolic execution using Kex.
     */
    private fun getKexDescriptionText(width: Int): String {
        return "<html><body style='width: ${(0.65 * width).toInt()} px;'><font face=Monochrome>" +
            "<strong>Symbolic Execution-based test generation</strong><br><br>" +
            "Uses <a href=https://github.com/vorpal-research/kex>Kex</a>. You can generate tests with this tool locally.<br>" +
            "However, it only supports projects implemented by Java versions 8 and upwards.</font></body></html>"
    }

    /**
     * Returns the disclaimer text based on the given width.
     *
     * @param width The width in pixels to be used for styling the disclaimer text.
     * @return The disclaimer text as an HTML string.
     */
    private fun getDisclaimerText(width: Int): String {
        return "<html><body style='width: ${(0.65 * width).toInt()} px;'><font face=Monochrome>" +
            "<strong>DISCLAIMER</strong><br><br>" +
            "TestSpark is currently designed to serve as an experimental tool.<br>" +
            "Please keep in mind that tests generated by TestSpark are meant to augment your existing test suites. " +
            "They are not meant to replace writing tests manually.</font></body></html>"
    }
}
