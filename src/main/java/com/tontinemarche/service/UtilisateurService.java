package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.CreateUtilisateurRequest;
import com.tontinemarche.dto.UpdateUtilisateurRequest;
import com.tontinemarche.dto.UtilisateurDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.security.UserPrincipal;
import com.tontinemarche.util.PhotoUrlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private static final Set<RoleType> ADMIN_AGENCE_ASSIGNABLE = EnumSet.of(
            RoleType.ADMIN_AGENCE, RoleType.AGENT, RoleType.CAISSIER, RoleType.AUDITEUR
    );

    private final UtilisateurRepository utilisateurRepository;
    private final AgenceRepository agenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AgentService agentService;

    @Transactional(readOnly = true)
    public List<UtilisateurDto> findAll(Long agenceId, RoleType role) {
        UserPrincipal principal = requirePrincipal();
        Long scopedAgenceId = resolveListAgenceScope(principal, agenceId);

        List<Utilisateur> users = scopedAgenceId != null
                ? utilisateurRepository.findByAgenceIdOrderByNomCompletAsc(scopedAgenceId)
                : utilisateurRepository.findAllByOrderByNomCompletAsc();

        return users.stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> principal.getRole() != RoleType.ADMIN_AGENCE || u.getRole() != RoleType.SUPER_ADMIN)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UtilisateurDto findById(Long id) {
        Utilisateur user = getEntity(id);
        assertCanManageUser(user);
        return toDto(user);
    }

    @Transactional
    public UtilisateurDto create(CreateUtilisateurRequest request) {
        UserPrincipal principal = requirePrincipal();
        assertCanManageUsers(principal);

        if (utilisateurRepository.existsByUsername(request.getUsername().trim())) {
            throw ApiException.conflict("Nom d'utilisateur déjà utilisé");
        }

        RoleType role = request.getRole();
        assertRoleAssignable(principal, role);

        Long agenceId = resolveAgenceIdForWrite(principal, role, request.getAgenceId());
        Agence agence = resolveAgence(role, agenceId);

        Utilisateur user = utilisateurRepository.save(Utilisateur.builder()
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .nomComplet(request.getNomComplet().trim())
                .email(trimOrNull(request.getEmail()))
                .telephone(trimOrNull(request.getTelephone()))
                .photoUrl(PhotoUrlSanitizer.sanitize(request.getPhotoUrl()))
                .role(role)
                .agence(agence)
                .statut(StatutEntity.ACTIF)
                .build());

        if (role == RoleType.ADMIN_AGENCE) {
            agentService.ensureCollecteurProfile(user);
        }

        auditService.log("CREATION", "Utilisateur", user.getUsername(), user.getRole().name(), agenceId(user));
        return toDto(user);
    }

    @Transactional
    public UtilisateurDto update(Long id, UpdateUtilisateurRequest request) {
        UserPrincipal principal = requirePrincipal();
        assertCanManageUsers(principal);

        Utilisateur user = getEntity(id);
        assertCanManageUser(user);
        protectPrivilegedAccount(user, principal);
        assertRoleAssignable(principal, request.getRole());

        if (principal.getRole() == RoleType.ADMIN_AGENCE && principal.getId().equals(user.getId())) {
            if (request.getRole() != RoleType.ADMIN_AGENCE) {
                throw ApiException.badRequest("Vous ne pouvez pas modifier votre propre rôle");
            }
            if (request.getStatut() != StatutEntity.ACTIF) {
                throw ApiException.badRequest("Vous ne pouvez pas désactiver votre propre compte");
            }
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user.setNomComplet(request.getNomComplet().trim());
        user.setEmail(trimOrNull(request.getEmail()));
        user.setTelephone(trimOrNull(request.getTelephone()));
        user.setPhotoUrl(PhotoUrlSanitizer.sanitize(request.getPhotoUrl()));
        user.setRole(request.getRole());
        user.setStatut(request.getStatut());

        Long agenceId = resolveAgenceIdForWrite(principal, request.getRole(), request.getAgenceId());
        user.setAgence(resolveAgence(request.getRole(), agenceId));

        auditService.log("MODIFICATION", "Utilisateur", user.getUsername(), user.getRole().name(), agenceId(user));
        return toDto(utilisateurRepository.save(user));
    }

    @Transactional
    public UtilisateurDto desactiver(Long id) {
        UserPrincipal principal = requirePrincipal();
        assertCanManageUsers(principal);

        Utilisateur user = getEntity(id);
        assertCanManageUser(user);
        protectPrivilegedAccount(user, principal);

        if (principal.getId().equals(user.getId())) {
            throw ApiException.badRequest("Vous ne pouvez pas désactiver votre propre compte");
        }

        user.setStatut(StatutEntity.INACTIF);
        auditService.log("DESACTIVATION", "Utilisateur", user.getUsername(), null, agenceId(user));
        return toDto(utilisateurRepository.save(user));
    }

    public Map<String, Object> permissions() {
        UserPrincipal principal = requirePrincipal();
        List<Map<String, Object>> roles = List.of(
                rolePerm("SUPER_ADMIN", "Super administrateur", List.of(
                        "Gestion globale des agences", "Gestion des utilisateurs et rôles",
                        "Vue dashboard globale", "CMS site web", "Audit complet"
                )),
                rolePerm("ADMIN_AGENCE", "Administrateur agence", List.of(
                        "Gestion de son agence", "Gestion des utilisateurs de l'agence",
                        "Gestion agents et marchés", "Clients et collectes",
                        "Restitutions, dépenses, caisse", "Grille de commission"
                )),
                rolePerm("AGENT", "Agent collecteur", List.of(
                        "Ses clients et marchés assignés", "Enregistrement des collectes",
                        "Dashboard personnel", "Modification de son profil"
                )),
                rolePerm("CAISSIER", "Caissier", List.of(
                        "Restitutions et dépenses", "Gestion caisse du jour",
                        "Dashboard agence", "Consultation clients"
                )),
                rolePerm("AUDITEUR", "Auditeur", List.of(
                        "Consultation dashboard agence", "Journal d'audit", "Rapports en lecture seule"
                ))
        );

        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            roles = roles.stream()
                    .filter(r -> !"SUPER_ADMIN".equals(r.get("code")))
                    .toList();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", roles);
        result.put("assignableRoles", assignableRoles(principal).stream().map(RoleType::name).toList());
        return result;
    }

    public List<RoleType> assignableRoles(UserPrincipal principal) {
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return List.of(RoleType.values());
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            return ADMIN_AGENCE_ASSIGNABLE.stream().toList();
        }
        return List.of();
    }

    private Map<String, Object> rolePerm(String code, String label, List<String> permissions) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("permissions", permissions);
        return m;
    }

    private Long resolveListAgenceScope(UserPrincipal principal, Long requestedAgenceId) {
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return requestedAgenceId;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (requestedAgenceId != null && !requestedAgenceId.equals(principal.getAgenceId())) {
                throw ApiException.forbidden("Accès limité aux utilisateurs de votre agence");
            }
            return principal.getAgenceId();
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private Long resolveAgenceIdForWrite(UserPrincipal principal, RoleType role, Long requestedAgenceId) {
        if (role == RoleType.SUPER_ADMIN) {
            return null;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null) {
                throw ApiException.forbidden("Agence non définie");
            }
            if (requestedAgenceId != null && !requestedAgenceId.equals(principal.getAgenceId())) {
                throw ApiException.forbidden("Vous ne pouvez gérer que les utilisateurs de votre agence");
            }
            return principal.getAgenceId();
        }
        return requestedAgenceId;
    }

    private void assertCanManageUsers(UserPrincipal principal) {
        if (principal.getRole() != RoleType.SUPER_ADMIN && principal.getRole() != RoleType.ADMIN_AGENCE) {
            throw ApiException.forbidden("Accès refusé");
        }
    }

    private void assertCanManageUser(Utilisateur user) {
        UserPrincipal principal = requirePrincipal();
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (user.getRole() == RoleType.SUPER_ADMIN) {
                throw ApiException.forbidden("Accès refusé");
            }
            if (user.getAgence() == null || !user.getAgence().getId().equals(principal.getAgenceId())) {
                throw ApiException.forbidden("Utilisateur hors de votre agence");
            }
            return;
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private void assertRoleAssignable(UserPrincipal principal, RoleType role) {
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE && !ADMIN_AGENCE_ASSIGNABLE.contains(role)) {
            throw ApiException.forbidden("Rôle non autorisé pour un administrateur d'agence");
        }
    }

    private void protectPrivilegedAccount(Utilisateur user, UserPrincipal principal) {
        if (user.getRole() == RoleType.SUPER_ADMIN && principal.getRole() != RoleType.SUPER_ADMIN) {
            throw ApiException.forbidden("Accès refusé");
        }
    }

    private Agence resolveAgence(RoleType role, Long agenceId) {
        if (role == RoleType.SUPER_ADMIN) {
            return null;
        }
        if (agenceId == null) {
            throw ApiException.badRequest("L'agence est obligatoire pour le rôle " + role.name());
        }
        return agenceRepository.findById(agenceId)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable"));
    }

    private Utilisateur getEntity(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }

    private UtilisateurDto toDto(Utilisateur user) {
        return UtilisateurDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nomComplet(user.getNomComplet())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .photoUrl(user.getPhotoUrl())
                .role(user.getRole())
                .agenceId(user.getAgence() != null ? user.getAgence().getId() : null)
                .agenceNom(user.getAgence() != null ? user.getAgence().getNom() : null)
                .statut(user.getStatut())
                .build();
    }

    private Long agenceId(Utilisateur user) {
        return user.getAgence() != null ? user.getAgence().getId() : null;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
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
}
