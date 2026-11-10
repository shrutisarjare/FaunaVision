package com.endangered.species.ml;

import com.endangered.species.service.SpeciesCatalogService;
import com.endangered.species.service.WikiImageService;
import com.endangered.species.web.ApiException;
import com.endangered.species.web.Dtos;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implements the four {@code /identify-*} flows. CLIP ranks the upload against the actual
 * catalogue; the ImageNet classifier is only a fallback for an installation without the
 * CLIP files, and audio evidence is used to re-rank near ties.
 */
@Service
public class IdentifyService {

    private static final Logger log = LoggerFactory.getLogger(IdentifyService.class);

    /** Frames sampled across a video: each one costs a CLIP forward pass. */
    private static final int VIDEO_FRAMES = 8;
    private static final int CANDIDATES = SpeciesMatcher.TOP_K;

    /**
     * Weight of a matching soundtrack, in cosine units. Small on purpose: a birdsong
     * settles a close call between two birds, it does not overrule a clear photo.
     */
    private static final float AUDIO_BONUS = 0.015f;

    /**
     * Features a description can name outright, mapped to the catalogue groups that own
     * them. Small bonus again: it settles a close call between similar species.
     */
    private static final float TRAIT_BONUS = 0.012f;

    private static final Map<String, List<String>> TRAITS = Map.ofEntries(
            Map.entry("trunk", List.of("elephant")),
            Map.entry("tusk", List.of("elephant", "walrus")),
            Map.entry("stripe", List.of("tiger")),
            Map.entry("antler", List.of("deer")),
            Map.entry("blowhole", List.of("dolphin", "whale")),
            Map.entry("tentacle", List.of("octopus")),
            Map.entry("feather", List.of("bird")),
            Map.entry("beak", List.of("bird")),
            Map.entry("shell", List.of("sea turtle")),
            Map.entry("flipper", List.of("seal", "dolphin", "sea turtle")),
            Map.entry("fin", List.of("shark")),
            Map.entry("roar", List.of("tiger", "lion", "leopard")),
            Map.entry("howl", List.of("wolf")),
            Map.entry("hoot", List.of("owl")));

    private final ImageClassifier images;
    private final AudioClassifier audio;
    private final ClipModel clip;
    private final SpeciesMatcher matcher;
    private final SpeciesCatalogService catalog;
    private final WikiImageService wiki;

    public IdentifyService(ImageClassifier images, AudioClassifier audio, ClipModel clip,
                           SpeciesMatcher matcher, SpeciesCatalogService catalog, WikiImageService wiki) {
        this.images = images;
        this.audio = audio;
        this.clip = clip;
        this.matcher = matcher;
        this.catalog = catalog;
        this.wiki = wiki;
    }

    public Dtos.IdentifyResult fromImage(MultipartFile file) {
        BufferedImage image = readImage(file);
        if (matcher.ready()) {
            List<SpeciesMatcher.Scored> ranked = matcher.rank(matcher.scores(clip.embedImage(image)));
            if (!ranked.isEmpty()) {
                return toResult(ranked);
            }
        }
        return fromImageNet(image);
    }

    public Dtos.IdentifyResult fromVideo(MultipartFile file) {
        Path temp = storeTemp(file, "video");
        try {
            List<BufferedImage> frames = MediaTools.sampleFramesAcross(temp, VIDEO_FRAMES);
            if (frames.isEmpty()) {
                throw ApiException.badRequest("Could not read any frames from the video");
            }
            if (matcher.ready()) {
                float[] merged = new float[0];
                int observed = 0;
                for (BufferedImage frame : frames) {
                    float[] scores = matcher.scores(clip.embedImage(frame));
                    if (scores.length == 0) {
                        continue;
                    }
                    observed++;
                    merged = SpeciesMatcher.mergeByMean(merged, scores, observed);
                }
                AudioClassifier.Evidence soundtrack = soundtrack(temp);
                String note = null;
                if (!soundtrack.isEmpty()) {
                    log.debug("Video soundtrack suggests {} -> {}", soundtrack.label(), soundtrack.animalTypes());
                    matcher.boostTypes(merged, soundtrack.animalTypes(), AUDIO_BONUS);
                    note = "The soundtrack matched " + soundtrack.label()
                            + ", which re-ranked frames that were already close.";
                }
                List<SpeciesMatcher.Scored> ranked = matcher.rank(merged);
                if (!ranked.isEmpty()) {
                    return toResult(ranked, note);
                }
            }
            String species = majorityVote(frames);
            return new Dtos.IdentifyResult(species, null, catalog.search(species), null, null);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * Audio names a group rather than a species, so the catalogue supplies the candidates and
     * the group's evidence mass becomes their confidence.
     */
    public Dtos.IdentifyResult fromAudio(MultipartFile file) {
        Path temp = storeTemp(file, "audio");
        try {
            AudioClassifier.Evidence evidence = audio.evidence(temp);
            if (evidence.isEmpty()) {
                log.debug("Audio is inconclusive; best class was '{}' at {}",
                        evidence.label(), evidence.confidence());
                return new Dtos.IdentifyResult("unknown", null, List.of(), null, null);
            }
            log.debug("Audio evidence {} -> {}", evidence.label(), evidence.typeScores());

            Map<String, Float> groups = evidence.typeScores();
            float total = 0f;
            for (float value : groups.values()) {
                total += value;
            }

            List<Dtos.SpeciesSummary> results = new ArrayList<>();
            List<Dtos.Match> matches = new ArrayList<>();
            for (Map.Entry<String, Float> group : groups.entrySet()) {
                int room = CANDIDATES - results.size();
                if (room <= 0) {
                    break;
                }
                List<Dtos.SpeciesSummary> found = catalog.byType(group.getKey(), room);
                if (found.isEmpty()) {
                    continue;
                }
                // The group cannot say which of its species it is, so its mass is shared:
                // "birdsong" at 100% over five birds is five candidates at 20% each.
                double share = total > 0 ? group.getValue() / total / found.size() : 0d;
                for (Dtos.SpeciesSummary summary : found) {
                    results.add(summary);
                    matches.add(new Dtos.Match(summary.name(), summary.scientificName(), round(share)));
                }
            }
            if (results.isEmpty()) {
                return new Dtos.IdentifyResult("unknown", null, List.of(), null,
                        "Heard " + evidence.label() + ", but the catalogue has no species in that group.");
            }
            String note = String.format(
                    "Heard %s (%.0f%%). A sound cannot tell the species of a group apart, so these"
                            + " candidates are ordered by conservation status.",
                    evidence.label(), evidence.confidence() * 100f);
            return new Dtos.IdentifyResult(results.get(0).name(), null, results, matches, note);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * A description is a caption, so it is encoded by CLIP's text tower and compared with
     * the species-name embeddings. The legacy keyword chain still answers when the CLIP
     * files are absent.
     */
    public Dtos.IdentifyResult fromText(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return new Dtos.IdentifyResult("unknown", null, List.of(), null,
                    "Describe the animal's size, colour, habitat or behaviour.");
        }
        if (matcher.ready()) {
            float[] scores = matcher.scores(clip.embedText(value));
            matcher.boostMentions(scores, value);
            matcher.boostTypes(scores, traitsIn(value), TRAIT_BONUS);
            List<SpeciesMatcher.Scored> ranked = matcher.rank(scores);
            if (!ranked.isEmpty()) {
                return toResult(ranked, "The description was compared with every species in the"
                        + " catalogue, so these are the closest matches rather than exact names.");
            }
        }
        return fromKeywords(value.toLowerCase(Locale.ROOT));
    }

    /** Catalogue groups named by the features in a description, in no particular order. */
    private static Set<String> traitsIn(String description) {
        Set<String> words = new HashSet<>();
        for (String word : description.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (word.isEmpty()) {
                continue;
            }
            words.add(word);
            if (word.length() > 3 && word.endsWith("s")) {
                words.add(word.substring(0, word.length() - 1));
            }
        }
        Set<String> types = new LinkedHashSet<>();
        for (String word : words) {
            List<String> mapped = TRAITS.get(word);
            if (mapped != null) {
                types.addAll(mapped);
            }
        }
        return types;
    }

    /** Pre-CLIP behaviour: four keyword shortcuts, then the raw description as a search term. */
    private Dtos.IdentifyResult fromKeywords(String value) {
        String species;
        if (value.contains("roar")) {
            species = "lion";
        } else if (value.contains("stripe")) {
            species = "tiger";
        } else if (value.contains("trunk")) {
            species = "elephant";
        } else if (value.contains("horn")) {
            species = "rhino";
        } else {
            species = value;
        }
        return new Dtos.IdentifyResult(species, null, catalog.search(species), null, null);
    }

    /** Builds the response around the ranked candidates, keeping the legacy keys intact. */
    private Dtos.IdentifyResult toResult(List<SpeciesMatcher.Scored> ranked) {
        return toResult(ranked, null);
    }

    private Dtos.IdentifyResult toResult(List<SpeciesMatcher.Scored> ranked, String evidence) {
        String species = ranked.get(0).entry().name();
        List<Dtos.Match> matches = ranked.stream()
                .map(scored -> new Dtos.Match(
                        scored.entry().name(),
                        scored.entry().scientificName(),
                        round(scored.confidence())))
                .toList();
        return new Dtos.IdentifyResult(species, wiki.imageFor(species), catalog.search(species), matches,
                evidence);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** Pre-CLIP behaviour: one ImageNet class cleaned to a species keyword. */
    private Dtos.IdentifyResult fromImageNet(BufferedImage image) {
        String species = images.classify(image);
        return new Dtos.IdentifyResult(species, wiki.imageFor(species), catalog.search(species), null, null);
    }

    /** A video without an audio track, or without the audio model, must still identify. */
    private AudioClassifier.Evidence soundtrack(Path video) {
        try {
            return audio.evidence(video);
        } catch (RuntimeException e) {
            log.debug("Skipping the video soundtrack: {}", e.getMessage());
            return AudioClassifier.Evidence.none();
        }
    }

    private String majorityVote(List<BufferedImage> frames) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        for (BufferedImage frame : frames) {
            votes.merge(images.classify(frame), 1, Integer::sum);
        }
        String winner = "unknown";
        int best = 0;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                winner = entry.getKey();
            }
        }
        return winner;
    }

    private static BufferedImage readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file uploaded");
        }
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw ApiException.badRequest("Unsupported image format");
            }
            return image;
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the image: " + e.getMessage());
        }
    }

    private static Path storeTemp(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file uploaded");
        }
        try {
            Path temp = Files.createTempFile("species-" + prefix + "-", ".bin");
            file.transferTo(temp);
            return temp;
        } catch (IOException e) {
            throw ApiException.badRequest("Could not store the upload: " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temp files are also cleaned by the OS; nothing useful to report here.
        }
    }
}
