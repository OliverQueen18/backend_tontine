package com.tontinemarche.service;

import com.tontinemarche.domain.entity.*;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.ClientDto;
import com.tontinemarche.dto.ClientHistoriqueDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.*;
import com.tontinemarche.security.UserPrincipal;
import com.tontinemarche.config.MarcheCodeInitializer;
import com.tontinemarche.util.PhotoUrlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final AgenceService agenceService;
    private final AgentService agentService;
    private final AgentRepository agentRepository;
    private final MarcheRepository marcheRepository;
    private final AffectationClientRepository affectationClientRepository;
    private final ClientHistoriqueRepository clientHistoriqueRepository;
    private final RestitutionRepository restitutionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CommissionGrilleService commissionGrilleService;

    @Transactional(readOnly = true)
    public List<ClientDto> search(String q, Long agenceId, Long agentId) {
        UserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == RoleType.AGENT && agentId == null) {
            agentId = agentRepository.findByUtilisateurId(principal.getId())
                    .map(Agent::getId)
                    .orElse(null);
        }
        if (principal != null && principal.getRole() == RoleType.ADMIN_AGENCE && agenceId == null) {
            agenceId = principal.getAgenceId();
        }
        return clientRepository.search(q, agenceId, agentId).stream()
                .map(this::toDtoWithCommission)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDto findById(Long id) {
        Client client = getEntity(id);
        assertCanViewClient(client);
        return toDtoWithCommission(client);
    }

    @Transactional
    public ClientDto create(ClientDto dto) {
        applyAgentContext(dto);
        validateTelephone(dto.getTelephone(), true);
        validateTelephoneOptional(dto.getTelephoneSecondaire(), "téléphone secondaire");

        if (dto.getMarcheId() == null) {
            throw ApiException.badRequest("Le marché est obligatoire");
        }
        if (dto.getAgentId() == null) {
            throw ApiException.badRequest("L'agent collecteur est obligatoire");
        }

        Agence agence = agenceService.getEntity(dto.getAgenceId());
        Marche marche = marcheRepository.findById(dto.getMarcheId())
                .orElseThrow(() -> ApiException.notFound("Marché introuvable"));
        Agent agent = agentService.getEntity(dto.getAgentId());
        validateAgentMarche(agent, dto.getMarcheId());
        String code = generateClientCode(marche);

        Client client = Client.builder()
                .code(code)
                .nomComplet(dto.getNomComplet())
                .telephone(normalizePhone(dto.getTelephone()))
                .email(dto.getEmail())
                .personneAContacter(dto.getPersonneAContacter())
                .telephoneSecondaire(normalizePhoneOptional(dto.getTelephoneSecondaire()))
                .adresse(dto.getAdresse())
                .profession(dto.getProfession())
                .photoUrl(PhotoUrlSanitizer.sanitize(dto.getPhotoUrl()))
                .signatureReference(dto.getSignatureReference())
                .agence(agence)
                .marche(marche)
                .agent(agent)
                .montantJournalier(dto.getMontantJournalier() != null ? dto.getMontantJournalier() : BigDecimal.ZERO)
                .fraisAdhesion(dto.getFraisAdhesion() != null ? dto.getFraisAdhesion() : BigDecimal.ZERO)
                .dateAdhesion(dto.getDateAdhesion() != null ? dto.getDateAdhesion() : LocalDate.now())
                .soldeEpargne(BigDecimal.ZERO)
                .statut(StatutEntity.ACTIF)
                .build();

        client = clientRepository.save(client);

        affectationClientRepository.save(AffectationClient.builder()
                .client(client)
                .agentSource(null)
                .agentCible(agent)
                .dateAffectation(LocalDate.now())
                .motif("Affectation initiale")
                .effectuePar(currentUser())
                .build());

        auditService.log("CREATION", "Client", client.getCode(), client.getNomComplet(), agence.getId());
        logHistorique(client, "CREATION", null, null, null,
                "Client créé et affecté à " + agent.getNomComplet());
        return toDtoWithCommission(client);
    }

    @Transactional
    public ClientDto update(Long id, ClientDto dto) {
        Client client = getEntity(id);
        assertCanManageClient(client);

        trackChange(client, "nomComplet", client.getNomComplet(), dto.getNomComplet());
        validateTelephone(dto.getTelephone(), true);
        trackChange(client, "telephone", client.getTelephone(), normalizePhone(dto.getTelephone()));
        trackChange(client, "email", client.getEmail(), dto.getEmail());
        trackChange(client, "personneAContacter", client.getPersonneAContacter(), dto.getPersonneAContacter());
        validateTelephoneOptional(dto.getTelephoneSecondaire(), "téléphone secondaire");
        trackChange(client, "telephoneSecondaire", client.getTelephoneSecondaire(),
                normalizePhoneOptional(dto.getTelephoneSecondaire()));
        trackChange(client, "adresse", client.getAdresse(), dto.getAdresse());
        trackChange(client, "profession", client.getProfession(), dto.getProfession());

        client.setNomComplet(dto.getNomComplet());
        client.setTelephone(normalizePhone(dto.getTelephone()));
        client.setEmail(dto.getEmail());
        client.setPersonneAContacter(dto.getPersonneAContacter());
        client.setTelephoneSecondaire(normalizePhoneOptional(dto.getTelephoneSecondaire()));
        client.setAdresse(dto.getAdresse());
        client.setProfession(dto.getProfession());
        client.setPhotoUrl(PhotoUrlSanitizer.sanitize(dto.getPhotoUrl()));
        if (dto.getSignatureReference() != null) {
            client.setSignatureReference(dto.getSignatureReference());
        }
        if (dto.getMontantJournalier() != null) {
            trackChange(client, "montantJournalier",
                    str(client.getMontantJournalier()), str(dto.getMontantJournalier()));
            client.setMontantJournalier(dto.getMontantJournalier());
        }
        if (dto.getMarcheId() != null) {
            Marche marche = marcheRepository.findById(dto.getMarcheId())
                    .orElseThrow(() -> ApiException.notFound("Marché introuvable"));
            trackChange(client, "marche", labelMarche(client.getMarche()), labelMarche(marche));
            client.setMarche(marche);
        }
        if (dto.getStatut() != null && dto.getStatut() != client.getStatut()) {
            trackChange(client, "statut", client.getStatut().name(), dto.getStatut().name());
            client.setStatut(dto.getStatut());
        }

        auditService.log("MODIFICATION", "Client", client.getCode(), client.getNomComplet(), client.getAgence().getId());
        return toDtoWithCommission(clientRepository.save(client));
    }

    @Transactional
    public ClientDto desactiver(Long id, String motif) {
        Client client = getEntity(id);
        assertCanManageClient(client);
        if (client.getStatut() == StatutEntity.INACTIF) {
            throw ApiException.badRequest("Le client est déjà inactif");
        }
        client.setStatut(StatutEntity.INACTIF);
        client = clientRepository.save(client);
        String details = motif != null && !motif.isBlank() ? motif.trim() : "Désactivation du client";
        logHistorique(client, "DESACTIVATION", "statut", StatutEntity.ACTIF.name(),
                StatutEntity.INACTIF.name(), details);
        auditService.log("DESACTIVATION", "Client", client.getCode(), details, client.getAgence().getId());
        return toDtoWithCommission(client);
    }

    @Transactional
    public void supprimer(Long id) {
        Client client = getEntity(id);
        assertCanManageClient(client);
        if (client.getSoldeEpargne() != null && client.getSoldeEpargne().signum() > 0) {
            throw ApiException.badRequest(
                    "Impossible de supprimer un client en cours de collecte (solde > 0)");
        }
        if (restitutionRepository.existsByClientIdAndValideeFalse(client.getId())) {
            throw ApiException.conflict("Une restitution est en attente pour ce client");
        }
        client.setSupprime(true);
        client.setStatut(StatutEntity.INACTIF);
        clientRepository.save(client);
        logHistorique(client, "SUPPRESSION", null, null, null,
                "Client supprimé (code " + client.getCode() + ")");
        auditService.log("SUPPRESSION", "Client", client.getCode(), client.getNomComplet(), client.getAgence().getId());
    }

    @Transactional
    public ClientDto transferer(Long clientId, Map<String, Object> payload) {
        Client client = getEntity(clientId);
        Long nouvelAgentId = Long.valueOf(payload.get("agentId").toString());
        String motif = payload.getOrDefault("motif", "Transfert").toString();
        LocalDate date = payload.containsKey("date")
                ? LocalDate.parse(payload.get("date").toString())
                : LocalDate.now();

        Agent agentSource = client.getAgent();
        Agent agentCible = agentService.getEntity(nouvelAgentId);

        if (agentSource != null && agentSource.getId().equals(agentCible.getId())) {
            throw ApiException.badRequest("Le client est déjà affecté à cet agent");
        }

        affectationClientRepository.save(AffectationClient.builder()
                .client(client)
                .agentSource(agentSource)
                .agentCible(agentCible)
                .dateAffectation(date)
                .motif(motif)
                .effectuePar(currentUser())
                .build());

        client.setAgent(agentCible);
        client = clientRepository.save(client);

        auditService.log("TRANSFERT", "Client", client.getCode(),
                "Vers agent " + agentCible.getCode() + " - " + motif,
                client.getAgence().getId());
        logHistorique(client, "TRANSFERT", "agent",
                agentSource != null ? agentSource.getNomComplet() : null,
                agentCible.getNomComplet(), motif);

        String sourceNom = agentSource != null ? agentSource.getNomComplet() : "aucun";
        notificationService.notifyAgenceStaff(
                client.getAgence().getId(),
                "TRANSFERT",
                "Client transféré",
                "Le client " + client.getNomComplet() + " (" + client.getCode()
                        + ") a été transféré de " + sourceNom + " vers "
                        + agentCible.getNomComplet() + ". Motif : " + motif + ".",
                com.tontinemarche.domain.enums.RoleType.ADMIN_AGENCE,
                com.tontinemarche.domain.enums.RoleType.SUPER_ADMIN
        );
        java.util.List<Utilisateur> agentsUsers = new java.util.ArrayList<>();
        if (agentSource != null && agentSource.getUtilisateur() != null) {
            agentsUsers.add(agentSource.getUtilisateur());
        }
        if (agentCible.getUtilisateur() != null) {
            agentsUsers.add(agentCible.getUtilisateur());
        }
        notificationService.notifyUsers(
                agentsUsers,
                "TRANSFERT",
                "Client transféré",
                "Le client " + client.getNomComplet() + " (" + client.getCode()
                        + ") a été transféré de " + sourceNom + " vers "
                        + agentCible.getNomComplet() + "."
        );

        return toDtoWithCommission(client);
    }

    @Transactional(readOnly = true)
    public List<ClientHistoriqueDto> historique(Long clientId) {
        Client client = getEntity(clientId);
        assertCanViewClient(client);
        return clientHistoriqueRepository.findByClientIdOrderByDateHeureDesc(clientId).stream()
                .map(h -> ClientHistoriqueDto.builder()
                        .id(h.getId())
                        .typeAction(h.getTypeAction())
                        .champ(h.getChamp())
                        .valeurAvant(h.getValeurAvant())
                        .valeurApres(h.getValeurApres())
                        .details(h.getDetails())
                        .effectueParNom(h.getEffectuePar() != null ? h.getEffectuePar().getNomComplet() : null)
                        .dateHeure(h.getDateHeure())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AffectationClient> historiqueAffectations(Long clientId) {
        return affectationClientRepository.findByClientIdOrderByDateAffectationDesc(clientId);
    }

    public Client getEntity(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Client introuvable: " + id));
        if (client.isSupprime()) {
            throw ApiException.notFound("Client introuvable: " + id);
        }
        return client;
    }

    private ClientDto toDtoWithCommission(Client client) {
        ClientDto dto = EntityMapper.toDto(client);
        dto.setCommissionEstimee(computeCommissionEstimee(client));
        return dto;
    }

    private BigDecimal computeCommissionEstimee(Client client) {
        if (client.getMontantJournalier() == null || client.getMontantJournalier().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal total = client.getSoldeEpargne() != null ? client.getSoldeEpargne() : BigDecimal.ZERO;
            return commissionGrilleService
                    .calculerCommissionRestitution(
                            client.getAgence().getId(),
                            client.getMontantJournalier(),
                            total)
                    .montantCommission();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void assertCanViewClient(Client client) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Accès refusé");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE || principal.getRole() == RoleType.CAISSIER) {
            if (client.getAgence().getId().equals(principal.getAgenceId())) {
                return;
            }
            throw ApiException.forbidden("Accès refusé à ce client");
        }
        if (principal.getRole() == RoleType.AGENT) {
            Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                    .orElseThrow(() -> ApiException.forbidden("Profil collecteur introuvable"));
            if (client.getAgent() != null && client.getAgent().getId().equals(agent.getId())) {
                return;
            }
            throw ApiException.forbidden("Ce client n'est pas dans votre portefeuille");
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private void assertCanManageClient(Client client) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Accès refusé");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (client.isSupprime()) {
            throw ApiException.badRequest("Client supprimé");
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (client.getAgence().getId().equals(principal.getAgenceId())) {
                return;
            }
            throw ApiException.forbidden("Accès refusé à ce client");
        }
        if (principal.getRole() == RoleType.AGENT) {
            Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                    .orElseThrow(() -> ApiException.forbidden("Profil collecteur introuvable"));
            if (client.getAgent() != null && client.getAgent().getId().equals(agent.getId())) {
                return;
            }
            throw ApiException.forbidden("Ce client n'est pas dans votre portefeuille");
        }
        throw ApiException.forbidden("Vous ne pouvez pas modifier ce client");
    }

    private void trackChange(Client client, String champ, String avant, String apres) {
        if (Objects.equals(normalizeHistoriqueValue(avant), normalizeHistoriqueValue(apres))) {
            return;
        }
        logHistorique(client, "MODIFICATION", champ, avant, apres, null);
    }

    private void logHistorique(Client client, String typeAction, String champ,
                               String valeurAvant, String valeurApres, String details) {
        clientHistoriqueRepository.save(ClientHistorique.builder()
                .client(client)
                .typeAction(typeAction)
                .champ(champ)
                .valeurAvant(valeurAvant)
                .valeurApres(valeurApres)
                .details(details)
                .effectuePar(currentUser())
                .dateHeure(LocalDateTime.now())
                .build());
    }

    private String normalizeHistoriqueValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private String labelMarche(Marche marche) {
        return marche == null ? null : marche.getNom();
    }

    private String generateClientCode(Marche marche) {
        String marketCode = marche.getCode();
        if (marketCode == null || marketCode.isBlank()) {
            marketCode = MarcheCodeInitializer.generateCode(marche.getNom());
            marche.setCode(marketCode);
            marcheRepository.save(marche);
        }
        marketCode = marketCode.toUpperCase();
        String prefix = "TM-" + marketCode + "-";
        long seq = clientRepository.countByMarcheId(marche.getId()) + 1;
        return prefix + String.format("%05d", seq);
    }

    private void applyAgentContext(ClientDto dto) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (dto.getAgenceId() == null) {
                dto.setAgenceId(principal.getAgenceId());
            }
            if (dto.getAgentId() == null) {
                Utilisateur user = utilisateurRepository.findById(principal.getId())
                        .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
                Agent agent = agentService.ensureCollecteurProfile(user);
                dto.setAgentId(agent.getId());
            }
            return;
        }
        if (principal.getRole() != RoleType.AGENT) {
            return;
        }
        Utilisateur user = utilisateurRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        Agent agent = agentService.ensureCollecteurProfile(user);
        dto.setAgentId(agent.getId());
        dto.setAgenceId(agent.getAgence().getId());
        if (dto.getMarcheId() == null && agent.getMarches() != null && agent.getMarches().size() == 1) {
            dto.setMarcheId(agent.getMarches().get(0).getId());
        }
    }

    private void validateAgentMarche(Agent agent, Long marcheId) {
        if (marcheId == null || agent.getMarches() == null || agent.getMarches().isEmpty()) {
            return;
        }
        boolean allowed = agent.getMarches().stream().anyMatch(m -> m.getId().equals(marcheId));
        if (!allowed) {
            throw ApiException.badRequest("Ce marché n'est pas assigné à l'agent collecteur");
        }
    }

    private void validateTelephone(String tel, boolean required) {
        if (tel == null || tel.isBlank()) {
            if (required) {
                throw ApiException.badRequest("Le téléphone est obligatoire (8 chiffres)");
            }
            return;
        }
        if (!normalizePhone(tel).matches("\\d{8}")) {
            throw ApiException.badRequest("Le téléphone doit contenir exactement 8 chiffres");
        }
    }

    private void validateTelephoneOptional(String tel, String label) {
        if (tel == null || tel.isBlank()) {
            return;
        }
        if (!normalizePhone(tel).matches("\\d{8}")) {
            throw ApiException.badRequest("Le " + label + " doit contenir exactement 8 chiffres");
        }
    }

    private String normalizePhone(String tel) {
        return tel.replaceAll("\\D", "");
    }

    private String normalizePhoneOptional(String tel) {
        if (tel == null || tel.isBlank()) {
            return null;
        }
        return normalizePhone(tel);
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private Utilisateur currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return utilisateurRepository.findById(principal.getId()).orElse(null);
        }
        return null;
    }
}
