package com.tontinemarche.config;

import com.tontinemarche.service.SiteContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SiteContentInitializer implements CommandLineRunner {

    private final SiteContentService siteContentService;

    @Override
    public void run(String... args) {
        siteContentService.ensureDefaults("fr");
        siteContentService.ensureCollecteurSection("fr");
    }
}
