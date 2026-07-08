package com.tontinemarche.config;

import com.tontinemarche.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformSettingsInitializer implements CommandLineRunner {

    private final PlatformSettingsService platformSettingsService;

    @Override
    public void run(String... args) {
        platformSettingsService.ensureDefault();
    }
}
