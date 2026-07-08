package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.DemandeInscriptionAgence;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutDemandeInscription;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.AgenceDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.DemandeInscriptionAgenceRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemandeInscriptionService {

    private final DemandeInscriptionAgenceRepository demandeRepository;
    private final AgenceService agenceService;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AgentService agentService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAll(String statut) {
        List<DemandeInscriptionAgence> list;
        if (statut != null && !statut.isBlank()) {
            list = demandeRepository.findByStatutOrderByCreatedAtDesc(
                    StatutDemandeInscription.valueOf(statut));
        } else {
            list = demandeRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public long countEnAttente() {
        return demandeRepository.countByStatut(StatutDemandeInscription.EN_ATTENTE);
    }

    @Transactional
    public Map<String, Object> approuver(Long id) {
        DemandeInscriptionAgence demande = getEnAttente(id);

        if (utilisateurRepository.existsByUsername(demande.getUsername())) {
            throw ApiException.conflict("L'identifiant " + demande.getUsername() + " est déjà utilisé");
        }

        AgenceDto created = agenceService.create(AgenceDto.builder()
                .nom(demande.getAgenceNom())
                .responsable(demande.getResponsable())
                .telephone(demande.getAgenceTelephone())
                .email(demande.getAgenceEmail())
                .adresse(demande.getAdresse())
                .ville(demande.getVille())
                .logoUrl(demande.getLogoUrl())
                .latitude(demande.getLatitude())
                .longitude(demande.getLongitude())
                .build());

        Agence agence = agenceService.getEntity(created.getId());

        Utilisateur admin = utilisateurRepository.save(Utilisateur.builder()
                .username(demande.getUsername())
                .password(demande.getPasswordHash())
                .nomComplet(demande.getNomComplet())
                .email(demande.getEmail())
                .telephone(demande.getTelephone())
                .role(RoleType.ADMIN_AGENCE)
                .agence(agence)
                .statut(StatutEntity.ACTIF)
                .build());

        agentService.ensureCollecteurProfile(admin);

        demande.setStatut(StatutDemandeInscription.APPROUVEE);
        demande.setAgenceCreeeId(agence.getId());
        demande.setDateTraitement(Instant.now());
        demandeRepository.save(demande);

        auditService.log("APPROBATION_INSCRIPTION", "DemandeInscription", String.valueOf(id),
                "Agence créée " + agence.getCode(), agence.getId());

        notificationService.sendInscriptionApprovalEmail(demande, agence.getCode());

        return toMap(demande);
    }

    @Transactional
    public Map<String, Object> rejeter(Long id, String motif) {
        DemandeInscriptionAgence demande = getEnAttente(id);
        demande.setStatut(StatutDemandeInscription.REJETEE);
        demande.setMotifRejet(motif != null ? motif.trim() : "Demande refusée");
        demande.setDateTraitement(Instant.now());
        demandeRepository.save(demande);

        auditService.log("REJET_INSCRIPTION", "DemandeInscription", String.valueOf(id),
                demande.getMotifRejet(), null);

        notificationService.sendInscriptionRejectionEmail(demande);

        return toMap(demande);
    }

    private DemandeInscriptionAgence getEnAttente(Long id) {
        DemandeInscriptionAgence demande = demandeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Demande introuvable"));
        if (demande.getStatut() != StatutDemandeInscription.EN_ATTENTE) {
            throw ApiException.badRequest("Cette demande a déjà été traitée");
        }
        return demande;
    }

    private Map<String, Object> toMap(DemandeInscriptionAgence d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("agenceNom", d.getAgenceNom());
        m.put("responsable", d.getResponsable());
        m.put("agenceTelephone", d.getAgenceTelephone());
        m.put("agenceEmail", d.getAgenceEmail());
        m.put("adresse", d.getAdresse());
        m.put("ville", d.getVille());
        m.put("logoUrl", d.getLogoUrl());
        m.put("latitude", d.getLatitude());
        m.put("longitude", d.getLongitude());
        m.put("username", d.getUsername());
        m.put("nomComplet", d.getNomComplet());
        m.put("email", d.getEmail());
        m.put("telephone", d.getTelephone());
        m.put("pieceIdentiteUrl", d.getPieceIdentiteUrl());
        m.put("moyenPaiement", d.getMoyenPaiement().name());
        m.put("referencePaiement", d.getReferencePaiement());
        m.put("statut", d.getStatut().name());
        m.put("motifRejet", d.getMotifRejet());
        m.put("agenceCreeeId", d.getAgenceCreeeId());
        m.put("createdAt", d.getCreatedAt());
        m.put("dateTraitement", d.getDateTraitement());
        return m;
    }
}
