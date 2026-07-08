package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Quartier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuartierRepository extends JpaRepository<Quartier, Long> {
    List<Quartier> findByAgenceId(Long agenceId);
}
