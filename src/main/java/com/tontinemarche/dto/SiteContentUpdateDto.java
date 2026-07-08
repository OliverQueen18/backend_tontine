package com.tontinemarche.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SiteContentUpdateDto(
        @NotNull Map<String, Object> content
) {
}
