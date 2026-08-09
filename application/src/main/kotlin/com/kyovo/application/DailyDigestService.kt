package com.kyovo.application

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.Theme
import com.kyovo.domain.port.input.GenerateDailyDigestUseCase
import com.kyovo.domain.port.output.ArticleFeedPort
import com.kyovo.domain.port.output.NotifierPort
import com.kyovo.domain.port.output.SummarizerPort
import com.kyovo.domain.service.ArticleCurator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DailyDigestService(
    private val feedPort: ArticleFeedPort,
    private val curator: ArticleCurator,
    private val summarizerPort: SummarizerPort,
    private val notifierPort: NotifierPort,
    private val windowHours: Long
) : GenerateDailyDigestUseCase {

    override suspend fun execute() {
        val rawArticles = fetchAllThemesInParallel()
        val curated = curator.curate(rawArticles, windowHours)
        if (curated.isEmpty()) return

        val summarized = summarizerPort.summarize(curated)
        if (summarized.isEmpty()) return

        notifierPort.sendDigest(summarized)
    }

    // Each theme is fetched concurrently to minimise total latency.
    // coroutineScope ensures all child coroutines complete (or are cancelled on failure)
    // before returning.
    private suspend fun fetchAllThemesInParallel(): List<Article> = coroutineScope {
        Theme.entries
            .map { theme -> async { feedPort.fetchArticles(theme) } }
            .awaitAll()
            .flatten()
    }
}
