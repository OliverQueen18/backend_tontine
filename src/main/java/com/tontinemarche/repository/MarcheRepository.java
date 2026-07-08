package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Marche;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarcheRepository extends JpaRepository<Marche, Long> {
    @EntityGraph(attributePaths = "agence")
    List<Marche> findByAgenceId(Long agenceId);

    @Override
    @EntityGraph(attributePaths = "agence")
    List<Marche> findAll();

    @Override
    @EntityGraph(attributePaths = "agence")
    Optional<Marche> findById(Long id);
}
