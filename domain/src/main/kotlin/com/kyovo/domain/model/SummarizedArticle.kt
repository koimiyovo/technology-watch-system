package com.kyovo.domain.model

data class SummarizedArticle(
    val article: Article,
    val summary: String,
    val relevanceScore: Int,
    val keyPoints: List<String>
)
