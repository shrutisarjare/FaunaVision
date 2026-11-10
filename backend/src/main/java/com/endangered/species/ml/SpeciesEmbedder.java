package com.endangered.species.ml;

import com.endangered.species.repo.SpeciesRecordRepository;
import com.endangered.species.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Projects the whole catalogue into CLIP's text space so an upload can be scored against
 * real species names instead of 1000 ImageNet words. The result is cached on disk and
 * rebuilt whenever the species list or the vision model changes.
 */
@Service
public class SpeciesEmbedder {

    private static final Logger log = LoggerFactory.getLogger(SpeciesEmbedder.class);

    /** Bump when the prompt set or the tokenizer changes so stale caches are rebuilt. */
    private static final String CACHE_VERSION = "v6";

    private final SpeciesRecordRepository records;
    private final ClipModel clip;
    private final MlModelRegistry models;
    private final Path cacheFile;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile SpeciesIndex index;

    public SpeciesEmbedder(SpeciesRecordRepository records, ClipModel clip, MlModelRegistry models,
                           @Value("${app.ml.species-cache}") String cachePath) {
        this.records = records;
        this.clip = clip;
        this.models = models;
        this.cacheFile = Paths.get(cachePath).toAbsolutePath().normalize();
    }

    /** The cached index, built on first use. {@code null} when CLIP is not installed. */
    public SpeciesIndex index() {
        SpeciesIndex current = index;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (index == null) {
                index = load();
            }
            return index;
        }
    }

    /** Drops the in-memory copy so the next call rebuilds it (used after catalogue edits). */
    public synchronized void invalidate() {
        index = null;
    }

    private SpeciesIndex load() {
        if (!clip.available()) {
            log.info("CLIP models are absent; identification falls back to MobileNetV2");
            return null;
        }
        List<Object[]> rows = records.distinctSpecies();
        if (rows.isEmpty()) {
            return null;
        }

        String fingerprint = fingerprint(rows);
        SpeciesIndex cached = readCache(fingerprint);
        if (cached != null) {
            log.info("Loaded {} species embeddings from {}", cached.species().size(), cacheFile);
            return cached;
        }

        SpeciesIndex built = build(rows, fingerprint);
        writeCache(built);
        return built;
    }

    private SpeciesIndex build(List<Object[]> rows, String fingerprint) {
        long started = System.currentTimeMillis();
        List<List<String>> perSpecies = new ArrayList<>(rows.size());
        List<String> prompts = new ArrayList<>(rows.size() * 4);

        for (Object[] row : rows) {
            List<String> own = promptsFor(text(row[0]), text(row[1]));
            perSpecies.add(own);
            prompts.addAll(own);
        }

        List<float[]> embeddings = clip.embedTexts(prompts);
        if (embeddings.size() != prompts.size()) {
            throw ApiException.unavailable("CLIP returned " + embeddings.size()
                    + " embeddings for " + prompts.size() + " prompts");
        }

        List<SpeciesIndex.Entry> species = new ArrayList<>(rows.size());
        int cursor = 0;
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            List<String> own = perSpecies.get(i);
            float[] averaged = average(embeddings, cursor, own.size());
            cursor += own.size();
            species.add(new SpeciesIndex.Entry(text(row[0]), text(row[1]), text(row[2]), averaged));
        }

        log.info("Encoded {} species ({} prompts) in {} ms", species.size(), prompts.size(),
                System.currentTimeMillis() - started);
        return new SpeciesIndex(fingerprint, species);
    }

    /**
     * Several phrasings per species; the average is markedly more stable than a single
     * "a photo of X" prompt, especially for long names like "African Forest Elephant".
     *
     * <p>The catalogue can list one animal under more than one row (scientific-name
     * variants), and each row becomes its own entry. {@link SpeciesMatcher} keeps the best
     * score per name, so a variant with a better-matching prompt is never lost.
     */
    private static List<String> promptsFor(String name, String scientificName) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<String> prompts = new ArrayList<>(6);
        prompts.add("a photo of a " + name);
        prompts.add("a photo of a " + name + " in the wild");
        prompts.add("a close-up wildlife photograph of a " + name);

        int open = name.indexOf('(');
        int close = name.indexOf(')', open + 1);
        if (open > 0 && close > open) {
            String base = name.substring(0, open).trim();
            String alias = name.substring(open + 1, close).trim();
            if (!alias.isEmpty()) {
                prompts.add("a photo of a " + alias);
                prompts.add("a photo of a " + base);
            }
        }

        if (scientificName != null && !scientificName.isBlank()) {
            prompts.add(scientificName);
        }
        return prompts;
    }

    private static float[] average(List<float[]> embeddings, int start, int count) {
        if (count <= 0) {
            return new float[0];
        }
        float[] sum = new float[embeddings.get(start).length];
        for (int i = start; i < start + count; i++) {
            float[] row = embeddings.get(i);
            for (int j = 0; j < sum.length; j++) {
                sum[j] += row[j];
            }
        }
        for (int j = 0; j < sum.length; j++) {
            sum[j] /= count;
        }
        return OnnxValues.unit(sum);
    }

    /** Species list plus model identity, so a changed catalogue or model forces a rebuild. */
    private String fingerprint(List<Object[]> rows) {
        StringBuilder builder = new StringBuilder(CACHE_VERSION);
        for (Object[] row : rows) {
            builder.append('|').append(text(row[0])).append('~').append(text(row[1]))
                    .append('~').append(text(row[2]));
        }
        try {
            Path vision = models.clipVisionPath();
            builder.append("|vision=").append(Files.size(vision))
                    .append('@').append(Files.getLastModifiedTime(vision).toMillis());
            // The text tower and the tokenizer shape the vectors too, so a swap of either
            // must invalidate the cache.
            Path text = models.clipTextPath();
            builder.append("|text=").append(Files.size(text))
                    .append('@').append(Files.getLastModifiedTime(text).toMillis());
            Path vocab = models.clipVocabPath();
            builder.append("|vocab=").append(Files.size(vocab));
        } catch (IOException e) {
            builder.append("|model=unknown");
        }
        return sha256(builder.toString());
    }

    private SpeciesIndex readCache(String fingerprint) {
        if (!Files.isRegularFile(cacheFile)) {
            return null;
        }
        try {
            SpeciesIndex cached = mapper.readValue(cacheFile.toFile(), SpeciesIndex.class);
            if (cached != null && fingerprint.equals(cached.fingerprint()) && !cached.isEmpty()) {
                return cached;
            }
            log.info("Species cache is stale; rebuilding");
        } catch (IOException e) {
            log.warn("Ignoring unreadable species cache {}: {}", cacheFile, e.getMessage());
        }
        return null;
    }

    private void writeCache(SpeciesIndex built) {
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(cacheFile.toFile(), built);
            log.info("Cached {} species embeddings at {}", built.species().size(), cacheFile);
        } catch (IOException e) {
            log.warn("Could not cache species embeddings: {}", e.getMessage());
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
