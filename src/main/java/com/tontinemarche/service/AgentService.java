package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.entity.Marche;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.AgentDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.AgentRepository;
import com.tontinemarche.repository.ClientRepository;
import com.tontinemarche.repository.CollecteRepository;
import com.tontinemarche.repository.MarcheRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.util.PhotoUrlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgenceService agenceService;
    private final MarcheRepository marcheRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final CollecteRepository collecteRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AgentDto> findAll(Long agenceId) {
        // Filtres / listes opérationnelles : uniquement les agents d'agences actives
        List<Agent> agents = agenceId != null
                ? agentRepository.findByAgenceIdAndAgenceStatut(agenceId, StatutEntity.ACTIF)
                : agentRepository.findByAgenceStatut(StatutEntity.ACTIF);
        return agents.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public AgentDto findById(Long id) {
        return map(getEntity(id));
    }

    @Transactional
    public AgentDto create(AgentDto dto) {
        Agence agence = agenceService.getEntity(dto.getAgenceId());
        String code = dto.getCode() != null && !dto.getCode().isBlank()
                ? dto.getCode()
                : generateCode(agence);

        List<Marche> marches = resolveMarches(dto, agence);

        Utilisateur utilisateur = null;
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            if (utilisateurRepository.existsByUsername(dto.getUsername())) {
                throw ApiException.conflict("Nom d'utilisateur déjà utilisé");
            }
            utilisateur = utilisateurRepository.save(Utilisateur.builder()
                    .username(dto.getUsername())
                    .password(passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : "agent123"))
                    .nomComplet(dto.getNomComplet())
                    .telephone(dto.getTelephone())
                    .role(RoleType.AGENT)
                    .agence(agence)
                    .statut(StatutEntity.ACTIF)
                    .build());
        }

        Agent agent = Agent.builder()
                .code(code)
                .nomComplet(dto.getNomComplet())
                .telephone(dto.getTelephone())
                .photoUrl(PhotoUrlSanitizer.sanitize(dto.getPhotoUrl()))
                .agence(agence)
                .marches(new ArrayList<>(marches))
                .utilisateur(utilisateur)
                .statut(StatutEntity.ACTIF)
                .build();

        agent = agentRepository.save(agent);
        auditService.log("CREATION", "Agent", agent.getCode(), agent.getNomComplet(), agence.getId());
        return map(agent);
    }

    @Transactional
    public AgentDto update(Long id, AgentDto dto) {
        Agent agent = getEntity(id);
        agent.setNomComplet(dto.getNomComplet());
        agent.setTelephone(dto.getTelephone());
        agent.setPhotoUrl(PhotoUrlSanitizer.sanitize(dto.getPhotoUrl()));
        if (dto.getMarcheIds() != null || dto.getMarcheId() != null) {
            agent.setMarches(new ArrayList<>(resolveMarches(dto, agent.getAgence())));
        }
        if (dto.getStatut() != null) {
            agent.setStatut(dto.getStatut());
            if (agent.getUtilisateur() != null) {
                agent.getUtilisateur().setStatut(dto.getStatut());
            }
        }
        auditService.log("MODIFICATION", "Agent", agent.getCode(), agent.getNomComplet(), agent.getAgence().getId());
        return map(agentRepository.save(agent));
    }

    @Transactional
    public AgentDto suspendre(Long id) {
        Agent agent = getEntity(id);
        agent.setStatut(StatutEntity.SUSPENDU);
        if (agent.getUtilisateur() != null) {
            agent.getUtilisateur().setStatut(StatutEntity.SUSPENDU);
        }
        auditService.log("SUSPENSION", "Agent", agent.getCode(), null, agent.getAgence().getId());
        return map(agentRepository.save(agent));
    }

    public Agent getEntity(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Agent introuvable: " + id));
    }

    /**
     * Garantit un profil agent collecteur pour les utilisateurs AGENT ou ADMIN_AGENCE.
     * L'admin d'agence peut ainsi créer des clients et effectuer des collectes en son nom.
     */
    @Transactional
    public Agent ensureCollecteurProfile(Utilisateur user) {
        if (user == null || !isCollecteurRole(user.getRole())) {
            return null;
        }
        if (user.getAgence() == null) {
            throw ApiException.badRequest("Agence requise pour le profil collecteur");
        }
        return agentRepository.findByUtilisateurId(user.getId())
                .map(agent -> syncAdminAgenceMarches(agent, user))
                .orElseGet(() -> createCollecteurProfile(user));
    }

    @Transactional(readOnly = true)
    public Agent findCollecteurProfile(Long utilisateurId) {
        return agentRepository.findByUtilisateurId(utilisateurId).orElse(null);
    }

    private Agent createCollecteurProfile(Utilisateur user) {
        Agence agence = user.getAgence();
        List<Marche> marches = resolveMarchesForCollecteur(user);
        Agent agent = Agent.builder()
                .code(generateCode(agence))
                .nomComplet(user.getNomComplet())
                .telephone(user.getTelephone())
                .photoUrl(user.getPhotoUrl())
                .agence(agence)
                .marches(new ArrayList<>(marches))
                .utilisateur(user)
                .statut(user.getStatut() != null ? user.getStatut() : StatutEntity.ACTIF)
                .build();
        agent = agentRepository.save(agent);
        auditService.log("CREATION", "Agent", agent.getCode(),
                "Profil collecteur " + user.getRole().name(), agence.getId());
        return agent;
    }

    private Agent syncAdminAgenceMarches(Agent agent, Utilisateur user) {
        if (user.getRole() != RoleType.ADMIN_AGENCE) {
            return agent;
        }
        List<Marche> marches = resolveMarchesForCollecteur(user);
        agent.setMarches(new ArrayList<>(marches));
        return agentRepository.save(agent);
    }

    private List<Marche> resolveMarchesForCollecteur(Utilisateur user) {
        if (user.getRole() == RoleType.ADMIN_AGENCE) {
            return marcheRepository.findByAgenceId(user.getAgence().getId()).stream()
                    .filter(m -> m.getStatut() == StatutEntity.ACTIF)
                    .toList();
        }
        return new ArrayList<>();
    }

    private boolean isCollecteurRole(RoleType role) {
        return role == RoleType.AGENT || role == RoleType.ADMIN_AGENCE;
    }

    private AgentDto map(Agent agent) {
        long clients = clientRepository.countByAgentId(agent.getId());
        BigDecimal montant = collecteRepository.sumByAgentAndDate(agent.getId(), LocalDate.now());
        return EntityMapper.toDto(agent, clients, montant != null ? montant : BigDecimal.ZERO);
    }

    private List<Marche> resolveMarches(AgentDto dto, Agence agence) {
        List<Long> ids = dto.getMarcheIds();
        if (ids == null || ids.isEmpty()) {
            if (dto.getMarcheId() != null) {
                ids = List.of(dto.getMarcheId());
            } else {
                return new ArrayList<>();
            }
        }
        List<Marche> marches = new ArrayList<>();
        for (Long id : ids) {
            Marche marche = marcheRepository.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Marché introuvable"));
            if (!marche.getAgence().getId().equals(agence.getId())) {
                throw ApiException.badRequest("Le marché " + marche.getNom() + " n'appartient pas à l'agence");
            }
            marches.add(marche);
        }
        return marches;
    }

    private String generateCode(Agence agence) {
        long count = agentRepository.countByAgenceId(agence.getId()) + 1;
        return agence.getCode() + "-A" + String.format("%03d", count);
    }
}
