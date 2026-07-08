package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUtilisateurIdOrderByDateNotificationDesc(Long utilisateurId);
    long countByUtilisateurIdAndLueFalse(Long utilisateurId);
}
