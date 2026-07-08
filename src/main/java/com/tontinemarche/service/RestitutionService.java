package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.entity.Restitution;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.domain.enums.TypeMouvement;
import com.tontinemarche.dto.RestitutionDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.AgentRepository;
import com.tontinemarche.repository.ClientHistoriqueRepository;
import com.tontinemarche.repository.ClientRepository;
import com.tontinemarche.repository.CollecteRepository;
import com.tontinemarche.repository.RestitutionRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class RestitutionService {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    private final RestitutionRepository restitutionRepository;
    private final ClientRepository clientRepository;
    private final CollecteRepository collecteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AgentRepository agentRepository;
    private final CaisseService caisseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CommissionGrilleService commissionGrilleService;
    private final ClientHistoriqueRepository clientHistoriqueRepository;

    @Transactional(readOnly = true)
    public List<RestitutionDto> findAll(Long agenceId) {
        UserPrincipal principal = currentPrincipal();
        List<Restitution> list;
        if (principal != null && principal.getRole() == RoleType.AGENT) {
            Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                    .orElseThrow(() -> ApiException.forbidden("Profil agent introuvable"));
            list = restitutionRepository.findByClient_Agent_IdOrderByDateHeureDesc(agent.getId());
        } else if (agenceId != null) {
            list = restitutionRepository.findByAgenceIdOrderByDateHeureDesc(agenceId);
        } else {
            list = restitutionRepository.findAll();
        }
        return list.stream().map(EntityMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RestitutionDto> enAttenteSignature() {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Accès refusé");
        }
        if (principal.getRole() != RoleType.AGENT && principal.getRole() != RoleType.ADMIN_AGENCE) {
            throw ApiException.forbidden("Réservé aux agents collecteurs");
        }
        Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Profil collecteur introuvable"));
        return restitutionRepository.findByClient_Agent_IdAndValideeFalseOrderByDateHeureDesc(agent.getId())
                .stream()
                .map(EntityMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calculer(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("Client introuvable"));

        BigDecimal total = collecteRepository.sumByClient(clientId);
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        var commissionCalc = commissionGrilleService.calculerCommission(client.getAgence().getId(), total);
        BigDecimal commission = commissionCalc.montantCommission();
        BigDecimal net = total.subtract(commission);

        return Map.of(
                "clientId", client.getId(),
                "clientCode", client.getCode(),
                "clientNom", client.getNomComplet(),
                "totalCollecte", total,
                "commission", commission,
                "commissionCalculee", commission,
                "montantNet", net,
                "soldeEpargne", client.getSoldeEpargne(),
                "trancheCommission", commissionCalc.trancheLabel(),
                "modeCommission", commissionCalc.fromGrille() ? "GRILLE" : "TAUX"
        );
    }

    @Transactional
    public RestitutionDto effectuer(RestitutionDto dto) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null
                || (principal.getRole() != RoleType.CAISSIER && principal.getRole() != RoleType.SUPER_ADMIN)) {
            throw ApiException.forbidden("Seul le caissier peut effectuer une restitution");
        }

        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> ApiException.notFound("Client introuvable"));

        if (client.getStatut() != StatutEntity.ACTIF) {
            throw ApiException.badRequest("Client inactif");
        }
        if (restitutionRepository.existsByClientIdAndValideeFalse(client.getId())) {
            throw ApiException.conflict("Une restitution est déjà en attente de signature pour ce client");
        }

        BigDecimal total = collecteRepository.sumByClient(client.getId());
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Aucune épargne à restituer");
        }

        BigDecimal commission = commissionGrilleService
                .calculerCommission(client.getAgence().getId(), total)
                .montantCommission();
        BigDecimal montantNet = total.subtract(commission);

        LocalDateTime now = LocalDateTime.now();
        String numeroRecu = "RES-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + SEQUENCE.incrementAndGet();

        Restitution restitution = Restitution.builder()
                .numeroRecu(numeroRecu)
                .client(client)
                .agence(client.getAgence())
                .caissier(currentUser())
                .totalCollecte(total)
                .commission(commission)
                .commissionCalculee(commission)
                .montantNet(montantNet)
                .dateHeure(now)
                .validee(false)
                .build();

        restitution = restitutionRepository.save(restitution);

        auditService.log("RESTITUTION_INIT", "Restitution", numeroRecu,
                "En attente validation agent — " + client.getCode(),
                client.getAgence().getId());

        notifierRestitutionInitiee(client, restitution, commission, montantNet);

        return EntityMapper.toDto(restitution);
    }

    @Transactional
    public RestitutionDto modifierCommission(Long restitutionId, BigDecimal commission) {
        Restitution restitution = getRestitutionForCollecteur(restitutionId);
        if (restitution.isValidee()) {
            throw ApiException.badRequest("Cette restitution est déjà finalisée");
        }
        BigDecimal avant = restitution.getCommission();
        applyCommissionAmount(restitution, commission);
        restitution = restitutionRepository.save(restitution);
        logClientHistorique(restitution.getClient(), "RESTITUTION_COMMISSION", "commission",
                avant != null ? avant.toPlainString() : null,
                restitution.getCommission().toPlainString(),
                "Commission ajustée sur restitution " + restitution.getNumeroRecu());
        return EntityMapper.toDto(restitution);
    }

    @Transactional
    public RestitutionDto finaliserSignature(Long restitutionId, String signatureClient, BigDecimal commission) {
        if (signatureClient == null || signatureClient.isBlank()) {
            throw ApiException.badRequest("La signature du client est obligatoire");
        }

        Restitution restitution = getRestitutionForCollecteur(restitutionId);

        if (restitution.isValidee()) {
            throw ApiException.badRequest("Cette restitution est déjà finalisée");
        }

        if (commission != null) {
            applyCommissionAmount(restitution, commission);
        }

        Client client = restitution.getClient();
        BigDecimal soldeAvant = client.getSoldeEpargne();
        restitution.setSignatureClient(signatureClient);
        restitution.setValidee(true);
        restitution = restitutionRepository.save(restitution);

        client.setSoldeEpargne(BigDecimal.ZERO);
        client.setStatut(StatutEntity.INACTIF);
        clientRepository.save(client);

        caisseService.enregistrerMouvement(
                client.getAgence().getId(),
                TypeMouvement.SORTIE,
                CategorieMouvement.RESTITUTION,
                restitution.getMontantNet(),
                "Restitution " + client.getCode(),
                restitution.getNumeroRecu()
        );

        auditService.log("RESTITUTION", "Restitution", restitution.getNumeroRecu(),
                restitution.getMontantNet() + " FCFA - commission " + restitution.getCommission()
                        + " FCFA - " + client.getCode(),
                client.getAgence().getId());

        logClientHistorique(client, "RESTITUTION", null, str(soldeAvant), "0",
                "Restitution finalisée — net " + restitution.getMontantNet()
                        + " FCFA, commission " + restitution.getCommission() + " FCFA");

        Agent agent = client.getAgent();
        notificationService.sendRestitutionNoticeToClient(client, restitution, agent);
        notificationService.notifyAgenceStaff(
                client.getAgence().getId(),
                "RESTITUTION",
                "Restitution finalisée",
                "La restitution " + restitution.getNumeroRecu() + " pour " + client.getNomComplet()
                        + " a été signée par le client (agent "
                        + (agent != null ? agent.getNomComplet() : "—") + ").",
                RoleType.ADMIN_AGENCE,
                RoleType.CAISSIER
        );

        notificationService.notifyAgenceStaff(
                client.getAgence().getId(),
                "COMMISSION",
                "Paiement de commission",
                "Commission de " + restitution.getCommission() + " FCFA sur la restitution "
                        + restitution.getNumeroRecu() + ".",
                RoleType.SUPER_ADMIN,
                RoleType.ADMIN_AGENCE
        );

        return EntityMapper.toDto(restitution);
    }

    private Restitution getRestitutionForCollecteur(Long restitutionId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null
                || (principal.getRole() != RoleType.AGENT && principal.getRole() != RoleType.ADMIN_AGENCE)) {
            throw ApiException.forbidden("Seul l'agent collecteur peut modifier cette restitution");
        }
        Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Profil collecteur introuvable"));

        Restitution restitution = restitutionRepository.findById(restitutionId)
                .orElseThrow(() -> ApiException.notFound("Restitution introuvable"));

        Client client = restitution.getClient();
        if (client.getAgent() == null || !client.getAgent().getId().equals(agent.getId())) {
            throw ApiException.forbidden("Ce client n'est pas dans votre portefeuille");
        }
        return restitution;
    }

    private void applyCommissionAmount(Restitution restitution, BigDecimal commission) {
        if (commission == null) {
            commission = BigDecimal.ZERO;
        }
        if (commission.signum() < 0) {
            throw ApiException.badRequest("La commission ne peut pas être négative");
        }
        if (commission.compareTo(restitution.getTotalCollecte()) > 0) {
            throw ApiException.badRequest("La commission ne peut pas dépasser le total collecté");
        }
        restitution.setCommission(commission.setScale(0, RoundingMode.HALF_UP));
        restitution.setMontantNet(restitution.getTotalCollecte().subtract(restitution.getCommission()));
    }

    private void logClientHistorique(Client client, String typeAction, String champ,
                                     String avant, String apres, String details) {
        clientHistoriqueRepository.save(com.tontinemarche.domain.entity.ClientHistorique.builder()
                .client(client)
                .typeAction(typeAction)
                .champ(champ)
                .valeurAvant(avant)
                .valeurApres(apres)
                .details(details)
                .effectuePar(currentUser())
                .dateHeure(LocalDateTime.now())
                .build());
    }

    private String str(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private void notifierRestitutionInitiee(Client client, Restitution restitution,
                                            BigDecimal commission, BigDecimal montantNet) {
        String baseMessage = "Restitution " + restitution.getNumeroRecu() + " pour " + client.getNomComplet()
                + " (" + client.getCode() + ") : net " + montantNet
                + " FCFA, commission proposée " + commission + " FCFA.";

        notificationService.notifyAgenceStaff(
                client.getAgence().getId(),
                "RESTITUTION",
                "Restitution initiée",
                baseMessage + " En attente de validation et signature par l'agent collecteur.",
                RoleType.ADMIN_AGENCE,
                RoleType.CAISSIER
        );

        Agent agent = client.getAgent();
        if (agent != null && agent.getUtilisateur() != null) {
            notificationService.notifyUsers(
                    List.of(agent.getUtilisateur()),
                    "RESTITUTION",
                    "Valider la commission",
                    baseMessage + " Vous pouvez ajuster la commission (y compris 0) avant signature du client."
            );
        }
    }

    private Utilisateur currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return utilisateurRepository.findById(principal.getId()).orElse(null);
        }
        return null;
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
