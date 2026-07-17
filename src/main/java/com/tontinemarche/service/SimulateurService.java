package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.SimulationAgenceDto;
import com.tontinemarche.dto.SimulationClientDto;
import com.tontinemarche.dto.SimulationResultatDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.ClientRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SimulateurService {

    private final ClientRepository clientRepository;
    private final AgenceRepository agenceRepository;
    private final CommissionGrilleService commissionGrilleService;
    private final PlatformSettingsService platformSettingsService;

    @Transactional(readOnly = true)
    public SimulationResultatDto simuler(LocalDate debut, LocalDate fin, Long requestedAgenceId) {
        if (debut == null || fin == null) {
            throw ApiException.badRequest("Les dates de début et de fin sont obligatoires");
        }
        if (debut.isAfter(fin)) {
            throw ApiException.badRequest("La date de début doit être antérieure ou égale à la date de fin");
        }

        long nombreJours = ChronoUnit.DAYS.between(debut, fin) + 1;
        Long scopedAgenceId = resolveAgenceScope(requestedAgenceId);

        List<Client> clients = scopedAgenceId != null
                ? clientRepository.findByAgenceIdAndStatutAndSupprimeFalse(scopedAgenceId, StatutEntity.ACTIF)
                : clientRepository.findByStatutAndSupprimeFalse(StatutEntity.ACTIF);

        Map<Long, AgenceBucket> buckets = new LinkedHashMap<>();
        List<SimulationClientDto> lignesClients = new ArrayList<>();

        BigDecimal jours = BigDecimal.valueOf(nombreJours);

        for (Client client : clients) {
            Agence agence = client.getAgence();
            if (agence == null || agence.getStatut() == StatutEntity.INACTIF) {
                continue;
            }

            BigDecimal mise = client.getMontantJournalier() != null ? client.getMontantJournalier() : BigDecimal.ZERO;
            BigDecimal collecte = mise.multiply(jours).setScale(2, RoundingMode.HALF_UP);

            CommissionGrilleService.CommissionResult commission =
                    commissionGrilleService.calculerCommissionRestitution(agence.getId(), mise, collecte);

            AgenceBucket bucket = buckets.computeIfAbsent(agence.getId(), id -> new AgenceBucket(agence));
            bucket.nombreClients++;
            bucket.totalMises = bucket.totalMises.add(mise);
            bucket.totalCollectes = bucket.totalCollectes.add(collecte);
            bucket.benefice = bucket.benefice.add(commission.montantCommission());

            lignesClients.add(SimulationClientDto.builder()
                    .clientId(client.getId())
                    .clientCode(client.getCode())
                    .clientNom(client.getNomComplet())
                    .agenceId(agence.getId())
                    .agenceNom(agence.getNom())
                    .montantJournalier(mise)
                    .collecteSimulee(collecte)
                    .commissionAgence(commission.montantCommission())
                    .trancheLabel(commission.trancheLabel())
                    .build());
        }

        List<SimulationAgenceDto> parAgence = new ArrayList<>();
        BigDecimal totalMises = BigDecimal.ZERO;
        BigDecimal totalCollectes = BigDecimal.ZERO;
        BigDecimal totalBenefice = BigDecimal.ZERO;
        BigDecimal totalCommissionAdmin = BigDecimal.ZERO;
        long totalClients = 0;

        for (AgenceBucket bucket : buckets.values()) {
            BigDecimal tauxAdmin = bucket.agence.getTauxCommissionAdmin() != null
                    ? bucket.agence.getTauxCommissionAdmin()
                    : platformSettingsService.getTauxCommissionAdminDefaut();
            BigDecimal commissionAdmin = bucket.totalCollectes.multiply(tauxAdmin).setScale(0, RoundingMode.HALF_UP);

            parAgence.add(SimulationAgenceDto.builder()
                    .agenceId(bucket.agence.getId())
                    .agenceNom(bucket.agence.getNom())
                    .tauxCommissionAdmin(tauxAdmin)
                    .nombreClients(bucket.nombreClients)
                    .totalMisesJournalieres(bucket.totalMises)
                    .totalCollectesSimulees(bucket.totalCollectes)
                    .beneficeAgence(bucket.benefice)
                    .commissionAdmin(commissionAdmin)
                    .build());

            totalClients += bucket.nombreClients;
            totalMises = totalMises.add(bucket.totalMises);
            totalCollectes = totalCollectes.add(bucket.totalCollectes);
            totalBenefice = totalBenefice.add(bucket.benefice);
            totalCommissionAdmin = totalCommissionAdmin.add(commissionAdmin);
        }

        parAgence.sort(Comparator.comparing(SimulationAgenceDto::getAgenceNom, String.CASE_INSENSITIVE_ORDER));
        lignesClients.sort(Comparator.comparing(SimulationClientDto::getClientNom, String.CASE_INSENSITIVE_ORDER));

        Agence agenceUnique = scopedAgenceId != null
                ? agenceRepository.findById(scopedAgenceId).orElse(null)
                : (buckets.size() == 1 ? buckets.values().iterator().next().agence : null);

        BigDecimal tauxAffiche = agenceUnique != null && agenceUnique.getTauxCommissionAdmin() != null
                ? agenceUnique.getTauxCommissionAdmin()
                : (scopedAgenceId == null && buckets.size() != 1
                ? null
                : platformSettingsService.getTauxCommissionAdminDefaut());

        return SimulationResultatDto.builder()
                .debut(debut)
                .fin(fin)
                .nombreJours(nombreJours)
                .agenceId(agenceUnique != null ? agenceUnique.getId() : scopedAgenceId)
                .agenceNom(agenceUnique != null ? agenceUnique.getNom() : (scopedAgenceId == null ? "Toutes les agences" : null))
                .tauxCommissionAdmin(tauxAffiche)
                .nombreClients(totalClients)
                .totalMisesJournalieres(totalMises)
                .totalCollectesSimulees(totalCollectes)
                .beneficeAgence(totalBenefice)
                .commissionAdmin(totalCommissionAdmin)
                .parAgence(parAgence)
                .clients(lignesClients)
                .build();
    }

    private Long resolveAgenceScope(Long requestedAgenceId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return requestedAgenceId;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null) {
                throw ApiException.forbidden("Agence non définie pour cet utilisateur");
            }
            if (requestedAgenceId != null && !principal.getAgenceId().equals(requestedAgenceId)) {
                throw ApiException.forbidden("Accès limité à votre agence");
            }
            return principal.getAgenceId();
        }
        throw ApiException.forbidden("Accès réservé à l'administration");
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static final class AgenceBucket {
        private final Agence agence;
        private long nombreClients;
        private BigDecimal totalMises = BigDecimal.ZERO;
        private BigDecimal totalCollectes = BigDecimal.ZERO;
        private BigDecimal benefice = BigDecimal.ZERO;

        private AgenceBucket(Agence agence) {
            this.agence = agence;
        }
    }
}
