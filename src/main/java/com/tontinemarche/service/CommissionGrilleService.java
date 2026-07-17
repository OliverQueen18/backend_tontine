package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.GrilleCommissionLigne;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.dto.GrilleCommissionLigneDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.GrilleCommissionRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommissionGrilleService {

    private final GrilleCommissionRepository grilleRepository;
    private final AgenceRepository agenceRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<GrilleCommissionLigneDto> findByAgence(Long agenceId) {
        assertCanManage(agenceId);
        return grilleRepository.findByAgenceIdOrderByOrdreAscMontantMinAsc(agenceId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<GrilleCommissionLigneDto> saveGrille(Long agenceId, List<GrilleCommissionLigneDto> lignesDto) {
        assertCanManage(agenceId);
        Agence agence = getAgence(agenceId);
        List<GrilleCommissionLigneDto> validated = validateAndSort(lignesDto);

        grilleRepository.deleteByAgenceId(agenceId);

        List<GrilleCommissionLigne> saved = new ArrayList<>();
        int ordre = 0;
        for (GrilleCommissionLigneDto dto : validated) {
            saved.add(grilleRepository.save(GrilleCommissionLigne.builder()
                    .agence(agence)
                    .montantMin(dto.getMontantMin())
                    .montantMax(dto.getMontantMax())
                    .montantCommission(dto.getMontantCommission())
                    .ordre(ordre++)
                    .build()));
        }

        auditService.log("MODIFICATION", "GrilleCommission", agence.getCode(),
                saved.size() + " tranche(s)", agenceId);

        return saved.stream().map(this::toDto).toList();
    }

    /**
     * Commission de restitution : la tranche de grille se base sur le montant journalier (mise),
     * pas sur le total collecté. Sans grille, le taux s'applique au total collecté.
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public CommissionResult calculerCommissionRestitution(Long agenceId,
                                                          BigDecimal montantJournalier,
                                                          BigDecimal totalCollecte) {
        List<GrilleCommissionLigne> lignes = grilleRepository
                .findByAgenceIdOrderByOrdreAscMontantMinAsc(agenceId);
        if (!lignes.isEmpty()) {
            BigDecimal base = montantJournalier != null ? montantJournalier : BigDecimal.ZERO;
            return calculerCommission(agenceId, base);
        }
        return calculerCommission(agenceId, totalCollecte);
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public CommissionResult calculerCommission(Long agenceId, BigDecimal montantReference) {
        if (montantReference == null || montantReference.compareTo(BigDecimal.ZERO) < 0) {
            montantReference = BigDecimal.ZERO;
        }

        List<GrilleCommissionLigne> lignes = grilleRepository
                .findByAgenceIdOrderByOrdreAscMontantMinAsc(agenceId);

        if (!lignes.isEmpty()) {
            for (GrilleCommissionLigne ligne : lignes) {
                if (matches(montantReference, ligne)) {
                    return new CommissionResult(
                            ligne.getMontantCommission().setScale(0, RoundingMode.HALF_UP),
                            formatTranche(ligne),
                            true
                    );
                }
            }
            // Montant au-delà de la dernière tranche plafonnée : on applique la dernière
            // (évite de casser listes/simulateur si la grille n'a pas de « sans plafond »).
            GrilleCommissionLigne last = lignes.get(lignes.size() - 1);
            if (last.getMontantMax() != null && montantReference.compareTo(last.getMontantMax()) > 0) {
                return new CommissionResult(
                        last.getMontantCommission().setScale(0, RoundingMode.HALF_UP),
                        formatTranche(last) + " (plafond étendu)",
                        true
                );
            }
            throw ApiException.badRequest(
                    "Aucune tranche de commission ne correspond au montant : "
                            + montantReference.stripTrailingZeros().toPlainString() + " FCFA");
        }

        Agence agence = getAgence(agenceId);
        BigDecimal taux = agence.getTauxCommission() != null ? agence.getTauxCommission() : new BigDecimal("0.10");
        BigDecimal commission = montantReference.multiply(taux).setScale(0, RoundingMode.HALF_UP);
        return new CommissionResult(
                commission,
                "Taux " + taux.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + " %",
                false
        );
    }

    @Transactional
    public void ensureDefaultGrille(Agence agence) {
        if (grilleRepository.countByAgenceId(agence.getId()) > 0) {
            return;
        }
        replaceWithDefaultGrille(agence);
    }

    /** Remplace toute la grille de l'agence par la grille par défaut métier. */
    @Transactional
    public void replaceWithDefaultGrille(Agence agence) {
        grilleRepository.deleteByAgenceId(agence.getId());
        List<GrilleCommissionLigne> defaults = List.of(
                ligne(agence, 0, new BigDecimal("500"), new BigDecimal("500"), 0),
                ligne(agence, new BigDecimal("501"), new BigDecimal("1000"), new BigDecimal("1000"), 1),
                ligne(agence, new BigDecimal("1001"), new BigDecimal("2000"), new BigDecimal("2000"), 2),
                ligne(agence, new BigDecimal("2001"), new BigDecimal("5000"), new BigDecimal("5000"), 3),
                ligne(agence, new BigDecimal("5001"), new BigDecimal("10000"), new BigDecimal("10000"), 4),
                ligne(agence, new BigDecimal("10001"), new BigDecimal("20000"), new BigDecimal("20000"), 5),
                ligne(agence, new BigDecimal("20001"), null, new BigDecimal("25000"), 6)
        );
        grilleRepository.saveAll(defaults);
    }

    @Transactional
    public List<GrilleCommissionLigneDto> resetToDefaultGrille(Long agenceId) {
        assertCanManage(agenceId);
        Agence agence = getAgence(agenceId);
        replaceWithDefaultGrille(agence);
        auditService.log("REINITIALISATION", "GrilleCommission", agence.getCode(),
                "Grille remise à la valeur par défaut", agenceId);
        return findByAgence(agenceId);
    }

    private GrilleCommissionLigne ligne(Agence agence, Object min, BigDecimal max, BigDecimal commission, int ordre) {
        BigDecimal montantMin = min instanceof BigDecimal b ? b : new BigDecimal(min.toString());
        return GrilleCommissionLigne.builder()
                .agence(agence)
                .montantMin(montantMin)
                .montantMax(max)
                .montantCommission(commission)
                .ordre(ordre)
                .build();
    }

    private boolean matches(BigDecimal total, GrilleCommissionLigne ligne) {
        if (total.compareTo(ligne.getMontantMin()) < 0) {
            return false;
        }
        return ligne.getMontantMax() == null || total.compareTo(ligne.getMontantMax()) <= 0;
    }

    private String formatTranche(GrilleCommissionLigne ligne) {
        String min = ligne.getMontantMin().stripTrailingZeros().toPlainString();
        String max = ligne.getMontantMax() != null
                ? ligne.getMontantMax().stripTrailingZeros().toPlainString()
                : "∞";
        String comm = ligne.getMontantCommission().stripTrailingZeros().toPlainString();
        return min + " – " + max + " FCFA → " + comm + " FCFA";
    }

    private List<GrilleCommissionLigneDto> validateAndSort(List<GrilleCommissionLigneDto> lignesDto) {
        if (lignesDto == null || lignesDto.isEmpty()) {
            throw ApiException.badRequest("La grille doit contenir au moins une tranche");
        }

        List<GrilleCommissionLigneDto> sorted = lignesDto.stream()
                .sorted(Comparator.comparing(GrilleCommissionLigneDto::getMontantMin,
                        Comparator.nullsLast(BigDecimal::compareTo)))
                .toList();

        GrilleCommissionLigneDto first = sorted.get(0);
        if (first.getMontantMin() == null || first.getMontantMin().compareTo(BigDecimal.ZERO) != 0) {
            throw ApiException.badRequest("La première tranche doit commencer à 0 FCFA");
        }

        for (int i = 0; i < sorted.size(); i++) {
            GrilleCommissionLigneDto dto = sorted.get(i);
            if (dto.getMontantMin() == null) {
                throw ApiException.badRequest("Le montant minimum est obligatoire (tranche " + (i + 1) + ")");
            }
            if (dto.getMontantCommission() == null || dto.getMontantCommission().compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest("La commission doit être positive (tranche " + (i + 1) + ")");
            }
            boolean isLast = i == sorted.size() - 1;
            if (!isLast) {
                if (dto.getMontantMax() == null) {
                    throw ApiException.badRequest("Seule la dernière tranche peut être sans plafond");
                }
                if (dto.getMontantMax().compareTo(dto.getMontantMin()) < 0) {
                    throw ApiException.badRequest("Le maximum doit être ≥ au minimum (tranche " + (i + 1) + ")");
                }
                GrilleCommissionLigneDto next = sorted.get(i + 1);
                BigDecimal expectedMin = dto.getMontantMax().add(BigDecimal.ONE);
                if (next.getMontantMin().compareTo(expectedMin) != 0) {
                    throw ApiException.badRequest("Les tranches doivent se suivre sans trou ni chevauchement");
                }
            } else if (dto.getMontantMax() != null && dto.getMontantMax().compareTo(dto.getMontantMin()) < 0) {
                throw ApiException.badRequest("Le maximum doit être ≥ au minimum sur la dernière tranche");
            }
            dto.setOrdre(i);
        }
        return sorted;
    }

    private void assertCanManage(Long agenceId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null || !principal.getAgenceId().equals(agenceId)) {
                throw ApiException.forbidden("Accès limité à votre agence");
            }
            return;
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private Agence getAgence(Long agenceId) {
        return agenceRepository.findById(agenceId)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable"));
    }

    private GrilleCommissionLigneDto toDto(GrilleCommissionLigne entity) {
        return GrilleCommissionLigneDto.builder()
                .id(entity.getId())
                .montantMin(entity.getMontantMin())
                .montantMax(entity.getMontantMax())
                .montantCommission(entity.getMontantCommission())
                .ordre(entity.getOrdre())
                .build();
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public record CommissionResult(BigDecimal montantCommission, String trancheLabel, boolean fromGrille) {}
}
