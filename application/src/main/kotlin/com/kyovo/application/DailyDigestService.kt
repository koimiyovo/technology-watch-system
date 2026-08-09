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
    private val notifierPort: NotifierPort
) : GenerateDailyDigestUseCase {

    override suspend fun execute() {
        val rawArticles = fetchAllThemesInParallel()
        val curated = curator.curate(rawArticles)
        if (curated.isEmpty()) return

        val summarized = summarizerPort.summarize(curated)
        if (summarized.isEmpty()) return

        notifierPort.sendDigest(summarized)
    }

    // Chaque thème est fetché en parallèle pour minimiser la latence totale.
    // coroutineScope garantit que toutes les coroutines enfants sont terminées
    // (ou annulées en cas d'exception) avant de retourner.
    private suspend fun fetchAllThemesInParallel(): List<Article> = coroutineScope {
        Theme.entries
            .map { theme -> async { feedPort.fetchArticles(theme) } }
            .awaitAll()
            .flatten()
    }
}
