package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.SensOperation;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.DashboardDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.*;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AgenceRepository agenceRepository;
    private final AgentRepository agentRepository;
    private final ClientRepository clientRepository;
    private final CollecteRepository collecteRepository;
    private final RestitutionRepository restitutionRepository;
    private final DepenseRepository depenseRepository;
    private final CaisseRepository caisseRepository;
    private final PlatformSettingsService platformSettingsService;

    @Transactional(readOnly = true)
    public DashboardDto getDashboard(Long requestedAgenceId, Long requestedAgentId, LocalDate debut, LocalDate fin) {
        UserPrincipal principal = currentPrincipal();
        DashboardScope scope = resolveScope(principal, requestedAgenceId, requestedAgentId);

        LocalDate today = LocalDate.now();
        LocalDate periodeDebut = debut != null ? debut : today.withDayOfMonth(1);
        LocalDate periodeFin = fin != null ? fin : today;
        if (periodeDebut.isAfter(periodeFin)) {
            throw ApiException.badRequest("La date de début doit être antérieure à la date de fin");
        }

        LocalDateTime debutMoisDt = periodeDebut.atStartOfDay();
        LocalDateTime finMoisDt = periodeFin.atTime(LocalTime.MAX);

        boolean vueAgent = scope.agentId() != null;
        boolean vueGlobale = scope.agenceId() == null && scope.agentId() == null;

        long nombreAgences = vueGlobale ? agenceRepository.countByStatut(StatutEntity.ACTIF) : 1;
        long nombreAgents;
        long nombreClients;
        if (vueAgent) {
            nombreAgents = 1;
            nombreClients = clientRepository.countByAgentId(scope.agentId());
        } else if (scope.agenceId() != null) {
            nombreAgents = agentRepository.countByAgenceId(scope.agenceId());
            nombreClients = clientRepository.countByAgenceId(scope.agenceId());
        } else {
            nombreAgents = agentRepository.countByStatut(StatutEntity.ACTIF);
            nombreClients = clientRepository.countByStatut(StatutEntity.ACTIF);
        }

        BigDecimal collectesJour = sumCollectes(scope, today, today);
        BigDecimal collectesMois = sumCollectes(scope, periodeDebut, periodeFin);
        BigDecimal collectesPeriode = collectesMois;

        BigDecimal commissions = sumCommissions(scope, debutMoisDt, finMoisDt);
        BigDecimal depenses = sumDepenses(scope, periodeDebut, periodeFin);
        BigDecimal benefice = vueAgent ? BigDecimal.ZERO : commissions.subtract(depenses != null ? depenses : BigDecimal.ZERO);

        BigDecimal montantOperationsEntree = vueAgent ? BigDecimal.ZERO : sumOperations(scope, periodeDebut, periodeFin, SensOperation.ENTREE);
        BigDecimal montantOperationsSortie = vueAgent ? BigDecimal.ZERO : sumOperations(scope, periodeDebut, periodeFin, SensOperation.SORTIE);

        BigDecimal commissionAdmin = BigDecimal.ZERO;
        if (!vueAgent) {
            if (scope.agenceId() != null) {
                BigDecimal taux = agenceRepository.findById(scope.agenceId())
                        .map(a -> a.getTauxCommissionAdmin())
                        .orElse(platformSettingsService.getTauxCommissionAdminDefaut());
                commissionAdmin = montantOperationsEntree.multiply(taux).setScale(0, RoundingMode.HALF_UP);
            } else if (vueGlobale) {
                commissionAdmin = computeGlobalCommissionAdmin(periodeDebut, periodeFin);
            }
        }

        BigDecimal soldeCaisse = BigDecimal.ZERO;
        if (!vueAgent && scope.agenceId() != null) {
            soldeCaisse = caisseRepository.findByAgenceIdAndDateCaisse(scope.agenceId(), today)
                    .map(c -> c.getSoldeTheorique())
                    .orElse(BigDecimal.ZERO);
        }

        long nombreSignatures = countSignatures(scope);

        var dernieresCollectes = loadRecentCollectes(scope).stream().map(EntityMapper::toDto).toList();
        var dernieresRestitutions = loadRecentRestitutions(scope).stream().map(EntityMapper::toDto).toList();
        var dernieresDepenses = vueAgent
                ? List.<com.tontinemarche.dto.DepenseDto>of()
                : loadRecentDepenses(scope).stream().map(EntityMapper::toDto).toList();

        List<Map<String, Object>> topAgents = vueAgent ? List.of() : buildTopAgents(scope, today);
        List<Map<String, Object>> evolution = buildEvolution(scope, periodeDebut, periodeFin);

        BigDecimal montantDepensesPeriode = vueAgent ? BigDecimal.ZERO : (depenses != null ? depenses : BigDecimal.ZERO);
        BigDecimal montantCommissionsPeriode = vueAgent ? BigDecimal.ZERO : (commissions != null ? commissions : BigDecimal.ZERO);

        return DashboardDto.builder()
                .scopeLabel(scope.label())
                .periodeDebut(periodeDebut)
                .periodeFin(periodeFin)
                .vueAgent(vueAgent)
                .nombreAgences(nombreAgences)
                .nombreAgents(nombreAgents)
                .nombreClients(nombreClients)
                .collectesJour(collectesJour != null ? collectesJour : BigDecimal.ZERO)
                .collectesMois(collectesMois != null ? collectesMois : BigDecimal.ZERO)
                .collectesPeriode(collectesPeriode != null ? collectesPeriode : BigDecimal.ZERO)
                .beneficeGlobal(benefice)
                .commissionAdmin(commissionAdmin)
                .nombreSignatures(nombreSignatures)
                .soldeCaisse(soldeCaisse)
                .dernieresCollectes(dernieresCollectes)
                .dernieresRestitutions(dernieresRestitutions)
                .dernieresDepenses(dernieresDepenses)
                .montantCommissionsPeriode(montantCommissionsPeriode)
                .montantDepensesPeriode(montantDepensesPeriode)
                .montantOperationsEntree(montantOperationsEntree)
                .montantOperationsSortie(montantOperationsSortie)
                .topAgents(topAgents)
                .evolutionCollectes(evolution)
                .build();
    }

    private DashboardScope resolveScope(UserPrincipal principal, Long requestedAgenceId, Long requestedAgentId) {
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }

        return switch (principal.getRole()) {
            case AGENT -> {
                Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                        .orElseThrow(() -> ApiException.badRequest("Profil agent introuvable"));
                yield new DashboardScope(
                        agent.getAgence().getId(),
                        agent.getId(),
                        "Mes performances — " + agent.getNomComplet()
                );
            }
            case ADMIN_AGENCE, CAISSIER, AUDITEUR -> {
                Long agenceId = principal.getAgenceId();
                if (agenceId == null) {
                    throw ApiException.forbidden("Agence non définie pour cet utilisateur");
                }
                if (requestedAgenceId != null && !requestedAgenceId.equals(agenceId)) {
                    throw ApiException.forbidden("Accès limité à votre agence");
                }
                Long agentId = validateAgentInAgence(requestedAgentId, agenceId);
                String label = agentId != null
                        ? "Agent — " + agentRepository.findById(agentId).map(Agent::getNomComplet).orElse("")
                        : agenceRepository.findById(agenceId).map(a -> "Agence — " + a.getNom()).orElse("Mon agence");
                yield new DashboardScope(agenceId, agentId, label);
            }
            case SUPER_ADMIN -> {
                Long agenceId = requestedAgenceId;
                Long agentId = validateAgentInAgence(requestedAgentId, agenceId);
                String label;
                if (agentId != null) {
                    label = "Agent — " + agentRepository.findById(agentId).map(Agent::getNomComplet).orElse("");
                } else if (agenceId != null) {
                    label = agenceRepository.findById(agenceId).map(a -> "Agence — " + a.getNom()).orElse("Agence");
                } else {
                    label = "Vue globale";
                }
                yield new DashboardScope(agenceId, agentId, label);
            }
        };
    }

    private Long validateAgentInAgence(Long agentId, Long agenceId) {
        if (agentId == null) {
            return null;
        }
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound("Agent introuvable"));
        if (agenceId != null && !agent.getAgence().getId().equals(agenceId)) {
            throw ApiException.badRequest("L'agent n'appartient pas à l'agence sélectionnée");
        }
        return agentId;
    }

    private BigDecimal sumCollectes(DashboardScope scope, LocalDate debut, LocalDate fin) {
        if (scope.agentId() != null) {
            return collecteRepository.sumByAgentBetween(scope.agentId(), debut, fin);
        }
        if (scope.agenceId() != null) {
            return collecteRepository.sumByAgenceBetween(scope.agenceId(), debut, fin);
        }
        return collecteRepository.sumBetween(debut, fin);
    }

    private BigDecimal sumCommissions(DashboardScope scope, LocalDateTime debut, LocalDateTime fin) {
        if (scope.agentId() != null) {
            return BigDecimal.ZERO;
        }
        if (scope.agenceId() != null) {
            return restitutionRepository.sumCommissionByAgenceBetween(scope.agenceId(), debut, fin);
        }
        return restitutionRepository.sumCommissionBetween(debut, fin);
    }

    private BigDecimal sumDepenses(DashboardScope scope, LocalDate debut, LocalDate fin) {
        if (scope.agentId() != null) {
            return BigDecimal.ZERO;
        }
        if (scope.agenceId() != null) {
            return depenseRepository.sumByAgenceBetween(scope.agenceId(), debut, fin);
        }
        return depenseRepository.sumBetween(debut, fin);
    }

    private long countSignatures(DashboardScope scope) {
        if (scope.agentId() != null) {
            return collecteRepository.countByAgentIdAndSignatureClientIsNotNull(scope.agentId());
        }
        return collecteRepository.countBySignatureClientIsNotNull();
    }

    private List<com.tontinemarche.domain.entity.Collecte> loadRecentCollectes(DashboardScope scope) {
        if (scope.agentId() != null) {
            return collecteRepository.findTop10ByAgentIdOrderByDateHeureDesc(scope.agentId());
        }
        if (scope.agenceId() != null) {
            return collecteRepository.findTop10ByAgenceIdOrderByDateHeureDesc(scope.agenceId());
        }
        return collecteRepository.findTop10ByOrderByDateHeureDesc();
    }

    private List<com.tontinemarche.domain.entity.Restitution> loadRecentRestitutions(DashboardScope scope) {
        if (scope.agentId() != null) {
            return restitutionRepository.findTop10ByClient_Agent_IdOrderByDateHeureDesc(scope.agentId());
        }
        if (scope.agenceId() != null) {
            return restitutionRepository.findTop10ByAgenceIdOrderByDateHeureDesc(scope.agenceId());
        }
        return restitutionRepository.findTop10ByOrderByDateHeureDesc();
    }

    private List<com.tontinemarche.domain.entity.Depense> loadRecentDepenses(DashboardScope scope) {
        if (scope.agenceId() != null) {
            return depenseRepository.findTop10ByAgenceIdOrderByDateDepenseDesc(scope.agenceId());
        }
        return depenseRepository.findTop10ByOrderByDateDepenseDesc();
    }

    private BigDecimal sumOperations(DashboardScope scope, LocalDate debut, LocalDate fin, SensOperation sens) {
        if (scope.agentId() != null) {
            return BigDecimal.ZERO;
        }
        if (scope.agenceId() != null) {
            return depenseRepository.sumByAgenceBetweenAndSens(scope.agenceId(), debut, fin, sens);
        }
        return depenseRepository.sumBetweenAndSens(debut, fin, sens);
    }

    private BigDecimal computeGlobalCommissionAdmin(LocalDate debut, LocalDate fin) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal defaultTaux = platformSettingsService.getTauxCommissionAdminDefaut();
        for (var agence : agenceRepository.findByStatut(StatutEntity.ACTIF)) {
            BigDecimal entrees = depenseRepository.sumByAgenceBetweenAndSens(
                    agence.getId(), debut, fin, SensOperation.ENTREE);
            if (entrees == null) {
                entrees = BigDecimal.ZERO;
            }
            BigDecimal taux = agence.getTauxCommissionAdmin() != null
                    ? agence.getTauxCommissionAdmin()
                    : defaultTaux;
            total = total.add(entrees.multiply(taux));
        }
        return total.setScale(0, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> buildTopAgents(DashboardScope scope, LocalDate today) {
        List<Map<String, Object>> topAgents = new ArrayList<>();
        List<Agent> agents;
        if (scope.agentId() != null) {
            agents = List.of(agentRepository.findById(scope.agentId()).orElseThrow());
        } else if (scope.agenceId() != null) {
            agents = agentRepository.findByAgenceId(scope.agenceId());
        } else {
            agents = agentRepository.findAll();
        }
        for (var agent : agents) {
            BigDecimal montant = collecteRepository.sumByAgentAndDate(agent.getId(), today);
            Map<String, Object> row = new HashMap<>();
            row.put("agentId", agent.getId());
            row.put("nom", agent.getNomComplet());
            row.put("code", agent.getCode());
            row.put("montant", montant != null ? montant : BigDecimal.ZERO);
            topAgents.add(row);
        }
        topAgents.sort((a, b) -> ((BigDecimal) b.get("montant")).compareTo((BigDecimal) a.get("montant")));
        if (topAgents.size() > 5) {
            topAgents = topAgents.subList(0, 5);
        }
        return topAgents;
    }

    private List<Map<String, Object>> buildEvolution(DashboardScope scope, LocalDate debut, LocalDate fin) {
        List<Map<String, Object>> evolution = new ArrayList<>();
        long days = java.time.temporal.ChronoUnit.DAYS.between(debut, fin);
        int points = (int) Math.min(days + 1, 31);
        LocalDate start = days > 30 ? fin.minusDays(30) : debut;
        for (int i = 0; i < points; i++) {
            LocalDate d = start.plusDays(i);
            if (d.isAfter(fin)) break;
            BigDecimal m = sumCollectes(scope, d, d);
            Map<String, Object> point = new HashMap<>();
            point.put("date", d.toString());
            point.put("montant", m != null ? m : BigDecimal.ZERO);
            evolution.add(point);
        }
        return evolution;
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private record DashboardScope(Long agenceId, Long agentId, String label) {}
}
