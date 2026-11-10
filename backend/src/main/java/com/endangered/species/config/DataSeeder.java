package com.endangered.species.config;

import com.endangered.species.service.SpeciesImportService;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds the embedded database from {@code classpath:seed/animals.xlsx} the first
 * time the application starts. Subsequent starts reuse the existing data.
 */
@Component
public class DataSeeder implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SpeciesImportService importService;

    public DataSeeder(SpeciesImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long existing = importService.count();
        if (existing > 0) {
            log.info("Species catalogue already contains {} records; skipping seed", existing);
            return;
        }
        ClassPathResource seed = new ClassPathResource("seed/animals.xlsx");
        if (!seed.exists()) {
            log.warn("Seed workbook seed/animals.xlsx is missing; catalogue will be empty");
            return;
        }
        long started = System.currentTimeMillis();
        try (InputStream input = seed.getInputStream()) {
            int imported = importService.replaceAll(input);
            log.info("Seeded {} records in {} ms", imported, System.currentTimeMillis() - started);
        }
    }

    /** Runs before every other runner, so later warm-ups see a populated catalogue. */
    @Override
    public int getOrder() {
        return 0;
    }
}
