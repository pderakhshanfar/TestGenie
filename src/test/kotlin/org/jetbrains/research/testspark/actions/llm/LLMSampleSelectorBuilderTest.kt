package org.jetbrains.research.testspark.actions.llm

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.*
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LLMSampleSelectorBuilderTest {

    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var openFile: PsiFile
    private lateinit var project: Project
    private lateinit var builder: LLMSampleSelectorBuilder

    @BeforeEach
    fun setUp() {
        val projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture> =
            IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("project")

        fixture = JavaTestFixtureFactory.getFixtureFactory()
            .createCodeInsightFixture(projectBuilder.fixture)
        fixture.setUp()
        fixture.addFileToProject("pom.xml", providePomXML())
        fixture.addFileToProject("src/dummy/Car.java", provideCarClass())
        openFile = fixture.addFileToProject("test/dummy/CarTest.java", provideCarTest())
        fixture.addFileToProject("src/dummy/Person.java", providePersonClass())
        fixture.addFileToProject("test/dummy/PersonTest.java", providePersonTest())
        project = fixture.project
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        builder = LLMSampleSelectorBuilder(project, SupportedLanguage.Java)
    }

    @Test
    fun collectTestSampleForCurrentFile() {
        runReadAction { builder.collectTestSamplesForCurrentFile(openFile.virtualFile) }
    }

    @Test
    fun collectTestSamples() {
        runReadAction { builder.collectTestSamples(project) }
    }

    private fun providePomXML(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>test-project</artifactId>
              <version>0.0.1-SNAPSHOT</version>
              <properties>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                  <version>5.9.2</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent()
    }

    private fun provideCarClass(): String {
        return """
            package dummy;
            public class Car {
              public String getName() {
                return "car";
              }
            }
        """.trimIndent()
    }

    private fun providePersonClass(): String {
        return """
            package dummy;
            public class Person {}
        """.trimIndent()
    }

    private fun provideCarTest(): String {
        return """
            package dummy;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class CarTest {
              @Test
              void testCar() {
                Car car = new Car();
                assertEquals("car", car.getName());
              }
            }
        """.trimIndent()
    }

    private fun providePersonTest(): String {
        return """
            package dummy;
            import org.junit.jupiter.api.Test;
            public class PersonTest {
              @Test
              void testPerson() {}
              @Test
              void testPerson2() {}
            }
        """.trimIndent()
    }
}