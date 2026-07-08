package com.tontinemarche.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class ClientCalculUtil {

    private ClientCalculUtil() {
    }

    public static BigDecimal computeNombreJoursPayes(BigDecimal soldeEpargne, BigDecimal montantJournalier) {
        if (soldeEpargne == null || montantJournalier == null || montantJournalier.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return soldeEpargne.divide(montantJournalier, 2, RoundingMode.HALF_UP);
    }

    /**
     * Prochaine date de retrait mensuelle basée sur le jour du mois de la date d'adhésion.
     */
    public static LocalDate computeDateProbableRetrait(LocalDate dateAdhesion, LocalDate reference) {
        if (dateAdhesion == null) {
            return null;
        }
        LocalDate from = reference != null ? reference : LocalDate.now();
        int adhesionDay = dateAdhesion.getDayOfMonth();

        LocalDate candidate = from.withDayOfMonth(Math.min(adhesionDay, from.lengthOfMonth()));
        if (!candidate.isAfter(from)) {
            LocalDate nextMonth = from.plusMonths(1);
            candidate = nextMonth.withDayOfMonth(Math.min(adhesionDay, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }

    public static BigDecimal montantFromJours(BigDecimal montantJournalier, BigDecimal nombreJours) {
        if (montantJournalier == null || nombreJours == null) {
            return BigDecimal.ZERO;
        }
        return montantJournalier.multiply(nombreJours).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal joursFromMontant(BigDecimal montantJournalier, BigDecimal montant) {
        if (montantJournalier == null || montant == null || montantJournalier.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return montant.divide(montantJournalier, 2, RoundingMode.HALF_UP);
    }
}
