package com.kyovo.infrastructure.email

import com.kyovo.domain.model.SummarizedArticle
import com.kyovo.domain.port.output.NotifierPort
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class SmtpNotifierAdapter(private val config: SmtpConfig) : NotifierPort {

    // Transport.send() est bloquant — on le confine sur IO.
    override suspend fun sendDigest(articles: List<SummarizedArticle>): Unit =
        withContext(Dispatchers.IO) {
            val session = buildSession()
            val message = buildMessage(session, articles)
            Transport.send(message)
        }

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(config.username, config.password)
        })
    }

    private fun buildMessage(session: Session, articles: List<SummarizedArticle>): Message =
        MimeMessage(session).apply {
            setFrom(InternetAddress(config.from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.to))
            subject = HtmlDigestBuilder.subject()
            setContent(HtmlDigestBuilder.build(articles), "text/html; charset=utf-8")
        }
}
