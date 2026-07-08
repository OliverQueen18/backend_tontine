package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.ClientHistorique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientHistoriqueRepository extends JpaRepository<ClientHistorique, Long> {
    List<ClientHistorique> findByClientIdOrderByDateHeureDesc(Long clientId);
}
