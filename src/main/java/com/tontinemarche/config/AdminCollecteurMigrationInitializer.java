package com.tontinemarche.config;

import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.repository.AgentRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class AdminCollecteurMigrationInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final AgentRepository agentRepository;
    private final AgentService agentService;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        for (Utilisateur user : utilisateurRepository.findByRole(RoleType.ADMIN_AGENCE)) {
            if (user.getAgence() == null) {
                continue;
            }
            if (agentRepository.findByUtilisateurId(user.getId()).isEmpty()) {
                agentService.ensureCollecteurProfile(user);
                created++;
            } else {
                agentService.ensureCollecteurProfile(user);
            }
        }
        if (created > 0) {
            log.info("Migration profils collecteur admin : {} profil(s) créé(s)", created);
        }
    }
}
