package com.tontinemarche.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inscription_agence_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscriptionAgenceOtp extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private Instant expiryDate;

    @Builder.Default
    private boolean verified = false;

    private String verificationToken;

    private Instant tokenExpiry;

    @Builder.Default
    private boolean used = false;
}
