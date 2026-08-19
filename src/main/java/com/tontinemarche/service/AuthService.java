package com.tontinemarche.service;

import com.tontinemarche.domain.entity.RefreshToken;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.dto.auth.AuthResponse;
import com.tontinemarche.dto.auth.LoginRequest;
import com.tontinemarche.dto.auth.RefreshTokenRequest;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.RefreshTokenRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.security.JwtService;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final AgentService agentService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername() != null ? request.getUsername().trim() : "",
                            request.getPassword())
            );
        } catch (DisabledException | LockedException ex) {
            throw ApiException.unauthorized("Compte désactivé");
        } catch (AuthenticationException ex) {
            throw ApiException.unauthorized("Identifiants incorrects");
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Utilisateur user = utilisateurRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));

        if (user.getRole() == RoleType.ADMIN_AGENCE) {
            agentService.ensureCollecteurProfile(user);
        }

        String accessToken = jwtService.generateAccessToken(principal);
        String tokenValue = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusMillis(jwtService.getRefreshTokenExpiration());

        RefreshToken refreshToken = refreshTokenRepository.findByUtilisateurId(user.getId())
                .map(existing -> {
                    existing.setToken(tokenValue);
                    existing.setExpiryDate(expiry);
                    return existing;
                })
                .orElseGet(() -> RefreshToken.builder()
                        .token(tokenValue)
                        .utilisateur(user)
                        .expiryDate(expiry)
                        .build());
        refreshToken = refreshTokenRepository.save(refreshToken);

        auditService.log("CONNEXION", "Utilisateur", user.getUsername(), "Connexion réussie",
                user.getAgence() != null ? user.getAgence().getId() : null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .nomComplet(user.getNomComplet())
                .role(user.getRole())
                .agenceId(user.getAgence() != null ? user.getAgence().getId() : null)
                .agenceNom(user.getAgence() != null ? user.getAgence().getNom() : null)
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> ApiException.badRequest("Refresh token invalide"));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw ApiException.badRequest("Refresh token expiré");
        }

        Utilisateur user = token.getUtilisateur();
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(token.getToken())
                .tokenType("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .nomComplet(user.getNomComplet())
                .role(user.getRole())
                .agenceId(user.getAgence() != null ? user.getAgence().getId() : null)
                .agenceNom(user.getAgence() != null ? user.getAgence().getNom() : null)
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }
}
