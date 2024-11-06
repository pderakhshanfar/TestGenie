import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

fun properties(key: String) = project.findProperty(key).toString()

// Space credentials
val spaceUsername =
    System.getProperty("space.username")?.toString() ?: project.properties["spaceUsername"]?.toString() ?: ""
val spacePassword =
    System.getProperty("space.pass")?.toString() ?: project.properties["spacePassword"]?.toString() ?: ""

// the test generation module for interacting with Grazie (used when the space credentials are provided)
val grazieTestGenerationVersion = "1.0.5"

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij.platform") version "2.1.0"
    // Gradle IntelliJ Plugin Migration Help (uncomment it for migration tips)
//    id("org.jetbrains.intellij.platform.migration") version "2.1.0"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.1.2"
    // Gradle Qodana Plugin
//    id("org.jetbrains.qodana") version "0.1.13"
}
group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
    // this part is mandatory for all modules for platform version 2:
    // See https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html#default-repositories
    intellijPlatform {
        defaultRepositories()
    }
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven("https://www.jetbrains.com/intellij-repository/snapshots")

    maven {
        url = uri("https://packages.jetbrains.team/maven/p/automatically-generating-unit-tests/maven")
        credentials {
            username = spaceUsername
            password = spacePassword
        }
    }

    if (spaceCredentialsProvided()) {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
        }
    }
}

if (spaceCredentialsProvided()) {
    // Add the new source set
    val hasGrazieAccess = sourceSets.create("hasGrazieAccess")
    // add output of main source set to new source set class path
    hasGrazieAccess.compileClasspath += sourceSets.main.get().output

    // register feature variant
    java.registerFeature(hasGrazieAccess.name) {
        usingSourceSet(hasGrazieAccess)
    }

    // Add the dependencies for the new source set
    dependencies {
        add(hasGrazieAccess.implementationConfigurationName, kotlin("stdlib"))
        add(hasGrazieAccess.implementationConfigurationName, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        add(
            hasGrazieAccess.implementationConfigurationName,
            "org.jetbrains.research:grazie-test-generation:$grazieTestGenerationVersion"
        )
    }

    tasks.register("checkCredentials") {
        configurations.detachedConfiguration(
            dependencies.create("org.jetbrains.research:grazie-test-generation:$grazieTestGenerationVersion"),
        ).files()
    }

    tasks.named(hasGrazieAccess.jarTaskName).configure {
        dependsOn("checkCredentials")
    }

    tasks.named<Jar>(hasGrazieAccess.jarTaskName) {
        exclude("**/plugin.xml")
    }

    // add build of new source set as the part of UI testing
    tasks.prepareTestSandbox.configure {
        dependsOn(hasGrazieAccess.jarTaskName)
        from(tasks.getByName(hasGrazieAccess.jarTaskName).outputs.files.asPath) { into("TestSpark/lib") }

        hasGrazieAccess.runtimeClasspath
            .elements.get().forEach {
                from(it.asFile.absolutePath) { into("TestSpark/lib") }
            }
    }
    // add build of new source set as the part of pluginBuild process
    tasks.prepareSandbox.configure {
        dependsOn(hasGrazieAccess.jarTaskName)
        from(tasks.getByName(hasGrazieAccess.jarTaskName).outputs.files.asPath) { into("TestSpark/lib") }

        hasGrazieAccess.runtimeClasspath
            .elements.get().forEach {
                from(it.asFile.absolutePath) { into("TestSpark/lib") }
            }
    }
}

dependencies {
    // Check platform V2 documentation for more details: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // make a custom version of IDEA
        create(properties("platformType"), properties("platformVersion"))
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        bundledPlugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        pluginVerifier()
        zipSigner()
        instrumentationTools()

        testFramework(TestFrameworkType.Bundled)
    }

    implementation(files("lib/evosuite-${properties("evosuiteVersion")}.jar"))
    implementation(files("lib/standalone-runtime.jar"))
    implementation(files("lib/jacocoagent.jar"))
    implementation(files("lib/jacococli.jar"))
    implementation(files("lib/opentest4j-1.1.1.jar"))
    implementation(files("lib/mockito-core-5.0.0.jar"))
    implementation(files("lib/byte-buddy-1.14.6.jar"))
    implementation(files("lib/byte-buddy-agent-1.14.6.jar"))
    implementation(files("lib/JUnitRunner.jar"))

    implementation(project(":core"))
    implementation(project(":langwrappers")) // Needed to use Psi related interfaces and load proper implementation
    implementation(project(":kotlin")) // Needed to load the testspark-kotlin.xml
    implementation(project(":java")) // Needed to load the testspark-java.xml
    if (spaceCredentialsProvided()) {
        "hasGrazieAccessCompileOnly"(project(":core"))
    }

    // https://central.sonatype.com/artifact/io.github.oshai/kotlin-logging-jvm/overview
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")

    // validation dependencies
    // https://mvnrepository.com/artifact/junit/junit
    implementation("junit:junit:4.13")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    implementation("org.junit.platform:junit-platform-launcher:1.10.0")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    // https://mvnrepository.com/artifact/org.jacoco/org.jacoco.core
    implementation("org.jacoco:org.jacoco.core:0.8.12")
    // https://mvnrepository.com/artifact/com.github.javaparser/javaparser-core
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.24.2")

    // https://gitlab.com/mvysny/konsume-xml
    implementation("com.gitlab.mvysny.konsume-xml:konsume-xml:1.0")

    // From the jetbrains repository
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.13")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.13")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/logging-interceptor
    testImplementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-engine
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.22.0")

    // https://mvnrepository.com/artifact/com.automation-remarks/video-recorder-junit5
    implementation("com.automation-remarks:video-recorder-junit5:2.0")

    // https://mvnrepository.com/artifact/org.mockito/mockito-all
    testImplementation("org.mockito:mockito-all:1.10.19")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    // https://mvnrepository.com/artifact/net.jqwik/jqwik
    testImplementation("net.jqwik:jqwik:1.6.5")

    // https://mvnrepository.com/artifact/com.github.javaparser/javaparser-symbol-solver-core
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.24.2")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-test
    implementation("org.jetbrains.kotlin:kotlin-test:1.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

// Configure Gradle IntelliJ Plugin - read more: // Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
        channels = listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first())
    }
    // Set the ides on which the plugin verification is executed.
    // See https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides
    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = properties("pluginUntilBuild")
                untilBuild = properties("pluginUntilBuild")
            }
        }
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId,ForbiddenPluginIdPrefix"
        )
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
// qodana {
//    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
//    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
//    saveReport.set(true)
//    showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
// }

tasks {

    compileKotlin {
        dependsOn("updateEvosuite")
        dependsOn("copyJUnitRunnerLib")
        dependsOn(":core:compileKotlin")
    }

    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    test {
        useJUnitPlatform()
        if (System.getProperty("test.profile") != "ui") {
            exclude("**/*uiTest*")
        }
    }

    signPlugin {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    patchPluginXml {
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) },
        )
    }

    publishPlugin {
        dependsOn("patchChangelog")
    }
}

abstract class CopyJUnitRunnerLib : DefaultTask() {
    @TaskAction
    fun execute() {
        val libName = "JUnitRunner.jar"
        val libSrcDir = "JUnitRunner${File.separator}build${File.separator}libs${File.separator}"
        val libDestDir = "lib${File.separator}"

        val libSrcPath = Paths.get("$libSrcDir$libName")
        val libDestPath = Paths.get("$libDestDir$libName")

        // check if the jar file exists
        if (!libSrcPath.toFile().exists()) {
            throw IllegalStateException("$libSrcPath does not exist")
        }

        // move the lib
        Files.move(libSrcPath, libDestPath, StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Custom gradle task used to source the custom evosuite binary
 * required for the build process. It functions as follows:
 * 1. Read the version specified inside build.gradle
 * 2. If the specified jar version is present for the build process, the
 * task finishes successfully, otherwise:
 * 3. Attempt to fetch the corresponding release from the supplied
 * download url.
 * 4. Unzips the release and places the raw jar inside the directory used by the build process
 */
abstract class UpdateEvoSuite : DefaultTask() {
    @Input
    var evoSuiteVersion: String = ""

    @TaskAction
    fun execute() {
        val libDir = File("lib")
        if (!libDir.exists()) {
            libDir.mkdirs()
        }

        val jarName = "evosuite-$evoSuiteVersion.jar"

        if (libDir.listFiles()?.any { it.name.matches(Regex(jarName)) } == true) {
            logger.info("Specified evosuite jar found, skipping update")
            return
        }

        logger.info("Specified evosuite jar not found, downloading release $jarName")
        val downloadUrl =
            "https://github.com/ciselab/evosuite/releases/download/thunderdome/release/$evoSuiteVersion/release.zip"
        val stream =
            try {
                URL(downloadUrl).openStream()
            } catch (e: Exception) {
                logger.error("Error fetching latest evosuite custom release - $e")
                return
            }

        ZipInputStream(stream).use { zipInputStream ->
            while (zipInputStream.nextEntry != null) {
                val file = File("lib", jarName)
                val outputStream = FileOutputStream(file)
                outputStream.write(zipInputStream.readAllBytes())
                outputStream.close()
            }
        }

        logger.info("Latest evosuite jar successfully downloaded, cleaning up lib directory")
        libDir.listFiles()?.filter { !it.name.matches(Regex(jarName)) && it.name.contains("evosuite") }?.map {
            if (it.delete()) {
                logger.info("Deleted outdated release ${it.name}")
            }
        }
    }
}

tasks.register<UpdateEvoSuite>("updateEvosuite") {
    evoSuiteVersion = properties("evosuiteVersion")
}
/**
 * Copies the JUnitRunner.jar file to the lib directory of the project.
 * This task depends on the "JUnitRunner" module being built beforehand.
 * JUnitRunner.jar is required for running tests with coverage in the main plugin
 */
tasks.register<Copy>("copyJUnitRunnerLib") {
    dependsOn(":JUnitRunner:jar")
    val libName = "JUnitRunner.jar"
    val libSrcDir =
        "${project.projectDir}${File.separator}JUnitRunner${File.separator}build${File.separator}libs${File.separator}"
    val libDestDir = "${project.projectDir}${File.separator}lib${File.separator}"
    val libSrcPath = Paths.get("$libSrcDir$libName")

    from(libSrcPath)
    into(libDestDir)
}

/**
 * Returns the original string if it is not null, or the default string if the original string is null.
 *
 * @param default the default string to return if the original string is null
 * @return the original string if it is not null, or the default string if the original string is null
 */
fun String?.orDefault(default: String): String = this ?: default

/**
 * This code sets up a Gradle task for running the plugin in headless mode
 *
 * @param root The root directory of the project under test.
 * @param file The file containing unit under test.
 * @param cut The class under test.
 * @param cp The classpath of the project.
 * @param llm The model used for the test generation task.
 * @param token The token for using LLM.
 * @param prompt a txt file containing the LLM's prompt template
 * @param out The output directory for the project.
 * @param enableCoverage flag to enable/disable coverage computation
 * @param methodName indicates the name of the method under test or empty for class level generation
 */
tasks.create<RunIdeTask>("headless") {
    val root: String? by project
    val file: String? by project
    val cut: String? by project
    val cp: String? by project
    val junitv: String? by project
    val llm: String? by project
    val token: String? by project
    val prompt: String? by project
    val out: String? by project
    val enableCoverage: String? by project
    val methodName: String? by project

    args = listOfNotNull(
        "testspark",
        root,
        file,
        cut,
        cp,
        junitv,
        llm,
        token,
        prompt,
        out,
        enableCoverage.orDefault("false"),
        methodName.orDefault("")
    )

    jvmArgs(
        "-Xmx16G",
        "-Djava.awt.headless=true",
        "--add-exports",
        "java.base/jdk.internal.vm=ALL-UNNAMED",
        "-Didea.system.path",
    )

    splitMode = false
    splitModeTarget = SplitModeTarget.BACKEND
}

fun spaceCredentialsProvided() = spaceUsername.isNotEmpty() && spacePassword.isNotEmpty()
