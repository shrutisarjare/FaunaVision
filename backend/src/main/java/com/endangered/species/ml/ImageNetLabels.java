package com.endangered.species.ml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Index → readable label mapping for the 1000 ImageNet classes.
 *
 * <p>The file is the standard synset list ({@code n01440764 tench, Tinca tinca}); only
 * the words before the first comma are kept, exactly like Keras
 * {@code decode_predictions} did for the Python service.</p>
 */
public final class ImageNetLabels {

    private static final Logger log = LoggerFactory.getLogger(ImageNetLabels.class);

    private final List<String> labels;

    private ImageNetLabels(List<String> labels) {
        this.labels = labels;
    }

    public static ImageNetLabels loadOrFallback(Path file) {
        if (file != null && Files.isRegularFile(file)) {
            try {
                List<String> parsed = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
                if (!parsed.isEmpty()) {
                    log.info("Loaded {} ImageNet labels from {}", parsed.size(), file);
                    return new ImageNetLabels(parsed);
                }
            } catch (IOException e) {
                log.warn("Could not read ImageNet labels {}: {}", file, e.getMessage());
            }
        }
        log.warn("ImageNet labels missing at {}; predictions will be reported as class indexes", file);
        return new ImageNetLabels(List.of());
    }

    private static List<String> parse(List<String> lines) {
        List<String> parsed = new ArrayList<>(lines.size());
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            String label = space < 0 ? trimmed : trimmed.substring(space + 1).trim();
            int comma = label.indexOf(',');
            if (comma > 0) {
                label = label.substring(0, comma).trim();
            }
            parsed.add(label.toLowerCase());
        }
        return parsed;
    }

    public String label(int index) {
        return index >= 0 && index < labels.size() ? labels.get(index) : "class " + index;
    }

    public int size() {
        return labels.size();
    }
}
