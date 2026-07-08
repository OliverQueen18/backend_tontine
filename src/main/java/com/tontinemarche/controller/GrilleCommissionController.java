package com.tontinemarche.controller;

import com.tontinemarche.dto.GrilleCommissionLigneDto;
import com.tontinemarche.dto.SaveGrilleCommissionRequest;
import com.tontinemarche.service.CommissionGrilleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agences/{agenceId}/grille-commission")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
public class GrilleCommissionController {

    private final CommissionGrilleService commissionGrilleService;

    @GetMapping
    public List<GrilleCommissionLigneDto> getGrille(@PathVariable Long agenceId) {
        return commissionGrilleService.findByAgence(agenceId);
    }

    @PutMapping
    public List<GrilleCommissionLigneDto> saveGrille(
            @PathVariable Long agenceId,
            @Valid @RequestBody SaveGrilleCommissionRequest request
    ) {
        return commissionGrilleService.saveGrille(agenceId, request.getLignes());
    }
}
