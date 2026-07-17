package com.tontinemarche.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.from-name:Tontine Marché}")
    private String fromName;

    @Async
    public void send(String to, String subject, String htmlBody) {
        if (!enabled) {
            log.warn("Envoi mail DÉSACTIVÉ (MAIL_ENABLED=false) — destinataire={} sujet={}", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email envoyé à {} — {}", to, subject);
        } catch (Exception ex) {
            log.error("Échec envoi email à {} — {}: {}", to, subject, ex.getMessage());
        }
    }
}
