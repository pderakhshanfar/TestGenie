package org.jetbrains.research.testspark.tools

/**
 * Enum class representing strategy for test generation
 */
enum class GenerationTool(val toolId: String) {
    EvoSuite("EvoSuite"),
    LLM("LLM"),
    Kex("Kex"),
    ;

    companion object {
        fun from(findValue: String): GenerationTool = entries.first { it.toolId == findValue }
    }
}
