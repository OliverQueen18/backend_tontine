package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.GrilleCommissionLigne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrilleCommissionRepository extends JpaRepository<GrilleCommissionLigne, Long> {
    List<GrilleCommissionLigne> findByAgenceIdOrderByOrdreAscMontantMinAsc(Long agenceId);
    void deleteByAgenceId(Long agenceId);
    long countByAgenceId(Long agenceId);
}
