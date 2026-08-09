package com.kyovo.domain.port.output

import com.kyovo.domain.model.Article
import com.kyovo.domain.model.Theme

interface ArticleFeedPort {
    suspend fun fetchArticles(theme: Theme): List<Article>
}
