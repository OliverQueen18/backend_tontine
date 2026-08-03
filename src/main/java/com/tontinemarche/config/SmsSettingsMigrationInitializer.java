package com.tontinemarche.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Colonnes d'activation des notifications SMS (plateforme / agence / client).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class SmsSettingsMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                ALTER TABLE platform_settings
                ADD COLUMN IF NOT EXISTS sms_notifications_enabled BOOLEAN NOT NULL DEFAULT false
                """);
        jdbcTemplate.execute("""
                ALTER TABLE agences
                ADD COLUMN IF NOT EXISTS sms_pour_tous_clients BOOLEAN NOT NULL DEFAULT false
                """);
        jdbcTemplate.execute("""
                ALTER TABLE clients
                ADD COLUMN IF NOT EXISTS sms_notifications_enabled BOOLEAN NOT NULL DEFAULT false
                """);
        log.info("Colonnes SMS (plateforme / agence / clients) vérifiées");
    }
}
