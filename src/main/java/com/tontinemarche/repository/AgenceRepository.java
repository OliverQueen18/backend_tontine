package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.enums.StatutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgenceRepository extends JpaRepository<Agence, Long> {
    Optional<Agence> findByCode(String code);
    boolean existsByCode(String code);
    List<Agence> findByStatut(StatutEntity statut);
    long countByStatut(StatutEntity statut);
}
