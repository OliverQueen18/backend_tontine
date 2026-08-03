package com.tontinemarche.service;

import com.tontinemarche.domain.entity.PlatformSettings;
import com.tontinemarche.dto.PlatformSettingsDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;
    private final SmsGatewayService smsGatewayService;

    @Transactional(readOnly = true)
    public PlatformSettings get() {
        return repository.findById(1L).orElseGet(this::createDefault);
    }

    @Transactional(readOnly = true)
    public PlatformSettingsDto getDto() {
        return toDto(get());
    }

    @Transactional
    public PlatformSettingsDto update(PlatformSettingsDto dto) {
        PlatformSettings settings = get();
        if (dto.getFraisCreationAgence() != null) {
            settings.setFraisCreationAgence(dto.getFraisCreationAgence());
        }
        if (dto.getTelephonePaiementMobile() != null) {
            settings.setTelephonePaiementMobile(dto.getTelephonePaiementMobile().trim());
        }
        if (dto.getTauxCommissionAdminDefaut() != null) {
            validateTauxCommissionAdmin(dto.getTauxCommissionAdminDefaut());
            settings.setTauxCommissionAdminDefaut(dto.getTauxCommissionAdminDefaut());
        }
        if (dto.getSmsNotificationsEnabled() != null) {
            if (Boolean.TRUE.equals(dto.getSmsNotificationsEnabled()) && !smsGatewayService.isReady()) {
                throw ApiException.badRequest(
                        "Impossible d'activer les SMS : la passerelle n'est pas configurée "
                                + "(SMS_GATEWAY_ENABLED et SMS_GATEWAY_API_KEY)");
            }
            settings.setSmsNotificationsEnabled(dto.getSmsNotificationsEnabled());
        }
        return toDto(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public BigDecimal getTauxCommissionAdminDefaut() {
        BigDecimal taux = get().getTauxCommissionAdminDefaut();
        return taux != null ? taux : new BigDecimal("0.0500");
    }

    @Transactional(readOnly = true)
    public boolean isSmsNotificationsEnabled() {
        return get().isSmsNotificationsEnabled();
    }

    private void validateTauxCommissionAdmin(BigDecimal taux) {
        if (taux.compareTo(BigDecimal.ZERO) < 0 || taux.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.badRequest("Le taux de commission admin doit être entre 0 % et 100 %");
        }
    }

    @Transactional
    public void ensureDefault() {
        if (repository.findById(1L).isEmpty()) {
            createDefault();
        }
    }

    private PlatformSettings createDefault() {
        return repository.save(PlatformSettings.builder()
                .id(1L)
                .fraisCreationAgence(new BigDecimal("50000"))
                .telephonePaiementMobile("+223 70 00 00 00")
                .tauxCommissionAdminDefaut(new BigDecimal("0.0500"))
                .smsNotificationsEnabled(false)
                .build());
    }

    private PlatformSettingsDto toDto(PlatformSettings s) {
        return PlatformSettingsDto.builder()
                .fraisCreationAgence(s.getFraisCreationAgence())
                .telephonePaiementMobile(s.getTelephonePaiementMobile())
                .tauxCommissionAdminDefaut(s.getTauxCommissionAdminDefaut())
                .smsNotificationsEnabled(s.isSmsNotificationsEnabled())
                .smsGatewayReady(smsGatewayService.isReady())
                .build();
    }
}
