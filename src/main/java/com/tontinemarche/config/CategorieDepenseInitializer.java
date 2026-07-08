package com.tontinemarche.config;

import com.tontinemarche.domain.entity.CategorieDepense;
import com.tontinemarche.domain.enums.SensOperation;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.repository.CategorieDepenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class CategorieDepenseInitializer implements CommandLineRunner {

    private static final List<String> DEFAULTS_SORTIE = List.of(
            "Essence", "Téléphone", "Papeterie", "Salaire", "Divers"
    );
    private static final List<String> DEFAULTS_ENTREE = List.of(
            "Apport caisse", "Remboursement"
    );

    private final CategorieDepenseRepository categorieDepenseRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (categorieDepenseRepository.count() > 0) {
            return;
        }
        for (String nom : DEFAULTS_SORTIE) {
            saveIfAbsent(nom, SensOperation.SORTIE, false);
        }
        for (String nom : DEFAULTS_ENTREE) {
            saveIfAbsent(nom, SensOperation.ENTREE, "Remboursement".equalsIgnoreCase(nom));
        }
        log.info("Catégories d'opération globales initialisées");
    }

    private void saveIfAbsent(String nom, SensOperation sens, boolean necessiteClient) {
        if (categorieDepenseRepository.findByNomIgnoreCase(nom).isPresent()) {
            return;
        }
        categorieDepenseRepository.save(CategorieDepense.builder()
                .nom(nom)
                .sens(sens)
                .necessiteMouvementCaisse(true)
                .necessiteClient(necessiteClient)
                .statut(StatutEntity.ACTIF)
                .build());
    }
}
