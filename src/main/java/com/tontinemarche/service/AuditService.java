package com.tontinemarche.service;

import com.tontinemarche.domain.entity.AuditLog;
import com.tontinemarche.repository.AuditLogRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(String action, String entite, String reference, String details, Long agenceId) {
        String username = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            username = principal.getUsername();
            if (agenceId == null) {
                agenceId = principal.getAgenceId();
            }
        }

        auditLogRepository.save(AuditLog.builder()
                .createdAt(Instant.now())
                .username(username)
                .action(action)
                .entite(entite)
                .reference(reference)
                .details(details)
                .agenceId(agenceId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(Long agenceId) {
        if (agenceId != null) {
            return auditLogRepository.findTop50ByAgenceIdOrderByCreatedAtDesc(agenceId);
        }
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
