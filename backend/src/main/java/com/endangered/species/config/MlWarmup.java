package com.endangered.species.config;

import com.endangered.species.ml.SpeciesMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Builds the CLIP species index at start-up so the first identify request does not pay for
 * the text encodes. Runs after {@link DataSeeder}, which seeds the catalogue.
 */
@Component
public class MlWarmup implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MlWarmup.class);

    private final SpeciesMatcher matcher;

    public MlWarmup(SpeciesMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (matcher.ready()) {
                log.info("CLIP species index is ready");
            } else {
                log.info("CLIP species index unavailable; identification falls back to MobileNetV2");
            }
        } catch (RuntimeException e) {
            log.warn("Could not warm up the CLIP species index: {}", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
