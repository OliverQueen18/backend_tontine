package com.tontinemarche.config;

import com.tontinemarche.domain.entity.Marche;
import com.tontinemarche.repository.MarcheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class MarcheLocationMigrationInitializer implements CommandLineRunner {

    private final MarcheRepository marcheRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = 0;
        for (Marche marche : marcheRepository.findAll()) {
            boolean missingLocation = isBlank(marche.getAdresse())
                    || marche.getLatitude() == null
                    || marche.getLongitude() == null;
            if (!missingLocation) {
                continue;
            }
            if (isBlank(marche.getAdresse())) {
                marche.setAdresse(marche.getNom() + ", Bamako");
            }
            if (marche.getLatitude() == null) {
                marche.setLatitude(12.6392);
            }
            if (marche.getLongitude() == null) {
                marche.setLongitude(-8.0029);
            }
            marcheRepository.save(marche);
            updated++;
        }
        if (updated > 0) {
            log.info("Migration localisation marchés : {} enregistrement(s) complété(s)", updated);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
