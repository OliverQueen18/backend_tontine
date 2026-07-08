package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.CategorieDepense;
import com.tontinemarche.domain.enums.StatutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieDepenseRepository extends JpaRepository<CategorieDepense, Long> {
    List<CategorieDepense> findAllByOrderByNomAsc();
    List<CategorieDepense> findByStatutOrderByNomAsc(StatutEntity statut);
    Optional<CategorieDepense> findByNomIgnoreCase(String nom);
    boolean existsByNomIgnoreCaseAndStatut(String nom, StatutEntity statut);
}
