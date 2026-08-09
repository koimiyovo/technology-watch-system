package com.kyovo.domain.port.output

import com.kyovo.domain.model.SummarizedArticle

interface NotifierPort {
    suspend fun sendDigest(articles: List<SummarizedArticle>)
}
