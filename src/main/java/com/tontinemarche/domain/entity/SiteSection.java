package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "site_sections", uniqueConstraints = @UniqueConstraint(columnNames = {"section_key", "locale"}))
public class SiteSection extends BaseEntity {

    @Column(name = "section_key", nullable = false, length = 64)
    private String sectionKey;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String locale = "fr";

    @Column(nullable = false, length = 128)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Lob
    @Column(name = "content_json", columnDefinition = "TEXT", nullable = false)
    private String contentJson;
}
