package com.tontinemarche.controller;

import com.tontinemarche.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AUDITEUR')")
    public List<Map<String, Object>> recent(@RequestParam(required = false) Long agenceId) {
        return auditService.recent(agenceId).stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("createdAt", a.getCreatedAt());
            m.put("username", a.getUsername());
            m.put("action", a.getAction());
            m.put("entite", a.getEntite());
            m.put("reference", a.getReference());
            m.put("details", a.getDetails());
            m.put("agenceId", a.getAgenceId());
            return m;
        }).toList();
    }
}
