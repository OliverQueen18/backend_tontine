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
@Order(1)
public class AgentMarcheMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'agents' AND column_name = 'marche_id'",
                    Integer.class);
            if (columnExists == null || columnExists == 0) {
                return;
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id AS agent_id, marche_id FROM agents WHERE marche_id IS NOT NULL");

            for (Map<String, Object> row : rows) {
                Long agentId = ((Number) row.get("agent_id")).longValue();
                Long marcheId = ((Number) row.get("marche_id")).longValue();
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM agent_marches WHERE agent_id = ? AND marche_id = ?",
                        Integer.class, agentId, marcheId);
                if (exists != null && exists == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO agent_marches (agent_id, marche_id) VALUES (?, ?)",
                            agentId, marcheId);
                }
            }
            log.info("Migration agent_marches : {} liaison(s) vérifiée(s)", rows.size());
        } catch (Exception e) {
            log.debug("Migration agent_marches ignorée : {}", e.getMessage());
        }
    }
}
