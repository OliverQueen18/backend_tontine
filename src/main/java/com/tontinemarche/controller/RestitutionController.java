package com.tontinemarche.controller;

import com.tontinemarche.dto.RestitutionDto;
import com.tontinemarche.service.RestitutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/restitutions")
@RequiredArgsConstructor
public class RestitutionController {

    private final RestitutionService restitutionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT', 'CAISSIER')")
    public List<RestitutionDto> findAll(@RequestParam(required = false) Long agenceId) {
        return restitutionService.findAll(agenceId);
    }

    @GetMapping("/calculer/{clientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT', 'CAISSIER')")
    public Map<String, Object> calculer(@PathVariable Long clientId) {
        return restitutionService.calculer(clientId);
    }

    @GetMapping("/en-attente-signature")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'AGENT', 'ADMIN_AGENCE')")
    public List<RestitutionDto> enAttenteSignature() {
        return restitutionService.enAttenteSignature();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public RestitutionDto effectuer(@Valid @RequestBody RestitutionDto dto) {
        return restitutionService.effectuer(dto);
    }

    @PatchMapping("/{id}/commission")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN_AGENCE')")
    public RestitutionDto modifierCommission(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        BigDecimal commission = new BigDecimal(payload.get("commission").toString());
        return restitutionService.modifierCommission(id, commission);
    }

    @PatchMapping("/{id}/finaliser")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN_AGENCE')")
    public RestitutionDto finaliser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        String signature = payload.get("signatureClient") != null
                ? payload.get("signatureClient").toString() : null;
        BigDecimal commission = payload.get("commission") != null
                ? new BigDecimal(payload.get("commission").toString()) : null;
        return restitutionService.finaliserSignature(id, signature, commission);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT', 'CAISSIER')")
    public RestitutionDto findById(@PathVariable Long id) {
        return restitutionService.findById(id);
    }

    @PostMapping("/{id}/renvoyer-recu")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT', 'CAISSIER')")
    public RestitutionDto renvoyerRecu(@PathVariable Long id) {
        return restitutionService.renvoyerRecu(id);
    }
}
