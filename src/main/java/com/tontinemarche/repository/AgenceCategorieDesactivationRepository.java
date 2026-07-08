package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.AgenceCategorieDesactivation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AgenceCategorieDesactivationRepository extends JpaRepository<AgenceCategorieDesactivation, Long> {
    boolean existsByAgenceIdAndCategorieId(Long agenceId, Long categorieId);
    Optional<AgenceCategorieDesactivation> findByAgenceIdAndCategorieId(Long agenceId, Long categorieId);
    void deleteByAgenceIdAndCategorieId(Long agenceId, Long categorieId);
    List<AgenceCategorieDesactivation> findByAgenceId(Long agenceId);

    @Query("SELECT d.categorie.id FROM AgenceCategorieDesactivation d WHERE d.agence.id = :agenceId")
    Set<Long> findCategorieIdByAgenceId(@Param("agenceId") Long agenceId);
}
