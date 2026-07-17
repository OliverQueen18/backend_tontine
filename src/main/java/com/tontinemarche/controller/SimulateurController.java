package com.tontinemarche.controller;

import com.tontinemarche.dto.SimulationResultatDto;
import com.tontinemarche.service.SimulateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/simulateur")
@RequiredArgsConstructor
public class SimulateurController {

    private final SimulateurService simulateurService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public SimulationResultatDto simuler(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long agenceId
    ) {
        return simulateurService.simuler(debut, fin, agenceId);
    }
}
