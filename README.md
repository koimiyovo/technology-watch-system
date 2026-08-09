# Technology Watch System

A daily technology digest delivered to your inbox every morning. The system fetches articles from curated RSS feeds, filters the last 24 hours, sends them to Claude for relevance selection and summarisation, and emails a structured HTML digest.

## What it does

1. **Fetch** — pulls articles from 23 RSS feeds across 6 themes in parallel
2. **Curate** — keeps only articles published in the last 24 hours, deduplicates by URL
3. **Summarise** — sends the curated list to Claude (`claude-opus-4-7`), which selects up to 3 articles per theme and writes practitioner-oriented summaries in English
4. **Deliver** — sends an HTML email via SMTP (Gmail app password)
5. **Schedule** — runs automatically every day at 06:00 UTC via GitHub Actions

## Architecture

Hexagonal architecture enforced by Gradle module boundaries. `domain` has no infrastructure dependency on its classpath — it is physically impossible for it to import Ktor, Rome, or Jakarta Mail.

```
┌──────────────────────────────────────────────────────┐
│  bootstrap  (composition root — wires everything)    │
│                                                      │
│  ┌──────────────┐    ┌─────────────────────────────┐ │
│  │ application  │───▶│         domain              │ │
│  │              │    │  Article  SummarizedArticle  │ │
│  │ DailyDigest  │    │  Theme    ArticleCurator     │ │
│  │ Service      │    │  ports: input / output       │ │
│  └──────────────┘    └─────────────────────────────┘ │
│         ▲                        ▲                   │
│  ┌──────┴──────┐  ┌──────────────┴──┐  ┌──────────┐ │
│  │ infra:rss   │  │  infra:llm      │  │infra:    │ │
│  │ RssFeed     │  │  ClaudeAdapter  │  │email     │ │
│  │ Adapter     │  │  (Ktor + kx.s)  │  │Smtp...   │ │
│  │ (Rome+Ktor) │  └─────────────────┘  └──────────┘ │
│  └─────────────┘                                     │
└──────────────────────────────────────────────────────┘
```

Dependency rule: all arrows point toward `domain`. `domain` and `application` never depend on any infrastructure module.

## Modules

| Module | Role |
|---|---|
| `domain` | Entities, port interfaces (`input/` use cases, `output/` adapters), `ArticleCurator` |
| `application` | `DailyDigestService` — orchestrates ports |
| `infrastructure:rss` | `RssFeedAdapter` — Rome + Ktor CIO |
| `infrastructure:llm` | `ClaudeAdapter` — Claude API via Ktor |
| `infrastructure:email` | `SmtpNotifierAdapter` + `HtmlDigestBuilder` — Jakarta Mail |
| `bootstrap` | `Main.kt` — reads env vars, wires the object graph, produces the fat JAR |

## RSS feeds

| Theme | Sources |
|---|---|
| Kotlin | JetBrains Kotlin Blog · Android Developers Blog · InfoQ Kotlin |
| AI | The Batch (DeepLearning.AI) · Hugging Face Blog · Import AI · AI Snake Oil |
| Cloud | AWS News · Google Cloud · Azure · The New Stack |
| Cybersecurity | Krebs on Security · Schneier on Security · The Hacker News · SANS ISC |
| Finance | Finextra · TechCrunch Fintech · The Block · CoinDesk · Bloomberg Technology |
| Tech Trends | Hacker News · MIT Technology Review · Ars Technica · IEEE Spectrum |

Feed URLs are declared in `RssFeedAdapter.feedUrlsByTheme`. A failure on any individual feed is caught and logged — it never blocks the rest of the digest.

## Stack

- **Kotlin** 2.3.20 · **Gradle** 9.2.1 (Kotlin DSL) · **JVM** 22
- **kotlinx.coroutines** 1.9.0 — parallel feed fetching
- **kotlinx.serialization** 1.7.3 — Claude API JSON
- **Ktor Client** 3.0.3 (CIO engine) — HTTP
- **Rome** 2.1.0 — RSS/Atom parsing
- **Eclipse Angus Mail** 2.0.3 — SMTP
- **JUnit 5** · **MockK** — testing

## Prerequisites

- JDK 22
- An [Anthropic API key](https://console.anthropic.com/)
- A Gmail account with an [app password](https://myaccount.google.com/apppasswords) (2FA required)

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | yes | — | Anthropic API key |
| `SMTP_USERNAME` | yes | — | Gmail address |
| `SMTP_PASSWORD` | yes | — | Gmail app password (16 chars) |
| `DIGEST_RECIPIENT` | yes | — | Recipient email address |
| `SMTP_HOST` | no | `smtp.gmail.com` | SMTP host |
| `SMTP_PORT` | no | `587` | SMTP port |
| `SMTP_FROM` | no | `SMTP_USERNAME` | Sender address |

## Build & run

```bash
# Run tests
./gradlew test

# Build the executable fat JAR
./gradlew :bootstrap:shadowJar

# Run locally (env vars must be set)
java -jar bootstrap/build/libs/technology-watch-system.jar
```

## GitHub Actions

The workflow at `.github/workflows/daily-digest.yml` runs every day at **06:00 UTC**.
It can also be triggered manually from the Actions tab (`workflow_dispatch`).

### Setup

Add the following secrets in **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `ANTHROPIC_API_KEY` | Your Anthropic API key |
| `SMTP_USERNAME` | Your Gmail address |
| `SMTP_PASSWORD` | Your Gmail app password |
| `DIGEST_RECIPIENT` | The email address that receives the digest |

## Project structure

```
technology-watch-system/
├── gradle/
│   └── libs.versions.toml          # centralised dependency versions
├── domain/
│   └── src/main/kotlin/com/kyovo/domain/
│       ├── model/                  # Article, SummarizedArticle, Theme
│       ├── port/
│       │   ├── input/              # GenerateDailyDigestUseCase
│       │   └── output/             # ArticleFeedPort, SummarizerPort, NotifierPort
│       └── service/                # ArticleCurator
├── application/
│   └── src/main/kotlin/.../        # DailyDigestService
├── infrastructure/
│   ├── rss/                        # RssFeedAdapter
│   ├── llm/                        # ClaudeAdapter
│   └── email/                      # SmtpNotifierAdapter, HtmlDigestBuilder
├── bootstrap/
│   └── src/main/kotlin/.../Main.kt # composition root
└── .github/workflows/
    └── daily-digest.yml
```

## Testing

TDD is enforced on `domain` and `application`:

```bash
./gradlew :domain:test        # 8 tests — ArticleCurator
./gradlew :application:test   # 5 tests — DailyDigestService (MockK)
./gradlew test                # all modules
```

Infrastructure modules (`rss`, `llm`, `email`) are integration-tested against real endpoints and are not subject to the TDD discipline.
