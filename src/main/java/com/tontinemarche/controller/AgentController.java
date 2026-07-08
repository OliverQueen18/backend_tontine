package com.tontinemarche.controller;

import com.tontinemarche.dto.AgentDto;
import com.tontinemarche.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public List<AgentDto> findAll(@RequestParam(required = false) Long agenceId) {
        return agentService.findAll(agenceId);
    }

    @GetMapping("/{id}")
    public AgentDto findById(@PathVariable Long id) {
        return agentService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public AgentDto create(@Valid @RequestBody AgentDto dto) {
        return agentService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public AgentDto update(@PathVariable Long id, @Valid @RequestBody AgentDto dto) {
        return agentService.update(id, dto);
    }

    @PatchMapping("/{id}/suspendre")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public AgentDto suspendre(@PathVariable Long id) {
        return agentService.suspendre(id);
    }
}
