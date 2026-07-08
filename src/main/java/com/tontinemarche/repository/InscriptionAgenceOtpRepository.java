package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.InscriptionAgenceOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InscriptionAgenceOtpRepository extends JpaRepository<InscriptionAgenceOtp, Long> {

    Optional<InscriptionAgenceOtp> findTopByEmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(String email);

    Optional<InscriptionAgenceOtp> findByVerificationTokenAndUsedFalse(String verificationToken);

    @Modifying
    @Query("UPDATE InscriptionAgenceOtp o SET o.used = true WHERE LOWER(o.email) = LOWER(:email) AND o.used = false")
    void invalidateAllForEmail(@Param("email") String email);
}
