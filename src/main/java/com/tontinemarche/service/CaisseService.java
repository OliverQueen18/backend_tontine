package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.Caisse;
import com.tontinemarche.domain.entity.MouvementCaisse;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.StatutCaisse;
import com.tontinemarche.domain.enums.TypeMouvement;
import com.tontinemarche.dto.CaisseControleDto;
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
        assertCanAccessAgence(agenceId);
        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, LocalDate.now())
                .orElse(null);
        if (caisse == null) {
            return null;
        }
        return map(caisse);
    }

    @Transactional(readOnly = true)
    public CaisseDto getByDate(Long agenceId, LocalDate date) {
        assertCanAccessAgence(agenceId);
        LocalDate dateCaisse = date != null ? date : LocalDate.now();
        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, dateCaisse)
                .orElseThrow(() -> ApiException.notFound("Aucune caisse pour le " + dateCaisse));
        return map(caisse);
    }

    @Transactional(readOnly = true)
    public CaisseDto getById(Long id) {
        Caisse caisse = caisseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Caisse introuvable"));
        assertCanAccessAgence(caisse.getAgence().getId());
        return map(caisse);
    }

    @Transactional(readOnly = true)
    public List<CaisseDto> findByPeriode(Long agenceId, LocalDate debut, LocalDate fin) {
        assertCanAccessAgence(agenceId);
        LocalDate d0 = debut != null ? debut : LocalDate.now().withDayOfMonth(1);
        LocalDate d1 = fin != null ? fin : LocalDate.now();
        if (d0.isAfter(d1)) {
            throw ApiException.badRequest("La date de début doit être antérieure à la date de fin");
        }
        return caisseRepository.findByAgenceIdAndDateCaisseBetweenOrderByDateCaisseDesc(agenceId, d0, d1)
                .stream()
                .map(this::mapSummary)
                .toList();
    }

    /**
     * Contrôle d'accès opérationnel : aucune caisse antérieure ouverte + caisse du jour ouverte.
     */
    @Transactional(readOnly = true)
    public CaisseControleDto getControle(Long agenceId) {
        assertCanAccessAgence(agenceId);
        LocalDate today = LocalDate.now();
        List<CaisseDto> anterieures = caissesAnterieuresOuvertes(agenceId, today).stream()
                .map(this::mapSummary)
                .toList();
        Caisse duJour = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, today).orElse(null);
        CaisseDto duJourDto = duJour != null ? mapSummary(duJour) : null;

        boolean jourOuverte = duJour != null && duJour.getStatut() == StatutCaisse.OUVERTE;
        boolean peutOperer = anterieures.isEmpty() && jourOuverte;

        String message;
        if (!anterieures.isEmpty()) {
            String dates = anterieures.stream()
                    .map(c -> c.getDateCaisse().toString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            message = "Des caisses antérieures sont encore ouvertes (" + dates
                    + "). Clôturez-les dans le menu Caisse avant de continuer.";
        } else if (!jourOuverte) {
            message = duJour != null && duJour.getStatut() == StatutCaisse.CLOTUREE
                    ? "La caisse du jour est clôturée. Annulez la clôture pour reprendre les opérations."
                    : "La caisse du jour n'est pas ouverte. Ouvrez-la avant d'effectuer des collectes, restitutions ou opérations.";
        } else {
            message = null;
        }

        return CaisseControleDto.builder()
                .peutOperer(peutOperer)
                .message(message)
                .caisseDuJour(duJourDto)
                .caissesAnterieuresOuvertes(anterieures)
                .build();
    }

    @Transactional
    public CaisseDto ouvrir(Long agenceId, BigDecimal soldeInitial) {
        assertCanAccessAgence(agenceId);
        LocalDate today = LocalDate.now();
        if (caisseRepository.findByAgenceIdAndDateCaisse(agenceId, today).isPresent()) {
            throw ApiException.conflict("La caisse du jour est déjà ouverte");
        }

        assertAucuneCaisseAnterieureOuverte(agenceId, today);

        Agence agence = agenceRepository.findById(agenceId)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable: " + agenceId));

        Caisse precedente = caisseRepository
                .findFirstByAgenceIdAndDateCaisseLessThanOrderByDateCaisseDesc(agenceId, today)
                .orElse(null);

        // Enchaînement : le solde net (réel) de la dernière caisse clôturée devient le solde antérieur.
        BigDecimal report;
        if (precedente != null && precedente.getStatut() == StatutCaisse.CLOTUREE) {
            report = soldeNet(precedente);
            if (soldeInitial != null && soldeInitial.compareTo(report) != 0) {
                throw ApiException.badRequest(
                        "Le solde initial doit correspondre au solde net de la caisse précédente ("
                                + report + " FCFA). Laissez le champ vide pour le report automatique.");
            }
        } else {
            report = soldeInitial != null ? soldeInitial : BigDecimal.ZERO;
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
        synchroniserMouvementReport(caisse, report);

        auditService.log("OUVERTURE_CAISSE", "Caisse", today.toString(),
                "Solde initial (report): " + report, agenceId);
        return map(caisse);
    }

    @Transactional
    public CaisseDto cloturer(Long agenceId, Map<String, Object> payload) {
        assertCanAccessAgence(agenceId);

        final LocalDate dateCaisse = payload.get("dateCaisse") != null
                && !payload.get("dateCaisse").toString().isBlank()
                ? LocalDate.parse(payload.get("dateCaisse").toString())
                : LocalDate.now();

        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, dateCaisse)
                .orElseThrow(() -> ApiException.notFound("Aucune caisse pour le " + dateCaisse));

        if (caisse.getStatut() == StatutCaisse.CLOTUREE) {
            throw ApiException.badRequest("Caisse déjà clôturée");
        }

        // Clôturer d'abord les caisses antérieures encore ouvertes (ordre chronologique).
        assertAucuneCaisseAnterieureOuverte(agenceId, dateCaisse);

        if (payload.get("soldeReel") == null) {
            throw ApiException.badRequest("Le solde réel est obligatoire");
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

        // Si des caisses postérieures existent déjà, propager le nouveau solde net.
        propagerSoldesSuivants(caisse);

        auditService.log("CLOTURE_CAISSE", "Caisse", caisse.getDateCaisse().toString(),
                "Solde net: " + soldeReel + " | Écart: " + caisse.getEcart(), agenceId);

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

    /**
     * Annule la clôture (réouvre la caisse). Uniquement sur la dernière caisse de la chaîne.
     */
    @Transactional
    public CaisseDto annulerCloture(Long id) {
        Caisse caisse = caisseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Caisse introuvable"));
        assertCanAccessAgence(caisse.getAgence().getId());

        if (caisse.getStatut() != StatutCaisse.CLOTUREE) {
            throw ApiException.badRequest("Cette caisse n'est pas clôturée");
        }
        assertEstDerniereCaisse(caisse);

        caisse.setStatut(StatutCaisse.OUVERTE);
        caisse.setSoldeReel(null);
        caisse.setEcart(null);
        caisse.setObservation(null);
        caisse.setCloturePar(null);
        caisse.setDateCloture(null);
        caisse = caisseRepository.save(caisse);

        auditService.log("ANNULATION_CLOTURE_CAISSE", "Caisse", caisse.getDateCaisse().toString(),
                "Clôture annulée — caisse réouverte", caisse.getAgence().getId());
        return map(caisse);
    }

    /**
     * Supprime une caisse et son journal, puis recalcule les reports des caisses suivantes.
     * Les collectes/restitutions métier restent en base (seul le journal de caisse est retiré).
     */
    @Transactional
    public void supprimer(Long id) {
        Caisse caisse = caisseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Caisse introuvable"));
        Long agenceId = caisse.getAgence().getId();
        assertCanAccessAgence(agenceId);

        LocalDate date = caisse.getDateCaisse();
        long nbMouvements = mouvementCaisseRepository
                .findByCaisseIdOrderByDateHeureDesc(caisse.getId())
                .size();

        Caisse precedente = caisseRepository
                .findFirstByAgenceIdAndDateCaisseLessThanOrderByDateCaisseDesc(agenceId, date)
                .orElse(null);

        mouvementCaisseRepository.deleteByCaisseId(caisse.getId());
        caisseRepository.delete(caisse);
        // Flush pour que les requêtes suivantes ne voient plus la caisse supprimée
        caisseRepository.flush();

        if (precedente != null) {
            propagerSoldesSuivants(precedente);
        } else {
            List<Caisse> suivantes = caisseRepository
                    .findByAgenceIdAndDateCaisseGreaterThanOrderByDateCaisseAsc(agenceId, date);
            if (!suivantes.isEmpty()) {
                Caisse premiere = suivantes.get(0);
                appliquerSoldeInitial(premiere, BigDecimal.ZERO);
                caisseRepository.save(premiere);
                propagerSoldesSuivants(premiere);
            }
        }

        auditService.log("SUPPRESSION_CAISSE", "Caisse", date.toString(),
                "Caisse supprimée (" + nbMouvements + " mouvement(s) journal)", agenceId);
    }

    @Transactional(readOnly = true)
    public Caisse requireCaisseOuverte(Long agenceId) {
        if (agenceId == null) {
            throw ApiException.badRequest("Agence requise pour vérifier la caisse");
        }
        LocalDate today = LocalDate.now();
        assertAucuneCaisseAnterieureOuverte(agenceId, today);

        Caisse caisse = caisseRepository.findByAgenceIdAndDateCaisse(agenceId, today)
                .orElseThrow(() -> ApiException.badRequest(
                        "La caisse du jour n'est pas ouverte. Ouvrez la caisse avant d'effectuer cette opération."));
        if (caisse.getStatut() != StatutCaisse.OUVERTE) {
            throw ApiException.badRequest(
                    "La caisse du jour est clôturée. Annulez la clôture avant d'effectuer cette opération.");
        }
        return caisse;
    }

    @Transactional
    public void enregistrerMouvement(Long agenceId, TypeMouvement type, CategorieMouvement categorie,
                                     BigDecimal montant, String libelle, String reference) {
        Caisse caisse = requireCaisseOuverte(agenceId);

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
        recalculerSoldeTheorique(caisse);
        caisseRepository.save(caisse);
    }

    /** Solde net reporté : solde réel si clôturée, sinon solde théorique. */
    private BigDecimal soldeNet(Caisse caisse) {
        if (caisse.getStatut() == StatutCaisse.CLOTUREE && caisse.getSoldeReel() != null) {
            return caisse.getSoldeReel();
        }
        return caisse.getSoldeTheorique() != null ? caisse.getSoldeTheorique() : BigDecimal.ZERO;
    }

    private List<Caisse> caissesAnterieuresOuvertes(Long agenceId, LocalDate avant) {
        return caisseRepository.findByAgenceIdAndStatutAndDateCaisseLessThanOrderByDateCaisseAsc(
                agenceId, StatutCaisse.OUVERTE, avant);
    }

    private void assertAucuneCaisseAnterieureOuverte(Long agenceId, LocalDate avant) {
        List<Caisse> anterieures = caissesAnterieuresOuvertes(agenceId, avant);
        if (!anterieures.isEmpty()) {
            Caisse premiere = anterieures.get(0);
            throw ApiException.badRequest(
                    "La caisse du " + premiere.getDateCaisse()
                            + " est encore ouverte. Clôturez toutes les caisses antérieures avant de continuer.");
        }
    }

    private void assertEstDerniereCaisse(Caisse caisse) {
        caisseRepository.findFirstByAgenceIdAndDateCaisseGreaterThanOrderByDateCaisseAsc(
                        caisse.getAgence().getId(), caisse.getDateCaisse())
                .ifPresent(suivante -> {
                    throw ApiException.badRequest(
                            "Impossible : une caisse plus récente existe (" + suivante.getDateCaisse()
                                    + "). Supprimez ou annulez d'abord les caisses les plus récentes "
                                    + "(de la plus récente vers la plus ancienne).");
                });
    }

    /**
     * Recalcule soldeInitial / report / soldeThéorique / écart pour toutes les caisses
     * strictement postérieures, en suivant l'évolution de la chaîne.
     */
    private void propagerSoldesSuivants(Caisse source) {
        List<Caisse> suivantes = caisseRepository
                .findByAgenceIdAndDateCaisseGreaterThanOrderByDateCaisseAsc(
                        source.getAgence().getId(), source.getDateCaisse());
        if (suivantes.isEmpty()) {
            return;
        }
        Caisse precedente = source;
        for (Caisse suivante : suivantes) {
            BigDecimal report = soldeNet(precedente);
            appliquerSoldeInitial(suivante, report);
            caisseRepository.save(suivante);
            precedente = suivante;
        }
    }

    private void appliquerSoldeInitial(Caisse caisse, BigDecimal report) {
        caisse.setSoldeInitial(report);
        recalculerSoldeTheorique(caisse);
        if (caisse.getStatut() == StatutCaisse.CLOTUREE && caisse.getSoldeReel() != null) {
            caisse.setEcart(caisse.getSoldeReel().subtract(caisse.getSoldeTheorique()));
        }
        synchroniserMouvementReport(caisse, report);
    }

    private void recalculerSoldeTheorique(Caisse caisse) {
        BigDecimal initial = caisse.getSoldeInitial() != null ? caisse.getSoldeInitial() : BigDecimal.ZERO;
        BigDecimal entrees = caisse.getTotalEntrees() != null ? caisse.getTotalEntrees() : BigDecimal.ZERO;
        BigDecimal sorties = caisse.getTotalSorties() != null ? caisse.getTotalSorties() : BigDecimal.ZERO;
        caisse.setSoldeTheorique(initial.add(entrees).subtract(sorties));
    }

    private void synchroniserMouvementReport(Caisse caisse, BigDecimal report) {
        List<MouvementCaisse> mouvements = mouvementCaisseRepository
                .findByCaisseIdOrderByDateHeureDesc(caisse.getId());
        List<MouvementCaisse> reports = mouvements.stream()
                .filter(m -> m.getCategorie() == CategorieMouvement.REPORT)
                .toList();

        if (report == null || report.compareTo(BigDecimal.ZERO) <= 0) {
            if (!reports.isEmpty()) {
                mouvementCaisseRepository.deleteAll(reports);
            }
            return;
        }

        if (reports.isEmpty()) {
            mouvementCaisseRepository.save(MouvementCaisse.builder()
                    .caisse(caisse)
                    .type(TypeMouvement.ENTREE)
                    .categorie(CategorieMouvement.REPORT)
                    .montant(report)
                    .libelle("Report solde antérieur")
                    .dateHeure(LocalDateTime.now())
                    .effectuePar(currentUser())
                    .build());
        } else {
            MouvementCaisse keep = reports.get(0);
            keep.setMontant(report);
            keep.setLibelle("Report solde antérieur");
            mouvementCaisseRepository.save(keep);
            if (reports.size() > 1) {
                mouvementCaisseRepository.deleteAll(reports.subList(1, reports.size()));
            }
        }
    }

    private CaisseDto map(Caisse caisse) {
        List<MouvementCaisseDto> mouvements = mouvementCaisseRepository
                .findByCaisseIdOrderByDateHeureDesc(caisse.getId())
                .stream()
                .map(EntityMapper::toDto)
                .toList();
        return EntityMapper.toDto(caisse, mouvements);
    }

    private CaisseDto mapSummary(Caisse caisse) {
        return EntityMapper.toDto(caisse, List.of());
    }

    private void assertCanAccessAgence(Long agenceId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        if (principal.getRole() == com.tontinemarche.domain.enums.RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getAgenceId() == null || !principal.getAgenceId().equals(agenceId)) {
            throw ApiException.forbidden("Accès limité à votre agence");
        }
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private Utilisateur currentUser() {
        UserPrincipal principal = currentPrincipal();
        if (principal != null) {
            return utilisateurRepository.findById(principal.getId()).orElse(null);
        }
        return null;
    }
}
