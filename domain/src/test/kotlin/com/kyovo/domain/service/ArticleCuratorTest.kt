package com.kyovo.domain.service

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.Theme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleCuratorTest {

    private val now = Instant.parse("2026-08-09T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val curator = ArticleCurator(clock)

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun article(
        link: String = "https://example.com/default",
        publishedAt: Instant = now.minus(Duration.ofHours(1)),
        title: String = "Title"
    ) = Article(
        title = title,
        link = link,
        summary = "Summary",
        publishedAt = publishedAt,
        theme = Theme.KOTLIN,
        source = "Test Feed"
    )

    // ── filtering ─────────────────────────────────────────────────────────────

    @Test
    fun `keeps article published exactly at the window boundary`() {
        val atBoundary = article(link = "https://a.com", publishedAt = now.minus(Duration.ofHours(24)))
        assertEquals(emptyList(), curator.curate(listOf(atBoundary), windowHours = 24))
    }

    @Test
    fun `keeps article published one second inside the window`() {
        val justIn = article(link = "https://b.com", publishedAt = now.minus(Duration.ofHours(24)).plusSeconds(1))
        assertEquals(listOf(justIn), curator.curate(listOf(justIn), windowHours = 24))
    }

    @Test
    fun `excludes articles older than 24h`() {
        val old = article(link = "https://c.com", publishedAt = now.minus(Duration.ofHours(25)))
        assertEquals(emptyList(), curator.curate(listOf(old), windowHours = 24))
    }

    @Test
    fun `handles empty input`() {
        assertEquals(emptyList(), curator.curate(emptyList(), windowHours = 24))
    }

    // ── deduplication ─────────────────────────────────────────────────────────

    @Test
    fun `keeps only the first occurrence of a duplicate link`() {
        val original  = article(link = "https://d.com", title = "Original")
        val duplicate = article(link = "https://d.com", title = "Duplicate")
        val result = curator.curate(listOf(original, duplicate), windowHours = 24)
        assertEquals(1, result.size)
        assertEquals("Original", result.single().title)
    }

    @Test
    fun `keeps articles with distinct links`() {
        val first  = article(link = "https://e.com")
        val second = article(link = "https://f.com")
        assertEquals(2, curator.curate(listOf(first, second), windowHours = 24).size)
    }

    // ── ordering ──────────────────────────────────────────────────────────────

    @Test
    fun `returns articles sorted from most recent to oldest`() {
        val older = article(link = "https://g.com", publishedAt = now.minus(Duration.ofHours(10)))
        val newer = article(link = "https://h.com", publishedAt = now.minus(Duration.ofHours(2)))
        assertEquals(listOf(newer, older), curator.curate(listOf(older, newer), windowHours = 24))
    }

    // ── custom window ─────────────────────────────────────────────────────────

    @Test
    fun `respects a custom windowHours parameter`() {
        val within = article(link = "https://i.com", publishedAt = now.minus(Duration.ofHours(6)))
        val beyond = article(link = "https://j.com", publishedAt = now.minus(Duration.ofHours(13)))
        assertEquals(listOf(within), curator.curate(listOf(within, beyond), windowHours = 12))
    }
}
