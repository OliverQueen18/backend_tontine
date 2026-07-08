package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.dto.AgenceDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgenceService {

    private final AgenceRepository agenceRepository;
    private final AuditService auditService;
    private final CommissionGrilleService commissionGrilleService;
    private final PlatformSettingsService platformSettingsService;

    @Transactional(readOnly = true)
    public List<AgenceDto> findAll() {
        return agenceRepository.findAll().stream().map(EntityMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AgenceDto findById(Long id) {
        assertCanAccessAgence(id);
        return EntityMapper.toDto(getEntity(id));
    }

    @Transactional
    public AgenceDto create(AgenceDto dto) {
        String code = dto.getCode() != null && !dto.getCode().isBlank()
                ? dto.getCode().toUpperCase()
                : generateCode(dto.getVille() != null ? dto.getVille() : dto.getNom());

        if (agenceRepository.existsByCode(code)) {
            throw ApiException.conflict("Code agence déjà utilisé: " + code);
        }

        Agence agence = Agence.builder()
                .code(code)
                .nom(dto.getNom())
                .responsable(dto.getResponsable())
                .telephone(dto.getTelephone())
                .email(dto.getEmail())
                .adresse(dto.getAdresse())
                .ville(dto.getVille())
                .logoUrl(trimOrNull(dto.getLogoUrl()))
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .tauxCommission(dto.getTauxCommission() != null ? dto.getTauxCommission() : new BigDecimal("0.1000"))
                .tauxCommissionAdmin(dto.getTauxCommissionAdmin() != null
                        ? dto.getTauxCommissionAdmin()
                        : platformSettingsService.getTauxCommissionAdminDefaut())
                .statut(StatutEntity.ACTIF)
                .build();

        agence = agenceRepository.save(agence);
        commissionGrilleService.ensureDefaultGrille(agence);
        auditService.log("CREATION", "Agence", agence.getCode(), agence.getNom(), agence.getId());
        return EntityMapper.toDto(agence);
    }

    @Transactional
    public AgenceDto update(Long id, AgenceDto dto) {
        UserPrincipal principal = requirePrincipal();
        assertCanAccessAgence(id);
        Agence agence = getEntity(id);

        agence.setNom(dto.getNom());
        agence.setResponsable(dto.getResponsable());
        agence.setTelephone(dto.getTelephone());
        agence.setEmail(dto.getEmail());
        agence.setAdresse(dto.getAdresse());
        agence.setVille(dto.getVille());
        agence.setLatitude(dto.getLatitude());
        agence.setLongitude(dto.getLongitude());
        if (dto.getLogoUrl() != null) {
            agence.setLogoUrl(trimOrNull(dto.getLogoUrl()));
        }

        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            if (dto.getTauxCommission() != null) {
                agence.setTauxCommission(dto.getTauxCommission());
            }
            if (dto.getTauxCommissionAdmin() != null) {
                agence.setTauxCommissionAdmin(dto.getTauxCommissionAdmin());
            }
            if (dto.getStatut() != null) {
                agence.setStatut(dto.getStatut());
            }
        }

        auditService.log("MODIFICATION", "Agence", agence.getCode(), agence.getNom(), agence.getId());
        return EntityMapper.toDto(agenceRepository.save(agence));
    }

    @Transactional
    public AgenceDto desactiver(Long id) {
        Agence agence = getEntity(id);
        agence.setStatut(StatutEntity.INACTIF);
        auditService.log("DESACTIVATION", "Agence", agence.getCode(), null, agence.getId());
        return EntityMapper.toDto(agenceRepository.save(agence));
    }

    public Agence getEntity(Long id) {
        return agenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable: " + id));
    }

    private String generateCode(String source) {
        String prefix = source.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (prefix.length() < 3) {
            prefix = (prefix + "XXX").substring(0, 3);
        } else {
            prefix = prefix.substring(0, 3);
        }
        int i = 1;
        String code = prefix;
        while (agenceRepository.existsByCode(code)) {
            code = prefix + i++;
        }
        return code;
    }

    private void assertCanAccessAgence(Long agenceId) {
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
    }

    private UserPrincipal requirePrincipal() {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Non authentifié");
        }
        return principal;
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
