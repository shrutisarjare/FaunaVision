package com.endangered.species.ml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Ranks catalogue species against an embedding. Scores are cosine similarities; the
 * confidence is a softmax at CLIP's own logit scale, so it behaves like the probability
 * the model was trained to produce rather than an arbitrary percentage.
 */
@Service
public class SpeciesMatcher {

    private static final float LOGIT_SCALE = 100f;
    public static final int TOP_K = 5;

    /** A spelled-out species name is stronger evidence than a close semantic guess. */
    private static final float NAME_BONUS = 0.02f;
    /** A distinctive word shared by the description and a species name or group. */
    private static final float WORD_BONUS = 0.01f;

    /** Name words that say "where" or "how big", not "which animal". */
    private static final Set<String> GENERIC_WORDS = Set.of(
            "indian", "asian", "african", "american", "australian", "arabian", "eurasian",
            "bengal", "siberian", "sumatran", "amur", "bornean", "himalayan", "pacific",
            "atlantic", "lankan", "giant", "common", "great", "lesser", "little", "royal",
            "spotted", "striped", "mountain", "forest", "water", "river", "wild");

    private final SpeciesEmbedder embedder;

    public SpeciesMatcher(SpeciesEmbedder embedder) {
        this.embedder = embedder;
    }

    /** A ranked species plus the evidence behind it. */
    public record Scored(SpeciesIndex.Entry entry, float similarity, double confidence) {
    }

    public boolean ready() {
        SpeciesIndex index = embedder.index();
        return index != null && !index.isEmpty();
    }

    /** Cosine similarity of one embedding against every catalogued species. */
    public float[] scores(float[] embedding) {
        SpeciesIndex index = embedder.index();
        if (index == null || index.isEmpty() || embedding == null || embedding.length == 0) {
            return new float[0];
        }
        List<SpeciesIndex.Entry> species = index.species();
        float[] scores = new float[species.size()];
        for (int i = 0; i < species.size(); i++) {
            scores[i] = OnnxValues.dot(embedding, species.get(i).embedding());
        }
        return scores;
    }

    /**
     * Running mean of the per-frame scores. Averaging is safer than max: a single frame
     * where the animal is out of shot can be lucky for a rival species, and taking the
     * maximum would let that one frame decide the whole clip.
     */
    public static float[] mergeByMean(float[] running, float[] candidate, int observed) {
        if (running.length == 0) {
            return candidate.clone();
        }
        if (candidate.length != running.length) {
            return running;
        }
        float weight = 1f / Math.max(1, observed);
        float[] merged = running.clone();
        for (int i = 0; i < merged.length; i++) {
            merged[i] += (candidate[i] - merged[i]) * weight;
        }
        return merged;
    }

    /**
     * Nudges species whose catalogue type matches an independent signal (for example a
     * birdsong in a video). The bonus is small on purpose: audio reorders near-ties but
     * never overrides a confident visual match.
     */
    public void boostTypes(float[] scores, Collection<String> animalTypes, float bonus) {
        SpeciesIndex index = embedder.index();
        if (index == null || index.isEmpty() || animalTypes == null || animalTypes.isEmpty()) {
            return;
        }
        List<SpeciesIndex.Entry> species = index.species();
        int limit = Math.min(scores.length, species.size());
        for (int i = 0; i < limit; i++) {
            String type = species.get(i).animalType();
            if (type != null) {
                String normalised = type.trim().toLowerCase(Locale.ROOT);
                if (animalTypes.contains(normalised)) {
                    scores[i] += bonus;
                }
            }
        }
    }

    /**
     * Adds evidence found in the words of a description: a spelled-out name beats any
     * embedding, and a distinctive shared word ("chital", "panda") breaks a near tie.
     */
    public void boostMentions(float[] scores, String description) {
        SpeciesIndex index = embedder.index();
        String text = normalise(description);
        if (index == null || index.isEmpty() || text.isEmpty()) {
            return;
        }
        String padded = " " + text + " ";
        List<SpeciesIndex.Entry> species = index.species();
        int limit = Math.min(scores.length, species.size());
        for (int i = 0; i < limit; i++) {
            SpeciesIndex.Entry entry = species.get(i);
            String name = normalise(entry.name());
            if (name.isEmpty()) {
                continue;
            }
            if (padded.contains(" " + name + " ")) {
                scores[i] += NAME_BONUS;
                continue;
            }
            if (mentionsWord(padded, name, 5) || mentionsWord(padded, normalise(entry.animalType()), 3)) {
                scores[i] += WORD_BONUS;
            }
        }
    }

    public List<Scored> rank(float[] scores) {
        return rank(scores, TOP_K);
    }

    public List<Scored> rank(float[] scores, int limit) {
        SpeciesIndex index = embedder.index();
        if (index == null || index.isEmpty() || scores.length == 0) {
            return List.of();
        }
        List<SpeciesIndex.Entry> species = index.species();
        int count = Math.min(scores.length, species.size());

        // One animal can appear in several catalogue rows. Pooling by maximum keeps a
        // well-matching variant instead of diluting it into an average, and leaves one
        // probability per name so the confidence stays honest.
        Map<String, Integer> bestBySpecies = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = species.get(i).name().toLowerCase(Locale.ROOT);
            Integer current = bestBySpecies.get(key);
            if (current == null || scores[i] > scores[current]) {
                bestBySpecies.put(key, i);
            }
        }

        List<Integer> entries = new ArrayList<>(bestBySpecies.values());
        float[] pooled = new float[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            pooled[i] = scores[entries.get(i)];
        }
        double[] confidence = softmax(pooled, pooled.length);

        List<Integer> order = new ArrayList<>(pooled.length);
        for (int i = 0; i < pooled.length; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> pooled[i]).reversed());

        List<Scored> ranked = new ArrayList<>(Math.min(limit, pooled.length));
        for (int position = 0; position < order.size() && ranked.size() < limit; position++) {
            int i = order.get(position);
            ranked.add(new Scored(species.get(entries.get(i)), pooled[i], confidence[i]));
        }
        return ranked;
    }

    /** Lowercases and turns punctuation into spaces, so "Sloth-bear!" still matches. */
    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && out.length() > 0) {
                    out.append(' ');
                }
                out.append(c);
                pendingSpace = false;
            } else {
                pendingSpace = true;
            }
        }
        return out.toString();
    }

    /** True when one distinctive word of {@code phrase} appears in the padded text. */
    private static boolean mentionsWord(String paddedText, String phrase, int minLength) {
        if (phrase.isEmpty()) {
            return false;
        }
        for (String word : phrase.split(" ")) {
            if (word.length() >= minLength && !GENERIC_WORDS.contains(word)
                    && paddedText.contains(" " + word + " ")) {
                return true;
            }
        }
        return false;
    }

    private static double[] softmax(float[] scores, int count) {
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            best = Math.max(best, (double) scores[i] * LOGIT_SCALE);
        }
        double[] exp = new double[count];
        double sum = 0;
        for (int i = 0; i < count; i++) {
            exp[i] = Math.exp((double) scores[i] * LOGIT_SCALE - best);
            sum += exp[i];
        }
        for (int i = 0; i < count; i++) {
            exp[i] /= sum;
        }
        return exp;
    }
}
