package com.kyovo.domain.port.output

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.SummarizedArticle

interface SummarizerPort {
    suspend fun summarize(articles: List<Article>): List<SummarizedArticle>
}
