package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.MouvementCaisse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MouvementCaisseRepository extends JpaRepository<MouvementCaisse, Long> {
    List<MouvementCaisse> findByCaisseIdOrderByDateHeureDesc(Long caisseId);

    void deleteByCaisseId(Long caisseId);
}
