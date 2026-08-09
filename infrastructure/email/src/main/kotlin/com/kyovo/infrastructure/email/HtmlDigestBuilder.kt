package com.kyovo.infrastructure.email

import com.kyovo.domain.model.SummarizedArticle
import com.kyovo.domain.model.Theme
import java.time.LocalDate

object HtmlDigestBuilder {

    fun subject(): String = "Veille technologique – ${LocalDate.now()}"

    fun build(articles: List<SummarizedArticle>): String {
        val sections = Theme.entries
            .mapNotNull { theme ->
                val themeArticles = articles.filter { it.article.theme == theme }
                themeArticles.takeIf { it.isNotEmpty() }?.let { buildSection(theme, it) }
            }
            .joinToString("\n")

        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8">
              <style>$CSS</style>
            </head>
            <body>
              <h1>Veille technologique &ndash; ${LocalDate.now()}</h1>
              $sections
              <footer><p>Généré automatiquement. ${articles.size} article(s) sélectionné(s).</p></footer>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSection(theme: Theme, articles: List<SummarizedArticle>): String {
        val items = articles.joinToString("\n") { buildArticleItem(it) }
        return """
            <section>
              <h2>${theme.displayName()}</h2>
              $items
            </section>
        """.trimIndent()
    }

    private fun buildArticleItem(s: SummarizedArticle): String {
        val keyPoints = if (s.keyPoints.isNotEmpty())
            "<ul>${s.keyPoints.joinToString("") { "<li>${it.escapeHtml()}</li>" }}</ul>"
        else ""
        return """
            <article>
              <h3><a href="${s.article.link}">${s.article.title.escapeHtml()}</a></h3>
              <p class="meta">${s.article.source.escapeHtml()} &middot; Pertinence : ${s.relevanceScore}/10</p>
              <p>${s.summary.escapeHtml()}</p>
              $keyPoints
            </article>
        """.trimIndent()
    }

    // Display logic (localised theme name) belongs to the presentation layer,
    // not to the domain — that is why this function lives here and not in the enum.
    private fun Theme.displayName(): String = when (this) {
        Theme.KOTLIN        -> "Kotlin"
        Theme.AI            -> "Intelligence Artificielle"
        Theme.CLOUD         -> "Cloud"
        Theme.CYBERSECURITY -> "Cybersécurité"
        Theme.FINANCE       -> "Finance & Fintech"
        Theme.TECH_TRENDS   -> "Tendances Tech"
    }

    private fun String.escapeHtml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val CSS = """
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
               max-width: 780px; margin: 40px auto; padding: 0 20px; color: #1a1a1a; }
        h1   { font-size: 1.5rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 0.5rem; }
        h2   { font-size: 1.2rem; margin-top: 2.5rem; color: #0f3460; }
        h3   { font-size: 1rem; margin: 0 0 0.25rem; }
        h3 a { color: #0f3460; text-decoration: none; }
        h3 a:hover { text-decoration: underline; }
        article { margin: 1.2rem 0; padding: 1rem; border-left: 3px solid #0f3460;
                  background: #fafafa; border-radius: 0 4px 4px 0; }
        .meta { color: #666; font-size: 0.8rem; margin: 0.2rem 0 0.6rem; }
        ul   { margin: 0.5rem 0 0 1.2rem; padding: 0; }
        li   { margin: 0.2rem 0; font-size: 0.9rem; }
        footer { margin-top: 3rem; font-size: 0.75rem; color: #999; border-top: 1px solid #e0e0e0; }
    """.trimIndent()
}
