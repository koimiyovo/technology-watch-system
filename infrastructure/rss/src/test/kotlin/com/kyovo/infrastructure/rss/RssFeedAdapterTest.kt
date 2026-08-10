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
    private val infoqUrl     = "https://www.infoq.com/kotlin/rss/"

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
}
