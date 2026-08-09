package com.kyovo.infrastructure.llm

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.SummarizedArticle
import com.kyovo.domain.port.output.SummarizerPort
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClaudeAdapter(
    private val httpClient: HttpClient,
    private val apiKey: String
) : SummarizerPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun summarize(articles: List<Article>): List<SummarizedArticle> {
        val userContent = buildUserContent(articles)
        val rawText = callApi(userContent)
        return parseAndJoin(rawText, articles)
    }

    private suspend fun callApi(userContent: String): String {
        val requestBody = json.encodeToString(
            ClaudeRequest(
                model     = MODEL,
                maxTokens = 4096,
                system    = SYSTEM_PROMPT,
                messages  = listOf(ClaudeMessage(role = "user", content = userContent))
            )
        )
        val responseText = httpClient.post(API_URL) {
            header("x-api-key", apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.bodyAsText()

        // Extract the text from the first text-type content block.
        // Using JsonElement avoids a default parameter on a wire type: the `text`
        // field is absent on non-text blocks (e.g. tool_use), which kotlinx.serialization
        // cannot model without a default value.
        return json.parseToJsonElement(responseText)
            .jsonObject["content"]!!
            .jsonArray
            .first { it.jsonObject["type"]!!.jsonPrimitive.content == "text" }
            .jsonObject["text"]!!
            .jsonPrimitive.content
    }

    private fun buildUserContent(articles: List<Article>): String {
        val block = articles.joinToString("\n---\n") { a ->
            """
            [${a.theme.name}] ${a.source}
            Title  : ${a.title}
            Link   : ${a.link}
            Summary: ${a.summary.take(300)}
            """.trimIndent()
        }
        return USER_PROMPT_TEMPLATE.replace("{ARTICLES}", block)
    }

    // Handles both raw JSON and JSON wrapped in markdown code fences.
    private fun extractJson(text: String): String =
        Regex("```(?:json)?\\s*([\\s\\S]+?)\\s*```").find(text)
            ?.groupValues?.get(1)?.trim()
            ?: text.trim()

    private fun parseAndJoin(rawText: String, source: List<Article>): List<SummarizedArticle> =
        try {
            val responses = json.decodeFromString<List<ArticleSummaryResponse>>(extractJson(rawText))
            val byLink = source.associateBy { it.link }
            responses.mapNotNull { r ->
                byLink[r.link]?.let { article ->
                    SummarizedArticle(
                        article        = article,
                        summary        = r.summary,
                        relevanceScore = r.relevanceScore.coerceIn(1, 10),
                        keyPoints      = r.keyPoints
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println("[ClaudeAdapter] Failed to parse LLM response: ${e.message}")
            emptyList()
        }

    companion object {
        private const val API_URL           = "https://api.anthropic.com/v1/messages"
        private const val MODEL             = "claude-opus-4-7"
        private const val ANTHROPIC_VERSION = "2023-06-01"

        private val SYSTEM_PROMPT = """
            You are a technology news curator. Your role is to select the most relevant
            and insightful articles from a daily feed for a senior developer passionate
            about Kotlin, AI, cloud, cybersecurity, and tech in general.

            Selection criteria (in decreasing priority):
            1. Immediate practical value (new features, releases, experience reports)
            2. Strong signal on an emerging trend
            3. In-depth analysis or original viewpoint
            4. Skip: marketing press releases, overly generic articles, thematic duplicates

            Respond ONLY with a valid JSON array — no text before or after.
        """.trimIndent()

        private val USER_PROMPT_TEMPLATE = """
            Here are today's articles, grouped by theme.
            Select at most 3 articles per theme (18 total maximum).

            For each selected article, return:
            - "link": the exact article URL (copy it verbatim, do not modify)
            - "summary": a 2-3 sentence summary in English, practitioner-oriented
            - "relevanceScore": integer from 1 to 10
            - "keyPoints": list of 2 to 3 key points in English, as short infinitive phrases

            Expected response format:
            [
              {
                "link": "https://...",
                "summary": "...",
                "relevanceScore": 8,
                "keyPoints": ["Understand X", "Avoid Y", "Adopt Z"]
              }
            ]

            Articles:
            {ARTICLES}
        """.trimIndent()
    }
}

// ── Claude API wire types ─────────────────────────────────────────────────────

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

// ── LLM response shape ────────────────────────────────────────────────────────

@Serializable
private data class ArticleSummaryResponse(
    val link: String,
    val summary: String,
    val relevanceScore: Int,
    val keyPoints: List<String>
)
