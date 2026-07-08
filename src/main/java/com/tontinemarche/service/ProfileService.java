package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.dto.auth.ChangePasswordRequest;
import com.tontinemarche.dto.auth.UpdateProfileRequest;
import com.tontinemarche.dto.auth.UserProfileDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.AgentRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.security.UserPrincipal;
import com.tontinemarche.util.PhotoUrlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UtilisateurRepository utilisateurRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserProfileDto getProfile() {
        return toDto(currentUser());
    }

    @Transactional
    public UserProfileDto updateProfile(UpdateProfileRequest request) {
        Utilisateur user = currentUser();
        user.setNomComplet(request.getNomComplet().trim());
        user.setEmail(trimOrNull(request.getEmail()));
        user.setTelephone(trimOrNull(request.getTelephone()));
        user.setPhotoUrl(PhotoUrlSanitizer.sanitize(request.getPhotoUrl()));
        utilisateurRepository.save(user);
        syncLinkedAgent(user);

        auditService.log("MODIFICATION", "Profil", user.getUsername(), "Mise à jour du profil",
                user.getAgence() != null ? user.getAgence().getId() : null);

        return toDto(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw ApiException.badRequest("Les mots de passe ne correspondent pas");
        }

        Utilisateur user = currentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Mot de passe actuel incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw ApiException.badRequest("Le nouveau mot de passe doit être différent de l'actuel");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        utilisateurRepository.save(user);

        auditService.log("MODIFICATION", "Profil", user.getUsername(), "Changement de mot de passe",
                user.getAgence() != null ? user.getAgence().getId() : null);
    }

    private void syncLinkedAgent(Utilisateur user) {
        if (user.getRole() != RoleType.AGENT) {
            return;
        }
        agentRepository.findByUtilisateurId(user.getId()).ifPresent(agent -> {
            agent.setNomComplet(user.getNomComplet());
            agent.setTelephone(user.getTelephone());
            agent.setPhotoUrl(user.getPhotoUrl());
            agentRepository.save(agent);
        });
    }

    private Utilisateur currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw ApiException.forbidden("Non authentifié");
        }
        return utilisateurRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }

    private UserProfileDto toDto(Utilisateur user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nomComplet(user.getNomComplet())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .photoUrl(user.getPhotoUrl())
                .role(user.getRole())
                .agenceId(user.getAgence() != null ? user.getAgence().getId() : null)
                .agenceNom(user.getAgence() != null ? user.getAgence().getNom() : null)
                .build();
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
