package com.tontinemarche.config;

import com.tontinemarche.domain.entity.*;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final AgenceRepository agenceRepository;
    private final MarcheRepository marcheRepository;
    private final QuartierRepository quartierRepository;
    private final AgentRepository agentRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (utilisateurRepository.count() > 0) {
            return;
        }

        log.info("Initialisation des données de démonstration...");

        Utilisateur superAdmin = utilisateurRepository.save(Utilisateur.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .nomComplet("Super Administrateur")
                .email("oliveservicespro@gmail.com")
                .telephone("+22370000000")
                .role(RoleType.SUPER_ADMIN)
                .statut(StatutEntity.ACTIF)
                .build());

        Agence bko = agenceRepository.save(Agence.builder()
                .code("BKO")
                .nom("Agence Bamako Centre")
                .responsable("Amadou Traoré")
                .telephone("+22370111111")
                .email("bko@tontinemarche.com")
                .adresse("Marché Médine")
                .ville("Bamako")
                .tauxCommission(new BigDecimal("0.1000"))
                .tauxCommissionAdmin(new BigDecimal("0.0500"))
                .statut(StatutEntity.ACTIF)
                .build());

        Agence sgo = agenceRepository.save(Agence.builder()
                .code("SGO")
                .nom("Agence Ségou")
                .responsable("Fatoumata Diallo")
                .telephone("+22370222222")
                .email("sgo@tontinemarche.com")
                .adresse("Marché Central")
                .ville("Ségou")
                .tauxCommission(new BigDecimal("0.1000"))
                .tauxCommissionAdmin(new BigDecimal("0.0500"))
                .statut(StatutEntity.ACTIF)
                .build());

        utilisateurRepository.save(Utilisateur.builder()
                .username("admin.bko")
                .password(passwordEncoder.encode("admin123"))
                .nomComplet("Amadou Traoré")
                .email("oliveservicespro@gmail.com")
                .telephone("+22370111111")
                .role(RoleType.ADMIN_AGENCE)
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        // admin.bko sera lié à un profil collecteur après création des marchés (voir fin)

        utilisateurRepository.save(Utilisateur.builder()
                .username("caissier.bko")
                .password(passwordEncoder.encode("caisse123"))
                .nomComplet("Mariama Coulibaly")
                .email("oliveservicespro@gmail.com")
                .telephone("+22370333333")
                .role(RoleType.CAISSIER)
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        utilisateurRepository.save(Utilisateur.builder()
                .username("auditeur")
                .password(passwordEncoder.encode("audit123"))
                .nomComplet("Ibrahim Keita")
                .email("oliveservicespro@gmail.com")
                .role(RoleType.AUDITEUR)
                .statut(StatutEntity.ACTIF)
                .build());

        Marche medine = marcheRepository.save(Marche.builder()
                .nom("Marché Médine")
                .code("MEDINE")
                .description("Grand marché de Médine")
                .adresse("Avenue de l'OUA, Médine, Bamako")
                .latitude(12.6522)
                .longitude(-8.0029)
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        Marche grandMarche = marcheRepository.save(Marche.builder()
                .nom("Grand Marché")
                .code("GMARCHE")
                .adresse("Centre-ville, Bamako")
                .latitude(12.6392)
                .longitude(-7.9994)
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        Quartier medineQ = quartierRepository.save(Quartier.builder()
                .nom("Médine")
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        quartierRepository.save(Quartier.builder()
                .nom("Hippodrome")
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        Utilisateur agentUser1 = utilisateurRepository.save(Utilisateur.builder()
                .username("agent001")
                .password(passwordEncoder.encode("agent123"))
                .nomComplet("Moussa Diarra")
                .email("oliveservicespro@gmail.com")
                .telephone("+22370444444")
                .role(RoleType.AGENT)
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        Utilisateur agentUser2 = utilisateurRepository.save(Utilisateur.builder()
                .username("agent002")
                .password(passwordEncoder.encode("agent123"))
                .nomComplet("Aïssata Sangaré")
                .email("oliveservicespro@gmail.com")
                .telephone("+22370555555")
                .role(RoleType.AGENT)
                .agence(bko)
                .statut(StatutEntity.ACTIF)
                .build());

        Agent agent1 = agentRepository.save(Agent.builder()
                .code("BKO-A001")
                .nomComplet("Moussa Diarra")
                .telephone("+22370444444")
                .agence(bko)
                .marches(new java.util.ArrayList<>(java.util.List.of(medine, grandMarche)))
                .utilisateur(agentUser1)
                .statut(StatutEntity.ACTIF)
                .build());

        Agent agent2 = agentRepository.save(Agent.builder()
                .code("BKO-A002")
                .nomComplet("Aïssata Sangaré")
                .telephone("+22370555555")
                .agence(bko)
                .marches(new java.util.ArrayList<>(java.util.List.of(grandMarche)))
                .utilisateur(agentUser2)
                .statut(StatutEntity.ACTIF)
                .build());

        createClient(bko, medine, medineQ, agent1, "Aminata Touré", "+22376000001", "1000");
        createClient(bko, medine, medineQ, agent1, "Boubacar Koné", "+22376000002", "2000");
        createClient(bko, medine, medineQ, agent1, "Oumou Diakité", "+22376000003", "500");
        createClient(bko, grandMarche, medineQ, agent2, "Sékou Camara", "+22376000004", "1500");
        createClient(bko, grandMarche, medineQ, agent2, "Fatoumata Sy", "+22376000005", "1000");

        Utilisateur adminBko = utilisateurRepository.findByUsername("admin.bko").orElseThrow();
        agentRepository.save(Agent.builder()
                .code("BKO-A000")
                .nomComplet(adminBko.getNomComplet())
                .telephone(adminBko.getTelephone())
                .agence(bko)
                .marches(new java.util.ArrayList<>(java.util.List.of(medine, grandMarche)))
                .utilisateur(adminBko)
                .statut(StatutEntity.ACTIF)
                .build());

        marcheRepository.save(Marche.builder()
                .nom("Marché Central")
                .code("CENTRAL")
                .adresse("Centre-ville, Ségou")
                .latitude(13.4314)
                .longitude(-2.2158)
                .agence(sgo)
                .statut(StatutEntity.ACTIF)
                .build());

        log.info("Données initialisées. Super admin: admin / admin123 (id={})", superAdmin.getId());
    }

    private void createClient(Agence agence, Marche marche, Quartier quartier, Agent agent,
                              String nom, String tel, String montant) {
        long seq = clientRepository.countByMarcheId(marche.getId()) + 1;
        String marketCode = marche.getCode() != null ? marche.getCode() : MarcheCodeInitializer.generateCode(marche.getNom());
        String code = "TM-" + marketCode + "-" + String.format("%05d", seq);
        clientRepository.save(Client.builder()
                .code(code)
                .nomComplet(nom)
                .telephone(tel.replaceAll("\\D", "").substring(Math.max(0, tel.replaceAll("\\D", "").length() - 8)))
                .profession("Commerçant")
                .adresse(quartier.getNom())
                .agence(agence)
                .marche(marche)
                .quartier(quartier)
                .agent(agent)
                .montantJournalier(new BigDecimal(montant))
                .fraisAdhesion(new BigDecimal("500"))
                .dateAdhesion(LocalDate.now().minusDays(30))
                .soldeEpargne(BigDecimal.ZERO)
                .statut(StatutEntity.ACTIF)
                .build());
    }
}
