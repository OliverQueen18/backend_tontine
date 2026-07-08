package com.tontinemarche.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class CategorieGlobalMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            migrateToGlobalCategories();
        } catch (Exception e) {
            log.debug("Migration catégories globales ignorée : {}", e.getMessage());
        }
    }

    private void migrateToGlobalCategories() {
        Integer agenceCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'categories_depenses' AND column_name = 'agence_id'",
                Integer.class);
        if (agenceCol == null || agenceCol == 0) {
            return;
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS agence_categorie_desactivations (
                id BIGSERIAL PRIMARY KEY,
                created_at TIMESTAMP,
                updated_at TIMESTAMP,
                agence_id BIGINT NOT NULL REFERENCES agences(id),
                categorie_id BIGINT NOT NULL REFERENCES categories_depenses(id),
                UNIQUE(agence_id, categorie_id)
            )
            """);

        List<Map<String, Object>> groups = jdbcTemplate.queryForList("""
            SELECT LOWER(nom) AS nom_key, MIN(id) AS keep_id
            FROM categories_depenses
            GROUP BY LOWER(nom)
            """);

        for (Map<String, Object> group : groups) {
            Long keepId = ((Number) group.get("keep_id")).longValue();
            String nomKey = group.get("nom_key").toString();

            List<Map<String, Object>> duplicates = jdbcTemplate.queryForList("""
                SELECT id, agence_id, statut FROM categories_depenses
                WHERE LOWER(nom) = ? AND id <> ?
                """, nomKey, keepId);

            for (Map<String, Object> dup : duplicates) {
                Long dupId = ((Number) dup.get("id")).longValue();
                Long agenceId = dup.get("agence_id") != null ? ((Number) dup.get("agence_id")).longValue() : null;
                String statut = dup.get("statut") != null ? dup.get("statut").toString() : "ACTIF";

                if (agenceId != null && "INACTIF".equals(statut)) {
                    jdbcTemplate.update("""
                        INSERT INTO agence_categorie_desactivations (agence_id, categorie_id, created_at, updated_at)
                        VALUES (?, ?, NOW(), NOW())
                        ON CONFLICT (agence_id, categorie_id) DO NOTHING
                        """, agenceId, keepId);
                }
                jdbcTemplate.update("DELETE FROM categories_depenses WHERE id = ?", dupId);
            }
        }

        jdbcTemplate.execute("ALTER TABLE categories_depenses DROP CONSTRAINT IF EXISTS categories_depenses_agence_id_nom_key");
        jdbcTemplate.execute("ALTER TABLE categories_depenses DROP CONSTRAINT IF EXISTS uk_categories_depenses_agence_nom");
        jdbcTemplate.execute("ALTER TABLE categories_depenses DROP COLUMN IF EXISTS agence_id");
        jdbcTemplate.execute("""
            DO $$ BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_constraint WHERE conname = 'categories_depenses_nom_key'
                ) THEN
                    ALTER TABLE categories_depenses ADD CONSTRAINT categories_depenses_nom_key UNIQUE (nom);
                END IF;
            END $$
            """);

        log.info("Migration catégories globales terminée");
    }
}
