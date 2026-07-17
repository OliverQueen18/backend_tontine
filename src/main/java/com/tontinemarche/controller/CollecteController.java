package com.tontinemarche.controller;

import com.tontinemarche.dto.CollecteDto;
import com.tontinemarche.service.CollecteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/collectes")
@RequiredArgsConstructor
public class CollecteController {

    private final CollecteService collecteService;

    @GetMapping
    public List<CollecteDto> filter(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        return collecteService.filter(agenceId, agentId, clientId, debut, fin);
    }

    @GetMapping("/portefeuille/{agentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public List<CollecteDto> portefeuille(@PathVariable Long agentId) {
        return collecteService.portefeuilleAgent(agentId);
    }

    @GetMapping("/client/{clientId}")
    public List<CollecteDto> historiqueClient(@PathVariable Long clientId) {
        return collecteService.historiqueClient(clientId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public CollecteDto enregistrer(@Valid @RequestBody CollecteDto dto) {
        return collecteService.enregistrer(dto);
    }

    @PatchMapping("/{id}/signer")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public CollecteDto signer(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        return collecteService.signer(id, payload != null ? payload.get("signatureClient") : null);
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public CollecteDto annuler(@PathVariable Long id) {
        return collecteService.annuler(id);
    }
}
