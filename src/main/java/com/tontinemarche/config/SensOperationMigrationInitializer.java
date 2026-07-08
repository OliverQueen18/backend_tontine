package com.tontinemarche.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class SensOperationMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        backfillColumn("categories_depenses", "SORTIE");
        backfillColumn("depenses", "SORTIE");
        backfillBoolean("categories_depenses", "necessite_mouvement_caisse", true);
        backfillBoolean("categories_depenses", "necessite_client", false);
    }

    private void backfillColumn(String table, String defaultValue) {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = 'sens'",
                    Integer.class, table);
            if (columnExists == null || columnExists == 0) {
                return;
            }
            int updated = jdbcTemplate.update(
                    "UPDATE " + table + " SET sens = ? WHERE sens IS NULL",
                    defaultValue);
            if (updated > 0) {
                log.info("Migration sens : {} ligne(s) mises à jour dans {}", updated, table);
            }
        } catch (Exception e) {
            log.debug("Migration sens ignorée pour {} : {}", table, e.getMessage());
        }
    }

    private void backfillBoolean(String table, String column, boolean defaultValue) {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            if (columnExists == null || columnExists == 0) {
                return;
            }
            int updated = jdbcTemplate.update(
                    "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " IS NULL",
                    defaultValue);
            if (updated > 0) {
                log.info("Migration {} : {} ligne(s) mises à jour dans {}", column, updated, table);
            }
        } catch (Exception e) {
            log.debug("Migration {} ignorée pour {} : {}", column, table, e.getMessage());
        }
    }
}
