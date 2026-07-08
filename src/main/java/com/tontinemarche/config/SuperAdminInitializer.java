package com.tontinemarche.config;

import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée automatiquement le super administrateur au démarrage s'il n'existe pas.
 * Idempotent : si un utilisateur portant le même identifiant existe déjà, rien n'est fait.
 * Les valeurs sont surchargeables par variables d'environnement
 * (SUPER_ADMIN_USERNAME, SUPER_ADMIN_PASSWORD, SUPER_ADMIN_EMAIL, SUPER_ADMIN_NAME).
 * S'exécute avant {@link DataInitializer}.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class SuperAdminInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username:admin}")
    private String username;

    @Value("${app.super-admin.password:*@Secret@*2018}")
    private String password;

    @Value("${app.super-admin.email:dmoussa1807@gmail.com}")
    private String email;

    @Value("${app.super-admin.nom-complet:Super Administrateur}")
    private String nomComplet;

    @Override
    @Transactional
    public void run(String... args) {
        if (utilisateurRepository.findByUsername(username).isPresent()) {
            log.info("Super administrateur '{}' déjà présent : création ignorée.", username);
            return;
        }

        Utilisateur superAdmin = utilisateurRepository.save(Utilisateur.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .nomComplet(nomComplet)
                .email(email)
                .role(RoleType.SUPER_ADMIN)
                .statut(StatutEntity.ACTIF)
                .mustChangePassword(true)
                .build());

        log.info("Super administrateur '{}' créé automatiquement (id={}).", username, superAdmin.getId());
    }
}
