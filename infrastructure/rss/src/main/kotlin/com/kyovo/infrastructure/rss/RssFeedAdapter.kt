package com.kyovo.infrastructure.rss

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.Theme
import com.kyovo.domain.port.output.ArticleFeedPort
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.StringReader

class RssFeedAdapter(private val httpClient: HttpClient) : ArticleFeedPort {

    override suspend fun fetchArticles(theme: Theme): List<Article> = coroutineScope {
        feedUrlsByTheme.getValue(theme)
            .map { url -> async { fetchFeed(url, theme) } }
            .awaitAll()
            .flatten()
    }

    // A failure on one feed must not block the entire digest.
    private suspend fun fetchFeed(url: String, theme: Theme): List<Article> = try {
        val xml = httpClient.get(url).bodyAsText()
        val feed = SyndFeedInput().build(StringReader(xml))
        feed.entries.mapNotNull { it.toArticle(theme, feed.title ?: url) }
    } catch (e: Exception) {
        System.err.println("[RssFeedAdapter] Skipped feed $url: ${e.message}")
        emptyList()
    }

    private fun SyndEntry.toArticle(theme: Theme, sourceName: String): Article? {
        val articleLink = link?.takeIf { it.isNotBlank() } ?: return null
        val articleTitle = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val publishedAt = (publishedDate ?: updatedDate)?.toInstant() ?: return null
        val summary = description?.value?.stripHtml()
            ?: contents.firstOrNull()?.value?.stripHtml()
            ?: ""
        return Article(
            title       = articleTitle,
            link        = articleLink,
            summary     = summary,
            publishedAt = publishedAt,
            theme       = theme,
            source      = sourceName
        )
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    companion object {
        private val feedUrlsByTheme: Map<Theme, List<String>> = mapOf(
            Theme.KOTLIN to listOf(
                "https://blog.jetbrains.com/kotlin/feed/",
                "https://android-developers.googleblog.com/feeds/posts/default",
                "https://www.infoq.com/kotlin/rss/"
            ),
            Theme.AI to listOf(
                "https://www.deeplearning.ai/the-batch/feed/",
                "https://huggingface.co/blog/feed.xml",
                "https://importai.substack.com/feed",
                "https://aisnakeoil.substack.com/feed"
            ),
            Theme.CLOUD to listOf(
                "https://aws.amazon.com/blogs/aws/feed/",
                "https://cloud.google.com/blog/rss/",
                "https://azure.microsoft.com/en-us/blog/feed/",
                "https://thenewstack.io/feed/"
            ),
            Theme.CYBERSECURITY to listOf(
                "https://krebsonsecurity.com/feed/",
                "https://www.schneier.com/feed/atom/",
                "https://feeds.feedburner.com/TheHackersNews",
                "https://isc.sans.edu/rssfeed.xml"
            ),
            Theme.FINANCE to listOf(
                "https://www.finextra.com/finextra-rss.aspx",
                "https://techcrunch.com/category/fintech/feed/",
                "https://www.theblock.co/rss.xml",
                "https://www.coindesk.com/arc/outboundfeeds/rss/",
                "https://feeds.bloomberg.com/technology/news.rss"
            ),
            Theme.TECH_TRENDS to listOf(
                "https://news.ycombinator.com/rss",
                "https://www.technologyreview.com/feed/",
                "https://feeds.arstechnica.com/arstechnica/index",
                "https://spectrum.ieee.org/feeds/feed.rss"
            )
        )
    }
}
