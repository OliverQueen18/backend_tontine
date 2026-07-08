package com.tontinemarche.controller;

import com.tontinemarche.dto.PlatformSettingsDto;
import com.tontinemarche.service.DemandeInscriptionService;
import com.tontinemarche.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminPlatformController {

    private final PlatformSettingsService platformSettingsService;
    private final DemandeInscriptionService demandeInscriptionService;

    @GetMapping("/api/admin/platform-settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformSettingsDto getSettings() {
        return platformSettingsService.getDto();
    }

    @PutMapping("/api/admin/platform-settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformSettingsDto updateSettings(@Valid @RequestBody PlatformSettingsDto dto) {
        return platformSettingsService.update(dto);
    }

    @GetMapping("/api/admin/demandes-inscription/en-attente/count")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Long> countDemandesEnAttente() {
        return Map.of("count", demandeInscriptionService.countEnAttente());
    }

    @GetMapping("/api/admin/demandes-inscription")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Map<String, Object>> listDemandes(@RequestParam(required = false) String statut) {
        return demandeInscriptionService.findAll(statut);
    }

    @PostMapping("/api/admin/demandes-inscription/{id}/approuver")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> approuver(@PathVariable Long id) {
        return demandeInscriptionService.approuver(id);
    }

    @PostMapping("/api/admin/demandes-inscription/{id}/rejeter")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> rejeter(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return demandeInscriptionService.rejeter(id, body.get("motif"));
    }
}
