package com.kyovo.domain.service

import com.kyovo.domain.model.Article
import java.time.Clock
import java.time.Duration

class ArticleCurator(private val clock: Clock) {

    fun curate(articles: List<Article>, windowHours: Long): List<Article> {
        val cutoff = clock.instant().minus(Duration.ofHours(windowHours))
        return articles
            .filter { it.publishedAt.isAfter(cutoff) }
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAt }
    }
}
