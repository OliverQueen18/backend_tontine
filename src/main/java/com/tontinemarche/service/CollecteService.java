package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.entity.Collecte;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.domain.enums.TypeMouvement;
import com.tontinemarche.dto.CollecteDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.AgentRepository;
import com.tontinemarche.repository.ClientRepository;
import com.tontinemarche.repository.CollecteRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.security.UserPrincipal;
import com.tontinemarche.util.ClientCalculUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class CollecteService {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    private final CollecteRepository collecteRepository;
    private final ClientRepository clientRepository;
    private final AgentRepository agentRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AgentService agentService;
    private final CaisseService caisseService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<CollecteDto> filter(Long agenceId, Long agentId, Long clientId, LocalDate debut, LocalDate fin) {
        return collecteRepository.filter(agenceId, agentId, clientId, debut, fin).stream()
                .map(EntityMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollecteDto> portefeuilleAgent(Long agentId) {
        return clientRepository.findByAgentIdAndStatut(agentId, StatutEntity.ACTIF).stream()
                .map(client -> CollecteDto.builder()
                        .clientId(client.getId())
                        .clientCode(client.getCode())
                        .clientNom(client.getNomComplet())
                        .clientPhotoUrl(client.getPhotoUrl())
                        .agentId(agentId)
                        .montantPrevu(client.getMontantJournalier())
                        .montantJournalier(client.getMontantJournalier())
                        .soldeEpargne(client.getSoldeEpargne())
                        .nombreJoursPayes(ClientCalculUtil.computeNombreJoursPayes(
                                client.getSoldeEpargne(), client.getMontantJournalier()))
                        .dateProbableRetrait(ClientCalculUtil.computeDateProbableRetrait(
                                client.getDateAdhesion(), LocalDate.now()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollecteDto> historiqueClient(Long clientId) {
        return collecteRepository.findByClientIdOrderByDateHeureDesc(clientId).stream()
                .map(EntityMapper::toDto)
                .toList();
    }

    @Transactional
    public CollecteDto enregistrer(CollecteDto dto) {
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> ApiException.notFound("Client introuvable"));
        caisseService.requireCaisseOuverte(client.getAgence().getId());

        if (client.getStatut() != StatutEntity.ACTIF) {
            throw ApiException.badRequest("Client inactif");
        }
        if (client.getAgent() == null) {
            throw ApiException.badRequest("Client non affecté à un agent");
        }

        Agent agent = resolveAgent(dto, client);
        LocalDateTime now = LocalDateTime.now();
        String numeroRecu = "COL-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + SEQUENCE.incrementAndGet();

        BigDecimal montantJournalier = client.getMontantJournalier();
        if (montantJournalier == null || montantJournalier.signum() <= 0) {
            throw ApiException.badRequest("Montant journalier du client non configuré");
        }

        BigDecimal nombreJours = dto.getNombreJoursPayes();
        BigDecimal montantRecu = dto.getMontantRecu();

        if (nombreJours != null && nombreJours.signum() > 0) {
            montantRecu = ClientCalculUtil.montantFromJours(montantJournalier, nombreJours);
        } else if (montantRecu != null && montantRecu.signum() > 0) {
            nombreJours = ClientCalculUtil.joursFromMontant(montantJournalier, montantRecu);
        } else {
            throw ApiException.badRequest("Indiquez le montant collecté ou le nombre de jours payés");
        }

        if (montantRecu.signum() <= 0) {
            throw ApiException.badRequest("Le montant collecté doit être positif");
        }

        boolean signee = dto.getSignatureClient() != null && !dto.getSignatureClient().isBlank();

        Collecte collecte = Collecte.builder()
                .numeroRecu(numeroRecu)
                .client(client)
                .agent(agent)
                .agence(client.getAgence())
                .montantPrevu(montantJournalier.multiply(nombreJours))
                .montantRecu(montantRecu)
                .nombreJoursPayes(nombreJours)
                .dateCollecte(now.toLocalDate())
                .dateHeure(now)
                .signatureClient(signee ? dto.getSignatureClient() : null)
                .validee(signee)
                .annulee(false)
                .build();

        collecte = collecteRepository.save(collecte);

        client.setSoldeEpargne(client.getSoldeEpargne().add(montantRecu));
        clientRepository.save(client);

        caisseService.enregistrerMouvement(
                client.getAgence().getId(),
                TypeMouvement.ENTREE,
                CategorieMouvement.COLLECTE,
                montantRecu,
                "Collecte " + client.getCode() + " (" + nombreJours + " j)",
                numeroRecu
        );

        auditService.log("COLLECTE", "Collecte", numeroRecu,
                montantRecu + " FCFA (" + nombreJours + " j) - " + client.getCode(),
                client.getAgence().getId());

        notificationService.notifyAgenceStaff(
                client.getAgence().getId(),
                "COLLECTE",
                "Nouvelle collecte",
                "Collecte " + numeroRecu + " : " + montantRecu + " FCFA (" + nombreJours
                        + " j) pour le client " + client.getCode() + " (" + client.getNomComplet()
                        + ") par l'agent " + agent.getNomComplet() + ".",
                RoleType.ADMIN_AGENCE,
                RoleType.SUPER_ADMIN
        );
        if (agent.getUtilisateur() != null) {
            notificationService.notifyUsers(
                    java.util.List.of(agent.getUtilisateur()),
                    "COLLECTE",
                    "Collecte enregistrée",
                    "Votre collecte " + numeroRecu + " de " + montantRecu
                            + " FCFA (" + nombreJours + " j) pour " + client.getNomComplet()
                            + " a été " + (signee ? "validée." : "enregistrée (signature en attente).")
            );
        }

        if (signee) {
            notificationService.sendCollecteReceiptToClient(client, collecte, agent);
        }

        return EntityMapper.toDto(collecte);
    }

    @Transactional
    public CollecteDto signer(Long id, String signatureClient) {
        if (signatureClient == null || signatureClient.isBlank()) {
            throw ApiException.badRequest("La signature électronique est obligatoire");
        }
        Collecte collecte = getCollecte(id);
        assertCanManage(collecte);
        if (collecte.isAnnulee()) {
            throw ApiException.badRequest("Impossible de signer une collecte annulée");
        }
        if (collecte.getSignatureClient() != null && !collecte.getSignatureClient().isBlank()) {
            throw ApiException.badRequest("Cette collecte est déjà signée");
        }

        collecte.setSignatureClient(signatureClient);
        collecte.setValidee(true);
        collecte = collecteRepository.save(collecte);

        auditService.log("COLLECTE_SIGNATURE", "Collecte", collecte.getNumeroRecu(),
                "Signature ajoutée — " + collecte.getClient().getCode(),
                collecte.getAgence().getId());

        notificationService.sendCollecteReceiptToClient(collecte.getClient(), collecte, collecte.getAgent());

        return EntityMapper.toDto(collecte);
    }

    @Transactional
    public CollecteDto annuler(Long id) {
        Collecte collecte = getCollecte(id);
        assertCanManage(collecte);
        if (collecte.isAnnulee()) {
            throw ApiException.badRequest("Cette collecte est déjà annulée");
        }

        Client client = collecte.getClient();
        BigDecimal montant = collecte.getMontantRecu();
        BigDecimal solde = client.getSoldeEpargne() != null ? client.getSoldeEpargne() : BigDecimal.ZERO;
        if (solde.compareTo(montant) < 0) {
            throw ApiException.badRequest(
                    "Impossible d'annuler : le solde du client (" + solde
                            + " FCFA) est inférieur au montant de la collecte"
            );
        }

        client.setSoldeEpargne(solde.subtract(montant));
        clientRepository.save(client);

        try {
            caisseService.enregistrerMouvement(
                    collecte.getAgence().getId(),
                    TypeMouvement.SORTIE,
                    CategorieMouvement.COLLECTE,
                    montant,
                    "Annulation collecte " + client.getCode() + " — " + collecte.getNumeroRecu(),
                    "ANNUL-" + collecte.getNumeroRecu()
            );
        } catch (ApiException e) {
            auditService.log("COLLECTE_ANNULATION_CAISSE", "Collecte", collecte.getNumeroRecu(),
                    "Annulation sans mouvement caisse : " + e.getMessage(),
                    collecte.getAgence().getId());
        }

        collecte.setAnnulee(true);
        collecte.setValidee(false);
        collecte = collecteRepository.save(collecte);

        auditService.log("COLLECTE_ANNULATION", "Collecte", collecte.getNumeroRecu(),
                "Annulation " + montant + " FCFA — " + client.getCode(),
                collecte.getAgence().getId());

        notificationService.notifyAgenceStaff(
                collecte.getAgence().getId(),
                "COLLECTE",
                "Collecte annulée",
                "La collecte " + collecte.getNumeroRecu() + " (" + montant + " FCFA) pour "
                        + client.getNomComplet() + " a été annulée.",
                RoleType.ADMIN_AGENCE,
                RoleType.SUPER_ADMIN
        );

        return EntityMapper.toDto(collecte);
    }

    private Collecte getCollecte(Long id) {
        return collecteRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Collecte introuvable"));
    }

    private void assertCanManage(Collecte collecte) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null
                    || !principal.getAgenceId().equals(collecte.getAgence().getId())) {
                throw ApiException.forbidden("Accès limité à votre agence");
            }
            return;
        }
        if (principal.getRole() == RoleType.AGENT) {
            Agent agent = agentRepository.findByUtilisateurId(principal.getId()).orElse(null);
            if (agent == null || !agent.getId().equals(collecte.getAgent().getId())) {
                throw ApiException.forbidden("Vous ne pouvez gérer que vos propres collectes");
            }
            return;
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private Agent resolveAgent(CollecteDto dto, Client client) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            if (principal.getRole() == RoleType.AGENT || principal.getRole() == RoleType.ADMIN_AGENCE) {
                Utilisateur user = utilisateurRepository.findById(principal.getId())
                        .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
                return agentService.ensureCollecteurProfile(user);
            }
            return agentRepository.findByUtilisateurId(principal.getId())
                    .orElse(client.getAgent());
        }
        if (dto.getAgentId() != null) {
            return agentRepository.findById(dto.getAgentId())
                    .orElseThrow(() -> ApiException.notFound("Agent introuvable"));
        }
        return client.getAgent();
    }
}
