package com.tontinemarche.service;

import com.tontinemarche.domain.entity.DemandeInscriptionAgence;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutDemandeInscription;
import com.tontinemarche.dto.InscriptionCollecteurRequest;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.DemandeInscriptionAgenceRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InscriptionCollecteurService {

    private final DemandeInscriptionAgenceRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final InscriptionOtpService inscriptionOtpService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public Map<String, Object> soumettre(InscriptionCollecteurRequest request) {
        if (!request.isAccepteConditions()) {
            throw ApiException.badRequest("Vous devez accepter les conditions d'utilisation");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw ApiException.badRequest("Les mots de passe ne correspondent pas");
        }
        String email = request.getEmail().trim().toLowerCase();
        String username = request.getUsername().trim();

        inscriptionOtpService.assertVerificationToken(email, request.getVerificationToken());

        if (utilisateurRepository.existsByUsername(username)) {
            throw ApiException.conflict("Cet identifiant est déjà utilisé");
        }
        if (demandeRepository.existsByUsernameIgnoreCaseAndStatut(username, StatutDemandeInscription.EN_ATTENTE)) {
            throw ApiException.conflict("Une demande est déjà en cours pour cet identifiant");
        }
        if (demandeRepository.existsByEmailIgnoreCaseAndStatut(email, StatutDemandeInscription.EN_ATTENTE)) {
            throw ApiException.conflict("Une demande est déjà en cours pour cet e-mail");
        }

        DemandeInscriptionAgence demande = DemandeInscriptionAgence.builder()
                .agenceNom(request.getAgenceNom().trim())
                .responsable(trimOrNull(request.getResponsable()))
                .agenceTelephone(trimOrNull(request.getAgenceTelephone()))
                .agenceEmail(trimOrNull(request.getAgenceEmail()))
                .adresse(trimOrNull(request.getAdresse()))
                .ville(trimOrNull(request.getVille()))
                .logoUrl(trimOrNull(request.getLogoUrl()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nomComplet(request.getNomComplet().trim())
                .email(email)
                .telephone(trimOrNull(request.getTelephone()))
                .pieceIdentiteUrl(request.getPieceIdentiteUrl().trim())
                .moyenPaiement(request.getMoyenPaiement())
                .referencePaiement(request.getReferencePaiement().trim())
                .accepteConditions(true)
                .statut(StatutDemandeInscription.EN_ATTENTE)
                .build();

        demande = demandeRepository.save(demande);
        inscriptionOtpService.consumeVerificationToken(request.getVerificationToken());

        auditService.log("DEMANDE_INSCRIPTION", "DemandeInscription", String.valueOf(demande.getId()),
                "Demande agence " + demande.getAgenceNom() + " — " + demande.getUsername(), null);

        notificationService.notifyAgenceStaff(
                null,
                "INSCRIPTION",
                "Nouvelle demande d'agence",
                "Demande d'inscription pour l'agence « " + demande.getAgenceNom() + " » "
                        + "(admin : " + demande.getNomComplet() + ", " + demande.getUsername() + "). "
                        + "Paiement " + demande.getMoyenPaiement().name() + " — réf. " + demande.getReferencePaiement()
                        + ". En attente de validation.",
                RoleType.SUPER_ADMIN
        );

        notificationService.sendPlainEmail(
                demande.getEmail(),
                "Demande d'inscription reçue",
                "Bonjour " + demande.getNomComplet() + ",\n\n"
                        + "Votre demande de création d'agence « " + demande.getAgenceNom() + " » a bien été reçue. "
                        + "Elle sera examinée par notre équipe. Vous recevrez un e-mail dès qu'elle sera traitée.\n\n"
                        + "Tontine Marché"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Votre demande a été soumise. Vous serez notifié après validation par l'administrateur.");
        result.put("demandeId", demande.getId());
        return result;
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
