package com.tontinemarche.config;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.service.CommissionGrilleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class GrilleCommissionInitializer implements CommandLineRunner {

    private final AgenceRepository agenceRepository;
    private final CommissionGrilleService commissionGrilleService;

    /**
     * Si true, remplace la grille de toutes les agences par la grille métier par défaut
     * (utile une fois après déploiement). Remettre à false ensuite pour ne pas écraser
     * les personnalisations faites dans Paramètres.
     */
    @Value("${app.commission.apply-default-grille-on-startup:false}")
    private boolean applyDefaultOnStartup;

    @Override
    @Transactional
    public void run(String... args) {
        for (Agence agence : agenceRepository.findAll()) {
            if (applyDefaultOnStartup) {
                commissionGrilleService.replaceWithDefaultGrille(agence);
            } else {
                commissionGrilleService.ensureDefaultGrille(agence);
            }
        }
        if (applyDefaultOnStartup) {
            log.info("Grilles de commission remplacées par la grille par défaut (toutes les agences)");
        } else {
            log.info("Grilles de commission vérifiées pour toutes les agences");
        }
    }
}
