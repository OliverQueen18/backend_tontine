package com.tontinemarche.config;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.service.CommissionGrilleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
    public void run(String... args) {
        for (Agence agence : agenceRepository.findAll()) {
            commissionGrilleService.ensureDefaultGrille(agence);
        }
        log.info("Grilles de commission vérifiées pour toutes les agences");
    }
}
