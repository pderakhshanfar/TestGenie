package org.jetbrains.research.testspark.core.test.java

import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.test.TestSuiteParser
import org.jetbrains.research.testspark.core.test.data.TestSuiteGeneratedByLLM
import org.jetbrains.research.testspark.core.test.strategies.TestSuiteParserStrategy

class JavaJUnitTestSuiteParser(
    private val packageName: String,
    private val junitVersion: JUnitVersion,
    private val importPattern: Regex,
    private val parsingStrategy: TestSuiteParserStrategy,
) : TestSuiteParser {
    override fun parseTestSuite(rawText: String): TestSuiteGeneratedByLLM? {
        return parsingStrategy.parseTestSuite(
            rawText,
            junitVersion,
            importPattern,
            packageName,
            testNamePattern = "void",
            JavaPrintTestBodyStrategy(),
        )
    }
}
