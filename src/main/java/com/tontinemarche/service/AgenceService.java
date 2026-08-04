package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.AgenceDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.ClientRepository;
import com.tontinemarche.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgenceService {

    private final AgenceRepository agenceRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    private final CommissionGrilleService commissionGrilleService;
    private final PlatformSettingsService platformSettingsService;
    private final SmsGatewayService smsGatewayService;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AgenceDto> findAll() {
        return agenceRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AgenceDto findById(Long id) {
        assertCanAccessAgence(id);
        return toDto(getEntity(id));
    }

    @Transactional
    public AgenceDto create(AgenceDto dto) {
        String code = dto.getCode() != null && !dto.getCode().isBlank()
                ? dto.getCode().toUpperCase()
                : generateCode(dto.getVille() != null ? dto.getVille() : dto.getNom());

        if (agenceRepository.existsByCode(code)) {
            throw ApiException.conflict("Code agence déjà utilisé: " + code);
        }

        Agence agence = Agence.builder()
                .code(code)
                .nom(dto.getNom())
                .responsable(dto.getResponsable())
                .telephone(dto.getTelephone())
                .email(dto.getEmail())
                .adresse(dto.getAdresse())
                .ville(dto.getVille())
                .logoUrl(trimOrNull(dto.getLogoUrl()))
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .tauxCommission(dto.getTauxCommission() != null ? dto.getTauxCommission() : new BigDecimal("0.1000"))
                .tauxCommissionAdmin(dto.getTauxCommissionAdmin() != null
                        ? dto.getTauxCommissionAdmin()
                        : platformSettingsService.getTauxCommissionAdminDefaut())
                .statut(StatutEntity.ACTIF)
                .build();

        agence = agenceRepository.save(agence);
        commissionGrilleService.ensureDefaultGrille(agence);
        auditService.log("CREATION", "Agence", agence.getCode(), agence.getNom(), agence.getId());
        return toDto(agence);
    }

    @Transactional
    public AgenceDto update(Long id, AgenceDto dto) {
        UserPrincipal principal = requirePrincipal();
        assertCanAccessAgence(id);
        Agence agence = getEntity(id);

        agence.setNom(dto.getNom());
        agence.setResponsable(dto.getResponsable());
        agence.setTelephone(dto.getTelephone());
        agence.setEmail(dto.getEmail());
        agence.setAdresse(dto.getAdresse());
        agence.setVille(dto.getVille());
        agence.setLatitude(dto.getLatitude());
        agence.setLongitude(dto.getLongitude());
        if (dto.getLogoUrl() != null) {
            agence.setLogoUrl(trimOrNull(dto.getLogoUrl()));
        }

        if (dto.getSmsPourTousClients() != null) {
            applySmsPourTousClients(agence, dto.getSmsPourTousClients());
        }

        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            if (dto.getTauxCommission() != null) {
                agence.setTauxCommission(dto.getTauxCommission());
            }
            if (dto.getTauxCommissionAdmin() != null) {
                agence.setTauxCommissionAdmin(dto.getTauxCommissionAdmin());
            }
            if (dto.getStatut() != null) {
                agence.setStatut(dto.getStatut());
            }
        }

        auditService.log("MODIFICATION", "Agence", agence.getCode(), agence.getNom(), agence.getId());
        return toDto(agenceRepository.save(agence));
    }

    @Transactional
    public AgenceDto desactiver(Long id) {
        Agence agence = getEntity(id);
        agence.setStatut(StatutEntity.INACTIF);
        auditService.log("DESACTIVATION", "Agence", agence.getCode(), null, agence.getId());
        return toDto(agenceRepository.save(agence));
    }

    /**
     * Active ou désactive les SMS pour tous les clients de l'agence
     * (flag agence + bascule individuelle des clients pour cohérence UI).
     */
    @Transactional
    public AgenceDto setSmsPourTousClients(Long id, boolean enabled) {
        assertCanAccessAgence(id);
        Agence agence = getEntity(id);
        applySmsPourTousClients(agence, enabled);
        auditService.log(enabled ? "SMS_AGENCE_ON" : "SMS_AGENCE_OFF",
                "Agence", agence.getCode(), null, agence.getId());
        return toDto(agenceRepository.save(agence));
    }

    private void applySmsPourTousClients(Agence agence, boolean enabled) {
        if (enabled) {
            if (!platformSettingsService.isSmsNotificationsEnabled()) {
                throw ApiException.badRequest(
                        "Les notifications SMS ne sont pas activées au niveau plateforme");
            }
            if (!smsGatewayService.isReady()) {
                throw ApiException.badRequest(
                        "La passerelle SMS n'est pas configurée (SMS_GATEWAY_ENABLED / SMS_GATEWAY_API_KEY)");
            }
        }
        agence.setSmsPourTousClients(enabled);
        if (enabled) {
            clientRepository.updateSmsEnabledByAgenceId(agence.getId(), true);
        }
    }

    private AgenceDto toDto(Agence agence) {
        AgenceDto dto = EntityMapper.toDto(agence);
        dto.setSmsPlateformeActive(platformSettingsService.isSmsNotificationsEnabled());
        dto.setSmsGatewayReady(smsGatewayService.isReady());
        return dto;
    }

    /**
     * Suppression définitive d'une agence et de toutes ses données liées (cascade).
     * Réservé au SUPER_ADMIN.
     */
    @Transactional
    public void supprimerCompletement(Long id) {
        UserPrincipal principal = requirePrincipal();
        if (principal.getRole() != RoleType.SUPER_ADMIN) {
            throw ApiException.forbidden("Seul un super administrateur peut supprimer une agence");
        }

        Agence agence = getEntity(id);
        String code = agence.getCode();
        String nom = agence.getNom();

        // Couper le suivi JPA pour éviter les conflits avec les DELETE JDBC
        entityManager.clear();

        // --- 1. Caisse & mouvements ---
        jdbcTemplate.update("""
                DELETE FROM mouvements_caisse
                WHERE caisse_id IN (SELECT id FROM caisses WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                UPDATE caisses SET ouvert_par_id = NULL, cloture_par_id = NULL
                WHERE agence_id = ?
                """, id);
        jdbcTemplate.update("DELETE FROM caisses WHERE agence_id = ?", id);

        // --- 2. Opérations métier (avant clients / agents / users) ---
        jdbcTemplate.update("""
                UPDATE depenses SET agent_id = NULL, client_id = NULL, valide_par_id = NULL
                WHERE agence_id = ?
                """, id);
        jdbcTemplate.update("DELETE FROM depenses WHERE agence_id = ?", id);
        jdbcTemplate.update("DELETE FROM collectes WHERE agence_id = ?", id);
        jdbcTemplate.update("""
                UPDATE restitutions SET caissier_id = NULL WHERE agence_id = ?
                """, id);
        jdbcTemplate.update("DELETE FROM restitutions WHERE agence_id = ?", id);

        // --- 3. Clients, historiques, affectations ---
        jdbcTemplate.update("""
                DELETE FROM affectations_clients
                WHERE client_id IN (SELECT id FROM clients WHERE agence_id = ?)
                   OR agent_source_id IN (SELECT id FROM agents WHERE agence_id = ?)
                   OR agent_cible_id IN (SELECT id FROM agents WHERE agence_id = ?)
                """, id, id, id);
        jdbcTemplate.update("""
                DELETE FROM client_historique
                WHERE client_id IN (SELECT id FROM clients WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                UPDATE clients SET agent_id = NULL, marche_id = NULL WHERE agence_id = ?
                """, id);
        jdbcTemplate.update("DELETE FROM clients WHERE agence_id = ?", id);

        // --- 4. Agents ↔ marchés ---
        jdbcTemplate.update("""
                DELETE FROM agent_marches
                WHERE agent_id IN (SELECT id FROM agents WHERE agence_id = ?)
                   OR marche_id IN (SELECT id FROM marches WHERE agence_id = ?)
                """, id, id);
        jdbcTemplate.update("UPDATE agents SET utilisateur_id = NULL WHERE agence_id = ?", id);
        jdbcTemplate.update("DELETE FROM agents WHERE agence_id = ?", id);

        // --- 5. Comptes utilisateurs de l'agence ---
        jdbcTemplate.update("""
                DELETE FROM notifications
                WHERE utilisateur_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                DELETE FROM refresh_tokens
                WHERE utilisateur_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                DELETE FROM password_reset_otps
                WHERE utilisateur_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        // Sécurité : détacher FKs restantes éventuelles vers ces utilisateurs
        jdbcTemplate.update("""
                UPDATE caisses SET ouvert_par_id = NULL
                WHERE ouvert_par_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                UPDATE caisses SET cloture_par_id = NULL
                WHERE cloture_par_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                UPDATE mouvements_caisse SET effectue_par_id = NULL
                WHERE effectue_par_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                UPDATE depenses SET valide_par_id = NULL
                WHERE valide_par_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("""
                UPDATE restitutions SET caissier_id = NULL
                WHERE caissier_id IN (SELECT id FROM utilisateurs WHERE agence_id = ?)
                """, id);
        jdbcTemplate.update("DELETE FROM utilisateurs WHERE agence_id = ?", id);

        // --- 6. Référentiels locaux ---
        jdbcTemplate.update("DELETE FROM marches WHERE agence_id = ?", id);
        jdbcTemplate.update("DELETE FROM quartiers WHERE agence_id = ?", id);
        jdbcTemplate.update("DELETE FROM grille_commission_lignes WHERE agence_id = ?", id);
        jdbcTemplate.update("DELETE FROM agence_categorie_desactivations WHERE agence_id = ?", id);

        // --- 7. Demandes d'inscription ayant créé cette agence + OTP liés ---
        jdbcTemplate.update("""
                DELETE FROM inscription_agence_otps
                WHERE LOWER(email) IN (
                    SELECT LOWER(email) FROM demandes_inscription_agence WHERE agence_creee_id = ?
                )
                """, id);
        jdbcTemplate.update("DELETE FROM demandes_inscription_agence WHERE agence_creee_id = ?", id);

        // --- 8. Journal d'audit de l'agence ---
        jdbcTemplate.update("DELETE FROM audit_logs WHERE agence_id = ?", id);

        // --- 9. Agence ---
        int deleted = jdbcTemplate.update("DELETE FROM agences WHERE id = ?", id);
        if (deleted == 0) {
            throw ApiException.notFound("Agence introuvable: " + id);
        }

        entityManager.clear();
        auditService.log("SUPPRESSION_AGENCE_CASCADE", "Agence", code,
                "Suppression cascade de l'agence « " + nom + " » et de toutes ses données", null);
    }

    public Agence getEntity(Long id) {
        return agenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable: " + id));
    }

    private String generateCode(String source) {
        String prefix = source.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (prefix.length() < 3) {
            prefix = (prefix + "XXX").substring(0, 3);
        } else {
            prefix = prefix.substring(0, 3);
        }
        int i = 1;
        String code = prefix;
        while (agenceRepository.existsByCode(code)) {
            code = prefix + i++;
        }
        return code;
    }

    private void assertCanAccessAgence(Long agenceId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null || !principal.getAgenceId().equals(agenceId)) {
                throw ApiException.forbidden("Accès limité à votre agence");
            }
            return;
        }
    }

    private UserPrincipal requirePrincipal() {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        return principal;
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
