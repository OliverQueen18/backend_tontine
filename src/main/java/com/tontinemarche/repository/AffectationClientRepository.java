package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.AffectationClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AffectationClientRepository extends JpaRepository<AffectationClient, Long> {
    List<AffectationClient> findByClientIdOrderByDateAffectationDesc(Long clientId);
    List<AffectationClient> findTop10ByOrderByCreatedAtDesc();
}
