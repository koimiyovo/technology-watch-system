package com.kyovo.infrastructure.rss

import com.kyovo.domain.model.Theme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RssFeedAdapterTest {

    // KOTLIN feed URLs, fixed in RssFeedAdapter.feedUrlsByTheme.
    private val jetbrainsUrl = "https://blog.jetbrains.com/kotlin/feed/"
    private val androidUrl   = "https://android-developers.googleblog.com/feeds/posts/default"
    private val infoqUrl     = "https://feed.infoq.com/kotlin/"

    // AI feed URL, fixed in RssFeedAdapter.feedUrlsByTheme.
    private val importAiUrl = "https://importai.substack.com/feed"

    // Any URL not stubbed with real feed content behaves like a broken feed:
    // both parsing attempts fail, so RssFeedAdapter must skip it silently.
    private fun adapterWith(responsesByUrl: Map<String, String>): RssFeedAdapter {
        val engine = MockEngine { request ->
            val body = responsesByUrl[request.url.toString()] ?: "not xml at all"
            respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/xml"))
        }
        return RssFeedAdapter(HttpClient(engine))
    }

    @Test
    fun `parses a well-formed RSS feed without a DOCTYPE declaration`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Kotlin Blog</title>
                <link>https://blog.jetbrains.com/kotlin/</link>
                <item>
                  <title>Kotlin 2.3 Released</title>
                  <link>https://blog.jetbrains.com/kotlin/2026/08/kotlin-2-3/</link>
                  <description>Kotlin 2.3 brings new language features.</description>
                  <pubDate>Mon, 10 Aug 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val adapter = adapterWith(mapOf(jetbrainsUrl to xml))
        val articles = adapter.fetchArticles(Theme.KOTLIN)

        assertEquals(1, articles.size)
        val article = articles.single()
        assertEquals("Kotlin 2.3 Released", article.title)
        assertEquals("https://blog.jetbrains.com/kotlin/2026/08/kotlin-2-3/", article.link)
        assertEquals("Kotlin Blog", article.source)
        assertTrue(article.summary.contains("Kotlin 2.3 brings new language features."))
    }

    @Test
    fun `parses an RSS feed with a DOCTYPE declaration`() = runTest {
        // Mirrors Substack/Finextra/Google Cloud feeds, which declare internal DTD entities.
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE rss [
              <!ENTITY nbsp "&#160;">
            ]>
            <rss version="2.0">
              <channel>
                <title>Android Developers Blog</title>
                <link>https://android-developers.googleblog.com/</link>
                <item>
                  <title>Kotlin Multiplatform Update</title>
                  <link>https://android-developers.googleblog.com/2026/08/kmp-update.html</link>
                  <description>New tooling&nbsp;is available.</description>
                  <pubDate>Mon, 10 Aug 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val adapter = adapterWith(mapOf(androidUrl to xml))
        val articles = adapter.fetchArticles(Theme.KOTLIN)

        assertEquals(1, articles.size)
        val article = articles.single()
        assertEquals("Kotlin Multiplatform Update", article.title)
        assertEquals("Android Developers Blog", article.source)
    }

    @Test
    fun `parses a feed with an unclosed HTML void tag by self-closing it before parsing`() = runTest {
        // Mirrors InfoQ feeds: an unclosed <br> tag breaks strict XML well-formedness.
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>InfoQ</title>
                <link>https://www.infoq.com/kotlin/</link>
                <item>
                  <title>Kotlin Coroutines Guide</title>
                  <link>https://www.infoq.com/articles/kotlin-coroutines</link>
                  <description>Read more<br>Continued here.</description>
                  <pubDate>Mon, 10 Aug 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val adapter = adapterWith(mapOf(infoqUrl to xml))
        val articles = adapter.fetchArticles(Theme.KOTLIN)

        assertEquals(1, articles.size)
        assertEquals("Kotlin Coroutines Guide", articles.single().title)
    }

    @Test
    fun `does not alter an already self-closed void tag or the RSS link element`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>InfoQ</title>
                <link>https://www.infoq.com/kotlin/</link>
                <item>
                  <title>Kotlin Coroutines Guide</title>
                  <link>https://www.infoq.com/articles/kotlin-coroutines</link>
                  <description>Illustration<img src="https://example.com/x.png"/> and a break<br/>here.</description>
                  <pubDate>Mon, 10 Aug 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val adapter = adapterWith(mapOf(infoqUrl to xml))
        val articles = adapter.fetchArticles(Theme.KOTLIN)

        assertEquals(1, articles.size)
        val article = articles.single()
        assertEquals("Kotlin Coroutines Guide", article.title)
        assertEquals("https://www.infoq.com/articles/kotlin-coroutines", article.link)
    }

    @Test
    fun `skips a feed whose XML cannot be parsed by either strategy without failing the others`() = runTest {
        val validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Kotlin Blog</title>
                <link>https://blog.jetbrains.com/kotlin/</link>
                <item>
                  <title>Kotlin 2.3 Released</title>
                  <link>https://blog.jetbrains.com/kotlin/2026/08/kotlin-2-3/</link>
                  <description>Kotlin 2.3 brings new language features.</description>
                  <pubDate>Mon, 10 Aug 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        // androidUrl and infoqUrl are left unstubbed, so they receive "not xml at all"
        // and must be skipped without throwing.
        val adapter = adapterWith(mapOf(jetbrainsUrl to validXml))
        val articles = adapter.fetchArticles(Theme.KOTLIN)

        assertEquals(1, articles.size)
        assertEquals("Kotlin 2.3 Released", articles.single().title)
    }

    @Test
    fun `parses the real importai substack feed sample`() = runTest {
        // Real payload fetched from https://importai.substack.com/feed on 2026-08-10:
        // Substack-generated RSS 2.0, no DOCTYPE, itunes/googleplay namespaces, every text
        // field wrapped in a multi-line CDATA block, content:encoded bodies full of escaped
        // HTML entities (&#8217; etc.) and literal href="..." links.
        val xml = javaClass.getResourceAsStream("/importai-feed-sample.xml")!!
            .bufferedReader(Charsets.UTF_8).readText()

        val adapter = adapterWith(mapOf(importAiUrl to xml))
        val articles = adapter.fetchArticles(Theme.AI)

        assertEquals(2, articles.size)
        val latest = articles.single { it.link.endsWith("posttrainbench") }
        assertEquals(
            "Import AI 468: 23 RSI ideas; PostTrainBench+; and how trust and transparency interplay with AI racing",
            latest.title
        )
        // Substack pretty-prints the channel <title> CDATA across lines, and Rome does not
        // trim it, so the raw source name carries leading/trailing whitespace and newlines.
        assertEquals("Import AI", latest.source.trim())
        assertTrue(latest.summary.isNotBlank())
    }

    @Test
    fun `logs both parser errors when the feed URL returns an HTML anti-bot page instead of RSS`() = runTest {
        // Reproduces the case reported for importai.substack.com/feed: the response body was
        // an HTML challenge/error page, not the real feed (which has no DOCTYPE at all). Before
        // this test, the logged error only named the fallback parser's blanket DOCTYPE
        // restriction, hiding the real, more informative cause from the primary parser.
        val antiBotHtml = """
            <!DOCTYPE html>
            <html><head><title>Just a moment...</title></head>
            <body>Enable JavaScript and cookies to continue</body></html>
        """.trimIndent()

        val originalErr = System.err
        val capturedErr = java.io.ByteArrayOutputStream()
        System.setErr(java.io.PrintStream(capturedErr))
        val articles: List<com.kyovo.domain.model.Article>
        try {
            val adapter = adapterWith(mapOf(importAiUrl to antiBotHtml))
            articles = adapter.fetchArticles(Theme.AI)
        } finally {
            System.setErr(originalErr)
        }

        assertTrue(articles.isEmpty())
        val stderr = capturedErr.toString(Charsets.UTF_8)
        assertTrue(stderr.contains("Skipped feed $importAiUrl"))
        assertTrue(stderr.contains("primary parser:"))
        assertTrue(stderr.contains("fallback parser:"))
        assertTrue(stderr.contains("DOCTYPE is disallowed"))
    }
}
