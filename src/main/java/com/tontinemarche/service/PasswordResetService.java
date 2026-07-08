package com.tontinemarche.service;

import com.tontinemarche.domain.entity.PasswordResetOtp;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.auth.OtpResponse;
import com.tontinemarche.dto.auth.ResetPasswordRequest;
import com.tontinemarche.dto.auth.VerifyOtpRequest;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.PasswordResetOtpRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.otp.expiration-minutes:10}")
    private int otpExpirationMinutes;

    @Value("${app.otp.reset-token-minutes:15}")
    private int resetTokenMinutes;

    @Transactional
    public OtpResponse requestOtp(String username) {
        Utilisateur user = findActiveUser(username);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw ApiException.badRequest("Aucune adresse email associée à ce compte. Contactez votre administrateur.");
        }

        otpRepository.invalidateAllForUser(user.getId());

        String otp = generateOtp();
        PasswordResetOtp entity = PasswordResetOtp.builder()
                .utilisateur(user)
                .otpHash(passwordEncoder.encode(otp))
                .expiryDate(Instant.now().plusSeconds(otpExpirationMinutes * 60L))
                .verified(false)
                .used(false)
                .build();
        otpRepository.save(entity);

        emailService.send(
                user.getEmail(),
                "Tontine Marché — Code de réinitialisation",
                buildOtpEmail(user.getNomComplet(), otp, otpExpirationMinutes)
        );

        return OtpResponse.builder()
                .message("Un code OTP a été envoyé à votre adresse email.")
                .maskedEmail(maskEmail(user.getEmail()))
                .expiresInSeconds(otpExpirationMinutes * 60)
                .step(1)
                .build();
    }

    @Transactional
    public OtpResponse verifyOtp(VerifyOtpRequest request) {
        Utilisateur user = findActiveUser(request.getUsername());
        PasswordResetOtp otp = otpRepository.findTopByUtilisateurIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> ApiException.badRequest("Aucune demande de réinitialisation en cours. Demandez un nouveau code."));

        if (otp.getExpiryDate().isBefore(Instant.now())) {
            otp.setUsed(true);
            otpRepository.save(otp);
            throw ApiException.badRequest("Code OTP expiré. Demandez un nouveau code.");
        }

        if (!passwordEncoder.matches(request.getOtp(), otp.getOtpHash())) {
            throw ApiException.badRequest("Code OTP incorrect.");
        }

        String resetToken = UUID.randomUUID().toString();
        otp.setVerified(true);
        otp.setResetToken(resetToken);
        otp.setResetTokenExpiry(Instant.now().plusSeconds(resetTokenMinutes * 60L));
        otpRepository.save(otp);

        return OtpResponse.builder()
                .message("Code vérifié. Définissez votre nouveau mot de passe.")
                .step(2)
                .resetToken(resetToken)
                .build();
    }

    @Transactional
    public OtpResponse resetPassword(ResetPasswordRequest request) {
        Utilisateur user = findActiveUser(request.getUsername());
        PasswordResetOtp otp = otpRepository.findTopByUtilisateurIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> ApiException.badRequest("Session de réinitialisation invalide."));

        if (!otp.isVerified() || otp.getResetToken() == null
                || !otp.getResetToken().equals(request.getResetToken())) {
            throw ApiException.badRequest("Session de réinitialisation invalide.");
        }

        if (otp.getResetTokenExpiry() == null || otp.getResetTokenExpiry().isBefore(Instant.now())) {
            otp.setUsed(true);
            otpRepository.save(otp);
            throw ApiException.badRequest("Session expirée. Recommencez la procédure.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        utilisateurRepository.save(user);

        otp.setUsed(true);
        otpRepository.save(otp);

        return OtpResponse.builder()
                .message("Mot de passe mis à jour avec succès. Vous pouvez vous connecter.")
                .step(3)
                .build();
    }

    private Utilisateur findActiveUser(String username) {
        Utilisateur user = utilisateurRepository.findByUsername(username.trim())
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        if (user.getStatut() != StatutEntity.ACTIF) {
            throw ApiException.badRequest("Ce compte est inactif. Contactez votre administrateur.");
        }
        return user;
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***@" + email.substring(at + 1);
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String buildOtpEmail(String name, String otp, int minutes) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px">
                  <h2 style="color:#1a5632">Réinitialisation du mot de passe</h2>
                  <p>Bonjour %s,</p>
                  <p>Voici votre code de vérification Tontine Marché :</p>
                  <p style="font-size:28px;font-weight:bold;letter-spacing:6px;color:#0b1f3a">%s</p>
                  <p>Ce code expire dans <strong>%d minutes</strong>.</p>
                  <p style="color:#64748b;font-size:13px">Si vous n'avez pas demandé cette réinitialisation, ignorez cet email.</p>
                </div>
                """.formatted(name, otp, minutes);
    }
}
