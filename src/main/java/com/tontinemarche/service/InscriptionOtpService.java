package com.tontinemarche.service;

import com.tontinemarche.domain.entity.InscriptionAgenceOtp;
import com.tontinemarche.dto.InscriptionOtpRequest;
import com.tontinemarche.dto.InscriptionVerifyOtpRequest;
import com.tontinemarche.dto.auth.OtpResponse;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.InscriptionAgenceOtpRepository;
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
public class InscriptionOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InscriptionAgenceOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SmsGatewayService smsGatewayService;

    @Value("${app.otp.expiration-minutes:10}")
    private int otpExpirationMinutes;

    @Value("${app.otp.reset-token-minutes:30}")
    private int verificationTokenMinutes;

    @Transactional
    public OtpResponse envoyerOtp(InscriptionOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        otpRepository.invalidateAllForEmail(email);

        String otp = generateOtp();
        InscriptionAgenceOtp entity = InscriptionAgenceOtp.builder()
                .email(email)
                .otpHash(passwordEncoder.encode(otp))
                .expiryDate(Instant.now().plusSeconds(otpExpirationMinutes * 60L))
                .verified(false)
                .used(false)
                .build();
        otpRepository.save(entity);

        String name = request.getNomComplet() != null && !request.getNomComplet().isBlank()
                ? request.getNomComplet().trim() : "Futur partenaire";

        emailService.send(
                email,
                "Tontine Marché — Vérification de votre e-mail",
                buildOtpEmail(name, otp, otpExpirationMinutes)
        );

        boolean smsSent = false;
        String maskedPhone = null;
        String phone = request.getTelephone();
        if (phone != null && !phone.isBlank() && smsGatewayService.isReady()) {
            String e164 = smsGatewayService.normalizePhoneNumber(phone);
            if (e164 != null) {
                smsSent = smsGatewayService.sendSms(e164, buildOtpSms(otp, otpExpirationMinutes));
                if (smsSent) {
                    maskedPhone = maskPhone(e164);
                }
            }
        }

        String message = smsSent
                ? "Un code OTP a été envoyé par e-mail et par SMS."
                : "Un code OTP a été envoyé à votre adresse e-mail.";

        return OtpResponse.builder()
                .message(message)
                .maskedEmail(maskEmail(email))
                .maskedPhone(maskedPhone)
                .smsSent(smsSent)
                .expiresInSeconds(otpExpirationMinutes * 60)
                .step(1)
                .build();
    }

    @Transactional
    public OtpResponse verifierOtp(InscriptionVerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        InscriptionAgenceOtp otp = otpRepository.findTopByEmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> ApiException.badRequest("Aucune demande OTP en cours. Renvoyez un code."));

        if (otp.getExpiryDate().isBefore(Instant.now())) {
            otp.setUsed(true);
            otpRepository.save(otp);
            throw ApiException.badRequest("Code OTP expiré. Demandez un nouveau code.");
        }

        if (!passwordEncoder.matches(request.getOtp(), otp.getOtpHash())) {
            throw ApiException.badRequest("Code OTP incorrect.");
        }

        String verificationToken = UUID.randomUUID().toString();
        otp.setVerified(true);
        otp.setVerificationToken(verificationToken);
        otp.setTokenExpiry(Instant.now().plusSeconds(verificationTokenMinutes * 60L));
        otpRepository.save(otp);

        return OtpResponse.builder()
                .message("E-mail vérifié. Vous pouvez soumettre votre demande.")
                .step(2)
                .resetToken(verificationToken)
                .build();
    }

    @Transactional(readOnly = true)
    public void assertVerificationToken(String email, String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) {
            throw ApiException.badRequest("La vérification e-mail est obligatoire");
        }
        String normalizedEmail = email.trim().toLowerCase();
        InscriptionAgenceOtp otp = otpRepository.findByVerificationTokenAndUsedFalse(verificationToken)
                .orElseThrow(() -> ApiException.badRequest("Jeton de vérification invalide"));

        if (!otp.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw ApiException.badRequest("L'e-mail ne correspond pas à la vérification OTP");
        }
        if (!otp.isVerified() || otp.getTokenExpiry() == null || otp.getTokenExpiry().isBefore(Instant.now())) {
            throw ApiException.badRequest("La vérification e-mail a expiré. Renvoyez un code OTP.");
        }
    }

    @Transactional
    public void consumeVerificationToken(String verificationToken) {
        otpRepository.findByVerificationTokenAndUsedFalse(verificationToken).ifPresent(otp -> {
            otp.setUsed(true);
            otpRepository.save(otp);
        });
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***@" + email.substring(at + 1);
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }

    private String buildOtpSms(String otp, int minutes) {
        return "Tontine Marche: votre code de verification est " + otp
                + ". Valide " + minutes + " minutes.";
    }

    private String buildOtpEmail(String name, String otp, int minutes) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px">
                  <h2 style="color:#1a5632">Vérification e-mail — Inscription collecteur</h2>
                  <p>Bonjour %s,</p>
                  <p>Voici votre code de vérification Tontine Marché :</p>
                  <p style="font-size:28px;font-weight:bold;letter-spacing:6px;color:#0b1f3a">%s</p>
                  <p>Ce code expire dans <strong>%d minutes</strong>.</p>
                  <p style="color:#64748b;font-size:13px">Si vous n'avez pas demandé cette inscription, ignorez cet email.</p>
                </div>
                """.formatted(name, otp, minutes);
    }
}
