package com.endangered.species.ml;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.endangered.species.web.ApiException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * YAMNet inference through ONNX Runtime. The waveform is resampled to 16 kHz mono and the
 * per-frame class scores are averaged, then read through the real AudioSet label names.
 *
 * <p>AudioSet does not contain species such as "tiger"; it contains groups like
 * "Roaring cats (lions, tigers)" and "Bird vocalization". The evidence therefore names a
 * catalogue type, not a species, and callers resolve types to species.
 */
@Service
public class AudioClassifier {

    static final int SAMPLE_RATE = 16000;
    private static final int TOP_CLASSES = 5;

    /** Below this peak class score the clip is treated as having no usable animal sound. */
    private static final float MIN_CONFIDENCE = 0.10f;

    /** AudioSet's "Roaring cats" class names lions and tigers outright; leopards roar too. */
    private static final List<String> BIG_CATS = List.of("tiger", "lion", "leopard");

    /** Children of AudioSet's generic "Bird" class that the catalogue also models. */
    private static final List<String> BIRD_CHILDREN = List.of("owl", "crow", "pigeon");

    private final MlModelRegistry models;
    private final AudioSetLabels labels;

    @Autowired
    public AudioClassifier(MlModelRegistry models) {
        this(models, AudioSetLabels.load());
    }

    AudioClassifier(MlModelRegistry models, AudioSetLabels labels) {
        this.models = models;
        this.labels = labels;
    }

    /** What the soundtrack suggests: the winning AudioSet class and the catalogue groups it fits. */
    public record Evidence(String label, float confidence, Map<String, Float> typeScores) {

        public Evidence {
            typeScores = typeScores == null ? Map.of() : typeScores;
        }

        public boolean isEmpty() {
            return typeScores.isEmpty();
        }

        /** Catalogue types, strongest first. */
        public Set<String> animalTypes() {
            return typeScores.keySet();
        }

        static Evidence none() {
            return new Evidence("", 0f, Map.of());
        }
    }

    public Evidence evidence(Path audioFile) {
        float[] samples = MediaTools.decodeMono(audioFile, SAMPLE_RATE);
        if (samples.length == 0) {
            return Evidence.none();
        }
        OrtSession session = models.audioSession();
        long[] shape = inputShape(session, samples.length);
        try (OnnxTensor input = OnnxTensor.createTensor(models.environment(),
                FloatBuffer.wrap(samples), shape)) {
            String inputName = session.getInputNames().iterator().next();
            try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
                int classes = labels.size() > 0 ? labels.size() : 521;
                float[] scores = OnnxValues.meanOverFrames(OnnxValues.classScores(result, classes));
                return interpret(scores);
            }
        } catch (OrtException e) {
            throw ApiException.unavailable("Audio model failed: " + e.getMessage());
        }
    }

    /**
     * Reads the strongest classes instead of only the argmax: several AudioSet classes map
     * onto the same group, so their scores are summed before the group is chosen.
     */
    Evidence interpret(float[] scores) {
        if (scores.length == 0) {
            return Evidence.none();
        }
        int[] top = topIndexes(scores, TOP_CLASSES);
        Map<String, Float> mass = new LinkedHashMap<>();
        Map<String, Float> strongestContribution = new LinkedHashMap<>();
        Map<String, String> sourceLabel = new LinkedHashMap<>();
        String bestLabel = "";
        float bestScore = 0;

        for (int index : top) {
            String label = labels.name(index);
            if (label.isEmpty()) {
                continue;
            }
            float score = scores[index];
            if (score > bestScore) {
                bestScore = score;
                bestLabel = label;
            }
            for (String type : typesFor(label)) {
                mass.merge(type, score, Float::sum);
                // Remember which class actually pointed at this group, so the message can
                // quote it rather than the unrelated argmax (often the parent "Animal").
                Float best = strongestContribution.get(type);
                if (best == null || score > best) {
                    strongestContribution.put(type, score);
                    sourceLabel.put(type, label);
                }
            }
        }

        if (mass.isEmpty() || bestScore < MIN_CONFIDENCE) {
            return new Evidence(bestLabel, bestScore, Map.of());
        }

        // An owl hoot is already a bird sound, so its parent class adds nothing but a
        // competing entry: fold "bird" into whichever child class fired.
        Float parent = mass.remove("bird");
        if (parent != null) {
            String child = firstPresent(mass, BIRD_CHILDREN);
            if (child == null) {
                mass.put("bird", parent);
            } else {
                mass.merge(child, parent, Float::sum);
            }
        }

        List<Map.Entry<String, Float>> ordered = new ArrayList<>(mass.entrySet());
        ordered.sort(Map.Entry.<String, Float>comparingByValue().reversed());

        Map<String, Float> ranked = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : ordered) {
            ranked.put(entry.getKey(), entry.getValue());
        }

        String group = ordered.get(0).getKey();
        return new Evidence(
                sourceLabel.getOrDefault(group, bestLabel),
                strongestContribution.getOrDefault(group, bestScore),
                ranked);
    }

    private static String firstPresent(Map<String, Float> mass, List<String> keys) {
        for (String key : keys) {
            if (mass.containsKey(key)) {
                return key;
            }
        }
        return null;
    }

    /**
     * AudioSet display name to one catalogue {@code animal_type}. The most specific class
     * wins: "Owl" resolves to the four owls, never to the 54 catalogue birds, because the
     * generic parent adds no information once a child class has fired.
     *
     * <p>Anything the catalogue does not model (speech, engines, cattle, ducks, geese)
     * maps to nothing, so the caller can report the sound as inconclusive.
     */
    static List<String> typesFor(String label) {
        if (label == null || label.isBlank()) {
            return List.of();
        }
        String lower = label.toLowerCase(Locale.ROOT);

        if (lower.contains("roaring cats")) {
            return BIG_CATS;
        }
        if (lower.equals("owl")) {
            return List.of("owl");
        }
        if (lower.equals("crow")) {
            return List.of("crow");
        }
        if (lower.contains("pigeon") || lower.contains("dove")) {
            return List.of("pigeon");
        }
        if (lower.contains("whale")) {
            return List.of("whale");
        }
        if (lower.equals("howl") || lower.contains("wolves")) {
            return List.of("wolf");
        }
        if (lower.equals("roar") || lower.contains("growl")) {
            return List.of("big cat");
        }
        if (lower.contains("bird") || lower.equals("fowl") || lower.equals("turkey")
                || lower.contains("chicken") || lower.contains("crowing")) {
            return List.of("bird");
        }
        if (lower.equals("frog")) {
            return List.of("frog");
        }
        if (lower.equals("snake")) {
            return List.of("snake");
        }
        if (lower.equals("cricket") || lower.equals("insect")) {
            return List.of("insect");
        }
        return List.of();
    }

    private static int[] topIndexes(float[] scores, int count) {
        List<Integer> indexes = new ArrayList<>(scores.length);
        for (int i = 0; i < scores.length; i++) {
            indexes.add(i);
        }
        indexes.sort(Comparator.comparingDouble((Integer i) -> scores[i]).reversed());
        int limit = Math.min(count, indexes.size());
        int[] top = new int[limit];
        for (int i = 0; i < limit; i++) {
            top[i] = indexes.get(i);
        }
        return top;
    }

    private static long[] inputShape(OrtSession session, int length) {
        try {
            NodeInfo info = session.getInputInfo().values().iterator().next();
            if (info.getInfo() instanceof TensorInfo tensor && tensor.getShape().length == 2) {
                return new long[]{1, length};
            }
        } catch (Exception e) {
            // Fall through to the flat shape used by the reference YAMNet export.
        }
        return new long[]{length};
    }
}
