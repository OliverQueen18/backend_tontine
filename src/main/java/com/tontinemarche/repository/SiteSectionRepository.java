package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.SiteSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteSectionRepository extends JpaRepository<SiteSection, Long> {

    Optional<SiteSection> findBySectionKeyAndLocale(String sectionKey, String locale);

    List<SiteSection> findByLocaleOrderBySortOrderAsc(String locale);

    boolean existsBySectionKeyAndLocale(String sectionKey, String locale);
}
