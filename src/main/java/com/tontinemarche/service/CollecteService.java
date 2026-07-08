package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.entity.Collecte;
import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.domain.enums.TypeMouvement;
import com.tontinemarche.dto.CollecteDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
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

        if (client.getStatut() != StatutEntity.ACTIF) {
            throw ApiException.badRequest("Client inactif");
        }
        if (client.getAgent() == null) {
            throw ApiException.badRequest("Client non affecté à un agent");
        }
        if (dto.getSignatureClient() == null || dto.getSignatureClient().isBlank()) {
            throw ApiException.badRequest("La signature électronique est obligatoire");
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
                .signatureClient(dto.getSignatureClient())
                .validee(true)
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
                com.tontinemarche.domain.enums.RoleType.ADMIN_AGENCE,
                com.tontinemarche.domain.enums.RoleType.SUPER_ADMIN
        );
        if (agent.getUtilisateur() != null) {
            notificationService.notifyUsers(
                    java.util.List.of(agent.getUtilisateur()),
                    "COLLECTE",
                    "Collecte enregistrée",
                    "Votre collecte " + numeroRecu + " de " + montantRecu
                            + " FCFA (" + nombreJours + " j) pour " + client.getNomComplet()
                            + " a été validée."
            );
        }

        notificationService.sendCollecteReceiptToClient(client, collecte, agent);

        return EntityMapper.toDto(collecte);
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
