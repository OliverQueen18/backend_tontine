package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.Caisse;
import com.tontinemarche.domain.entity.MouvementCaisse;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.StatutCaisse;
import com.tontinemarche.domain.enums.TypeMouvement;
import com.tontinemarche.dto.CaisseDto;
import com.tontinemarche.dto.MouvementCaisseDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.CaisseRepository;
import com.tontinemarche.repository.MouvementCaisseRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaisseService {

    private final CaisseRepository caisseRepository;
    private final MouvementCaisseRepository mouvementCaisseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AgenceRepository agenceRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public CaisseDto getCaisseDuJour(Long agenceId) {
        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, LocalDate.now())
                .orElse(null);
        if (caisse == null) {
            return null;
        }
        return map(caisse);
    }

    @Transactional
    public CaisseDto ouvrir(Long agenceId, BigDecimal soldeInitial) {
        LocalDate today = LocalDate.now();
        if (caisseRepository.findByAgenceIdAndDateCaisse(agenceId, today).isPresent()) {
            throw ApiException.conflict("La caisse du jour est déjà ouverte");
        }

        Agence agence = agenceRepository.findById(agenceId)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable: " + agenceId));

        BigDecimal report = soldeInitial != null ? soldeInitial : BigDecimal.ZERO;
        Caisse precedente = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, today.minusDays(1))
                .orElse(null);
        if (precedente != null && precedente.getStatut() == StatutCaisse.CLOTUREE
                && precedente.getSoldeReel() != null && soldeInitial == null) {
            report = precedente.getSoldeReel();
        }

        Caisse caisse = Caisse.builder()
                .agence(agence)
                .dateCaisse(today)
                .soldeInitial(report)
                .totalEntrees(BigDecimal.ZERO)
                .totalSorties(BigDecimal.ZERO)
                .soldeTheorique(report)
                .statut(StatutCaisse.OUVERTE)
                .ouvertPar(currentUser())
                .dateOuverture(LocalDateTime.now())
                .build();

        caisse = caisseRepository.save(caisse);

        if (report.compareTo(BigDecimal.ZERO) > 0) {
            mouvementCaisseRepository.save(MouvementCaisse.builder()
                    .caisse(caisse)
                    .type(TypeMouvement.ENTREE)
                    .categorie(CategorieMouvement.REPORT)
                    .montant(report)
                    .libelle("Report solde veille")
                    .dateHeure(LocalDateTime.now())
                    .effectuePar(currentUser())
                    .build());
        }

        auditService.log("OUVERTURE_CAISSE", "Caisse", today.toString(),
                "Solde initial: " + report, agenceId);
        return map(caisse);
    }

    @Transactional
    public CaisseDto cloturer(Long agenceId, Map<String, Object> payload) {
        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, LocalDate.now())
                .orElseThrow(() -> ApiException.notFound("Aucune caisse ouverte aujourd'hui"));

        if (caisse.getStatut() == StatutCaisse.CLOTUREE) {
            throw ApiException.badRequest("Caisse déjà clôturée");
        }

        BigDecimal soldeReel = new BigDecimal(payload.get("soldeReel").toString());
        String observation = payload.getOrDefault("observation", "").toString();

        caisse.setSoldeReel(soldeReel);
        caisse.setEcart(soldeReel.subtract(caisse.getSoldeTheorique()));
        caisse.setObservation(observation);
        caisse.setStatut(StatutCaisse.CLOTUREE);
        caisse.setCloturePar(currentUser());
        caisse.setDateCloture(LocalDateTime.now());

        caisse = caisseRepository.save(caisse);
        auditService.log("CLOTURE_CAISSE", "Caisse", caisse.getDateCaisse().toString(),
                "Écart: " + caisse.getEcart(), agenceId);

        if (caisse.getEcart() != null && caisse.getEcart().compareTo(BigDecimal.ZERO) != 0) {
            notificationService.notifyAgenceStaff(
                    agenceId,
                    "ECART_CAISSE",
                    "Écart de caisse",
                    "Écart de " + caisse.getEcart() + " FCFA détecté à la clôture du "
                            + caisse.getDateCaisse() + " (agence " + caisse.getAgence().getNom()
                            + "). Solde théorique : " + caisse.getSoldeTheorique()
                            + " FCFA, solde réel : " + caisse.getSoldeReel() + " FCFA.",
                    com.tontinemarche.domain.enums.RoleType.ADMIN_AGENCE,
                    com.tontinemarche.domain.enums.RoleType.CAISSIER,
                    com.tontinemarche.domain.enums.RoleType.AUDITEUR,
                    com.tontinemarche.domain.enums.RoleType.SUPER_ADMIN
            );
        }

        return map(caisse);
    }

    @Transactional
    public void enregistrerMouvement(Long agenceId, TypeMouvement type, CategorieMouvement categorie,
                                     BigDecimal montant, String libelle, String reference) {
        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, LocalDate.now())
                .orElseGet(() -> {
                    ouvrir(agenceId, null);
                    return caisseRepository.findByAgenceIdAndDateCaisse(agenceId, LocalDate.now())
                            .orElseThrow(() -> ApiException.badRequest("Impossible d'ouvrir la caisse"));
                });

        if (caisse.getStatut() == StatutCaisse.CLOTUREE) {
            throw ApiException.badRequest("La caisse est clôturée");
        }

        mouvementCaisseRepository.save(MouvementCaisse.builder()
                .caisse(caisse)
                .type(type)
                .categorie(categorie)
                .montant(montant)
                .libelle(libelle)
                .reference(reference)
                .dateHeure(LocalDateTime.now())
                .effectuePar(currentUser())
                .build());

        if (type == TypeMouvement.ENTREE && categorie != CategorieMouvement.REPORT) {
            caisse.setTotalEntrees(caisse.getTotalEntrees().add(montant));
        } else if (type == TypeMouvement.SORTIE) {
            caisse.setTotalSorties(caisse.getTotalSorties().add(montant));
        }
        caisse.setSoldeTheorique(
                caisse.getSoldeInitial().add(caisse.getTotalEntrees()).subtract(caisse.getTotalSorties())
        );
        caisseRepository.save(caisse);
    }

    private CaisseDto map(Caisse caisse) {
        List<MouvementCaisseDto> mouvements = mouvementCaisseRepository
                .findByCaisseIdOrderByDateHeureDesc(caisse.getId())
                .stream()
                .map(EntityMapper::toDto)
                .toList();
        return EntityMapper.toDto(caisse, mouvements);
    }

    private Utilisateur currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return utilisateurRepository.findById(principal.getId()).orElse(null);
        }
        return null;
    }
}
