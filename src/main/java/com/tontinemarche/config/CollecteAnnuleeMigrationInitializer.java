package com.tontinemarche.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ajoute la colonne collectes.annulee si absente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class CollecteAnnuleeMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute(
                "ALTER TABLE collectes ADD COLUMN IF NOT EXISTS annulee BOOLEAN NOT NULL DEFAULT false"
        );
        jdbcTemplate.execute(
                "UPDATE collectes SET annulee = false WHERE annulee IS NULL"
        );
        log.info("Colonne collectes.annulee vérifiée");
    }
}
