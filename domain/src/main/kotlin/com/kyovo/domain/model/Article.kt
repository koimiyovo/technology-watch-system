package com.kyovo.domain.model

import java.time.Instant

data class Article(
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Instant,
    val theme: Theme,
    val source: String
)
