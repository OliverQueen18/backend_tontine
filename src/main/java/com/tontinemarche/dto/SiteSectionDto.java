package com.tontinemarche.dto;

import java.time.Instant;
import java.util.Map;

public record SiteSectionDto(
        Long id,
        String sectionKey,
        String label,
        String locale,
        Map<String, Object> content,
        Instant updatedAt
) {
}
