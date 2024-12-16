package org.jetbrains.research.testspark.tools.llm.generation.gemini

import org.jetbrains.research.testspark.tools.llm.generation.LLMPlatform

class GeminiPlatform(
    override val name: String = "Google AI",
    override var token: String = "",
    override var model: String = "",
) : LLMPlatform
