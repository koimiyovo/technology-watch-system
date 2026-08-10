package com.kyovo.infrastructure.rss

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.Theme
import com.kyovo.domain.port.output.ArticleFeedPort
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.WireFeedInput
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jdom2.input.SAXBuilder
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
        val feed = parseFeed(xml)
        feed.entries.mapNotNull { it.toArticle(theme, feed.title ?: url) }
    } catch (e: Exception) {
        System.err.println("[RssFeedAdapter] Skipped feed $url : ${e.message}")
        emptyList()
    }

    private fun parseFeed(xml: String): SyndFeed {
        // InfoQ and similar feeds embed raw, unclosed HTML void elements (e.g. <br>) in text
        // fields, which breaks XML well-formedness before either parser below even runs.
        val sanitizedXml = closeUnclosedVoidHtmlTags(xml)
        val primaryFailure = try {
            // Allow DOCTYPE declarations (Substack, Finextra, Google Cloud use them).
            // External entity resolution stays disabled to prevent XXE attacks.
            val saxBuilder = SAXBuilder()
            saxBuilder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            saxBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false)
            saxBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            val document = saxBuilder.build(sanitizedXml.byteInputStream(Charsets.UTF_8))
            // WireFeedInput returns the format-specific WireFeed (e.g. Channel), not a SyndFeed directly.
            return SyndFeedImpl(WireFeedInput().build(document))
        } catch (e: Exception) {
            e
        }
        return try {
            // Fallback: Rome's healer fixes invalid characters and similar low-level malformations.
            val input = SyndFeedInput()
            input.setXmlHealerOn(true)
            input.build(StringReader(sanitizedXml))
        } catch (fallbackFailure: Exception) {
            // The fallback parser rejects any DOCTYPE outright, so its message is a red herring
            // whenever a DOCTYPE happens to be present (e.g. an anti-bot HTML page returned
            // instead of the real feed) - it names DOCTYPE even when that isn't the real
            // problem. The primary parser's error is the one that names what actually failed.
            throw primaryFailure
        }
    }

    // Limited to void elements that never appear as legitimate RSS/Atom element names
    // (unlike e.g. "link" or "area"), so this cannot corrupt well-formed feed content.
    private val voidHtmlTags = listOf("br", "hr", "img", "wbr")

    private fun closeUnclosedVoidHtmlTags(xml: String): String =
        voidHtmlTags.fold(xml) { acc, tag ->
            acc.replace(Regex("<$tag(\\s[^<>]*)?(?<!/)>", RegexOption.IGNORE_CASE)) { match ->
                "<$tag${match.groupValues[1]}/>"
            }
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
                "https://feed.infoq.com/kotlin/"
            ),
            Theme.AI to listOf(
                "https://huggingface.co/blog/feed.xml",
                "https://www.normaltech.ai/feed"
            ),
            Theme.CLOUD to listOf(
                "https://aws.amazon.com/blogs/aws/feed/",
                "https://cloudblog.withgoogle.com/rss/",
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
