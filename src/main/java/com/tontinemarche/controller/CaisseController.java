package com.tontinemarche.controller;

import com.tontinemarche.dto.CaisseControleDto;
import com.tontinemarche.dto.CaisseDto;
import com.tontinemarche.service.CaisseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/caisse")
@RequiredArgsConstructor
public class CaisseController {

    private final CaisseService caisseService;

    @GetMapping("/jour")
    public CaisseDto getCaisseDuJour(@RequestParam Long agenceId) {
        return caisseService.getCaisseDuJour(agenceId);
    }

    @GetMapping("/controle")
    public CaisseControleDto controle(@RequestParam Long agenceId) {
        return caisseService.getControle(agenceId);
    }

    @GetMapping("/historique")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER', 'AUDITEUR')")
    public List<CaisseDto> historique(
            @RequestParam Long agenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        return caisseService.findByPeriode(agenceId, debut, fin);
    }

    @GetMapping("/detail")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER', 'AUDITEUR')")
    public CaisseDto detail(
            @RequestParam Long agenceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return caisseService.getByDate(agenceId, date);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER', 'AUDITEUR')")
    public CaisseDto getById(@PathVariable Long id) {
        return caisseService.getById(id);
    }

    @PostMapping("/ouvrir")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public CaisseDto ouvrir(@RequestBody Map<String, Object> payload) {
        Long agenceId = Long.valueOf(payload.get("agenceId").toString());
        BigDecimal soldeInitial = payload.containsKey("soldeInitial") && payload.get("soldeInitial") != null
                ? new BigDecimal(payload.get("soldeInitial").toString())
                : null;
        return caisseService.ouvrir(agenceId, soldeInitial);
    }

    @PostMapping("/cloturer")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public CaisseDto cloturer(@RequestBody Map<String, Object> payload) {
        Long agenceId = Long.valueOf(payload.get("agenceId").toString());
        return caisseService.cloturer(agenceId, payload);
    }

    @PostMapping("/{id}/annuler-cloture")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public CaisseDto annulerCloture(@PathVariable Long id) {
        return caisseService.annulerCloture(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public void supprimer(@PathVariable Long id) {
        caisseService.supprimer(id);
    }
}
