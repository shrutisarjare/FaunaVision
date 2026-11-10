package com.endangered.species.ml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AudioSet class names for YAMNet's 521 outputs. Bundled as a resource so the audio path
 * never needs a download, and read as {@code index|display_name} lines.
 */
final class AudioSetLabels {

    private static final Logger log = LoggerFactory.getLogger(AudioSetLabels.class);
    private static final String RESOURCE = "ml/audioset_labels.txt";

    private final Map<Integer, String> names;

    private AudioSetLabels(Map<Integer, String> names) {
        this.names = names;
    }

    static AudioSetLabels load() {
        Map<Integer, String> names = new HashMap<>(700);
        try (InputStream input = AudioSetLabels.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                log.warn("AudioSet labels are missing from the classpath; audio names will be indexes");
                return new AudioSetLabels(names);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('|');
                if (separator <= 0) {
                    continue;
                }
                try {
                    names.put(Integer.parseInt(line.substring(0, separator).trim()),
                            line.substring(separator + 1).trim());
                } catch (NumberFormatException ignored) {
                    // Skip malformed rows rather than failing the whole audio path.
                }
            }
        } catch (IOException e) {
            log.warn("Could not read AudioSet labels: {}", e.getMessage());
        }
        return new AudioSetLabels(names);
    }

    String name(int index) {
        return names.getOrDefault(index, "");
    }

    int size() {
        return names.size();
    }
}
