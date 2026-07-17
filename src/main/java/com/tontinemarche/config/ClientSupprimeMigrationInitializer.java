package com.tontinemarche.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ajoute la colonne soft-delete clients.supprime si absente
 * (Hibernate ddl-auto=update ne la crée pas toujours correctement en boolean NOT NULL).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class ClientSupprimeMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute(
                "ALTER TABLE clients ADD COLUMN IF NOT EXISTS supprime BOOLEAN NOT NULL DEFAULT false"
        );
        jdbcTemplate.execute(
                "UPDATE clients SET supprime = false WHERE supprime IS NULL"
        );
        log.info("Colonne clients.supprime vérifiée");
    }
}
