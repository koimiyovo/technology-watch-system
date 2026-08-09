package com.kyovo.application

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.SummarizedArticle
import com.kyovo.domain.model.Theme
import com.kyovo.domain.port.output.ArticleFeedPort
import com.kyovo.domain.port.output.NotifierPort
import com.kyovo.domain.port.output.SummarizerPort
import com.kyovo.domain.service.ArticleCurator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyDigestServiceTest {

    private val now   = Instant.parse("2026-08-09T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val feedPort      = mockk<ArticleFeedPort>()
    private val summarizerPort = mockk<SummarizerPort>()
    private val notifierPort  = mockk<NotifierPort>()

    private val service = DailyDigestService(
        feedPort       = feedPort,
        curator        = ArticleCurator(clock),
        summarizerPort = summarizerPort,
        notifierPort   = notifierPort
    )

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun article(theme: Theme, link: String = "https://example.com/${theme.name}") = Article(
        title       = "Article – $theme",
        link        = link,
        summary     = "Summary",
        publishedAt = now.minus(Duration.ofHours(1)),
        theme       = theme,
        source      = "Test Feed"
    )

    private fun summarized(article: Article) = SummarizedArticle(
        article        = article,
        summary        = "LLM summary",
        relevanceScore = 9,
        keyPoints      = listOf("Key point")
    )

    private fun stubFeedEmpty() =
        Theme.entries.forEach { coEvery { feedPort.fetchArticles(it) } returns emptyList() }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `fetches articles for every theme`() = runTest {
        stubFeedEmpty()

        service.execute()

        Theme.entries.forEach { theme ->
            coVerify(exactly = 1) { feedPort.fetchArticles(theme) }
        }
    }

    @Test
    fun `does not call summarizer when curation yields no articles`() = runTest {
        // All articles are outside the 24h window
        val tooOld = article(Theme.KOTLIN).copy(publishedAt = now.minus(Duration.ofHours(25)))
        Theme.entries.forEach { coEvery { feedPort.fetchArticles(it) } returns listOf(tooOld) }

        service.execute()

        coVerify(exactly = 0) { summarizerPort.summarize(any()) }
    }

    @Test
    fun `does not call notifier when summarizer returns empty list`() = runTest {
        Theme.entries.forEach { coEvery { feedPort.fetchArticles(it) } returns listOf(article(it)) }
        coEvery { summarizerPort.summarize(any()) } returns emptyList()

        service.execute()

        coVerify(exactly = 0) { notifierPort.sendDigest(any()) }
    }

    @Test
    fun `sends digest when full pipeline produces results`() = runTest {
        val kotlinArticle = article(Theme.KOTLIN)
        Theme.entries.forEach { theme ->
            coEvery { feedPort.fetchArticles(theme) } returns
                if (theme == Theme.KOTLIN) listOf(kotlinArticle) else emptyList()
        }
        val expectedDigest = listOf(summarized(kotlinArticle))
        coEvery { summarizerPort.summarize(any()) } returns expectedDigest
        coEvery { notifierPort.sendDigest(expectedDigest) } returns Unit

        service.execute()

        coVerify(exactly = 1) { notifierPort.sendDigest(expectedDigest) }
    }

    @Test
    fun `deduplicates articles with same link coming from different theme feeds`() = runTest {
        val sharedLink = "https://shared.com/article"
        Theme.entries.forEach { theme ->
            coEvery { feedPort.fetchArticles(theme) } returns listOf(article(theme, link = sharedLink))
        }
        val capturedArticles = slot<List<Article>>()
        coEvery { summarizerPort.summarize(capture(capturedArticles)) } returns emptyList()

        service.execute()

        assertEquals(1, capturedArticles.captured.size)
        assertEquals(sharedLink, capturedArticles.captured.single().link)
    }
}
