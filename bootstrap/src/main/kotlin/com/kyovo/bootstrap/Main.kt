package com.kyovo.bootstrap

import com.kyovo.application.DailyDigestService
import com.kyovo.domain.service.ArticleCurator
import com.kyovo.infrastructure.email.SmtpConfig
import com.kyovo.infrastructure.email.SmtpNotifierAdapter
import com.kyovo.infrastructure.llm.ClaudeAdapter
import com.kyovo.infrastructure.rss.RssFeedAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import java.time.Clock
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val httpClient = buildHttpClient()

    val service = DailyDigestService(
        feedPort       = RssFeedAdapter(httpClient),
        curator        = ArticleCurator(Clock.systemUTC()),
        windowHours    = 24L,
        summarizerPort = ClaudeAdapter(
            httpClient = httpClient,
            apiKey     = requireEnv("ANTHROPIC_API_KEY")
        ),
        notifierPort   = SmtpNotifierAdapter(
            SmtpConfig(
                host     = env("SMTP_HOST") ?: "smtp.gmail.com",
                port     = env("SMTP_PORT")?.toInt() ?: 587,
                username = requireEnv("SMTP_USERNAME"),
                password = requireEnv("SMTP_PASSWORD"),
                from     = env("SMTP_FROM") ?: requireEnv("SMTP_USERNAME"),
                to       = requireEnv("DIGEST_RECIPIENT")
            )
        )
    )

    runCatching { service.execute() }
        .onSuccess { println("Digest generated and sent successfully.") }
        .onFailure { e ->
            System.err.println("Fatal error: ${e.message}")
            e.printStackTrace()
            httpClient.close()
            exitProcess(1)
        }

    httpClient.close()
}

private fun buildHttpClient() = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 120_000  // Claude API calls can take up to ~60s
        socketTimeoutMillis  = 60_000
    }
}

private fun env(name: String): String? = System.getenv(name)

private fun requireEnv(name: String): String =
    env(name) ?: error("Required environment variable '$name' is not set.")
