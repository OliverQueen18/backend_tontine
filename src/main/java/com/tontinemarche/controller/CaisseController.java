package com.tontinemarche.controller;

import com.tontinemarche.dto.CaisseDto;
import com.tontinemarche.service.CaisseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
}
