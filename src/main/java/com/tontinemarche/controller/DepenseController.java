package com.tontinemarche.controller;

import com.tontinemarche.dto.DepenseDto;
import com.tontinemarche.service.DepenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/depenses")
@RequiredArgsConstructor
public class DepenseController {

    private final DepenseService depenseService;

    @GetMapping
    public List<DepenseDto> findAll(@RequestParam(required = false) Long agenceId) {
        return depenseService.findAll(agenceId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public DepenseDto create(@Valid @RequestBody DepenseDto dto) {
        return depenseService.create(dto);
    }

    @PatchMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'CAISSIER')")
    public DepenseDto valider(@PathVariable Long id) {
        return depenseService.valider(id);
    }
}
