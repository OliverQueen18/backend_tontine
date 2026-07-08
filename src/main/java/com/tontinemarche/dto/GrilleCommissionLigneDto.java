package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class GrilleCommissionLigneDto {
    private Long id;
    private BigDecimal montantMin;
    private BigDecimal montantMax;
    private BigDecimal montantCommission;
    private Integer ordre;
}
