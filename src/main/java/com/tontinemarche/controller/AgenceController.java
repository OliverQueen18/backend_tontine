package com.tontinemarche.controller;

import com.tontinemarche.dto.AgenceDto;
import com.tontinemarche.service.AgenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agences")
@RequiredArgsConstructor
public class AgenceController {

    private final AgenceService agenceService;

    @GetMapping
    public List<AgenceDto> findAll() {
        return agenceService.findAll();
    }

    @GetMapping("/{id}")
    public AgenceDto findById(@PathVariable Long id) {
        return agenceService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AgenceDto create(@Valid @RequestBody AgenceDto dto) {
        return agenceService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public AgenceDto update(@PathVariable Long id, @Valid @RequestBody AgenceDto dto) {
        return agenceService.update(id, dto);
    }

    @PatchMapping("/{id}/desactiver")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AgenceDto desactiver(@PathVariable Long id) {
        return agenceService.desactiver(id);
    }
}
