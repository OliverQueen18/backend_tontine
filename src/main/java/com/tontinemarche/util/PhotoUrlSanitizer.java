package com.tontinemarche.util;

import com.tontinemarche.exception.ApiException;

public final class PhotoUrlSanitizer {

    private static final int MAX_LENGTH = 500;

    private PhotoUrlSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("data:")) {
            throw ApiException.badRequest(
                    "Photo trop volumineuse. Utilisez le téléversement de fichier plutôt qu'une image intégrée.");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw ApiException.badRequest("URL de photo trop longue (max " + MAX_LENGTH + " caractères)");
        }
        return trimmed;
    }
}
