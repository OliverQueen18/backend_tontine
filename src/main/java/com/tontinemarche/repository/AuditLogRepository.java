package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop50ByOrderByCreatedAtDesc();
    List<AuditLog> findTop50ByAgenceIdOrderByCreatedAtDesc(Long agenceId);
}
