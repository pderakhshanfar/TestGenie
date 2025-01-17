package org.jetbrains.research.testspark.tools.llm.generation.gemini

data class GeminiRequest(
    val contents: List<GeminiRequestBody>,
)

data class GeminiRequestBody(
    val parts: List<GeminiChatMessage>,
)

data class GeminiChatMessage(
    val text: String,
)

data class GeminiReply(
    val content: GeminiReplyContent,
    val finishReason: String,
    val avgLogprobs: Double,
)

data class GeminiReplyContent(
    val parts: List<GeminiReplyPart>,
    val role: String?,
)

data class GeminiReplyPart(
    val text: String,
)
