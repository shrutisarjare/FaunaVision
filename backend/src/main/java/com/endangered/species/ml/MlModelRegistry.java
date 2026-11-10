package com.endangered.species.ml;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.endangered.species.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lazily opens the ONNX models that live outside the jar (see
 * {@code scripts/fetch-models.ps1}). When a model is missing the identify endpoints
 * answer 503 with an actionable message instead of failing at start-up.
 */
@Component
public class MlModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(MlModelRegistry.class);

    private final Path modelDir;
    private final String imageModelName;
    private final String imageLabelsName;
    private final String audioModelName;
    private final Path clipDir;
    private final String clipVisionName;
    private final String clipTextName;
    private final String clipVocabName;
    private final String clipMergesName;
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();

    private volatile OrtSession imageSession;
    private volatile OrtSession audioSession;
    private volatile OrtSession clipVisionSession;
    private volatile OrtSession clipTextSession;
    private volatile ImageNetLabels imageLabels;
    private volatile String imageFailure;
    private volatile String audioFailure;
    private volatile String clipFailure;

    public MlModelRegistry(
            @Value("${app.ml.model-dir}") String modelDir,
            @Value("${app.ml.image-model}") String imageModelName,
            @Value("${app.ml.image-labels}") String imageLabelsName,
            @Value("${app.ml.audio-model}") String audioModelName,
            @Value("${app.ml.clip-dir}") String clipDir,
            @Value("${app.ml.clip-vision-model}") String clipVisionName,
            @Value("${app.ml.clip-text-model}") String clipTextName,
            @Value("${app.ml.clip-vocab}") String clipVocabName,
            @Value("${app.ml.clip-merges}") String clipMergesName) {
        this.modelDir = Paths.get(modelDir).toAbsolutePath().normalize();
        this.imageModelName = imageModelName;
        this.imageLabelsName = imageLabelsName;
        this.audioModelName = audioModelName;
        this.clipDir = this.modelDir.resolve(clipDir);
        this.clipVisionName = clipVisionName;
        this.clipTextName = clipTextName;
        this.clipVocabName = clipVocabName;
        this.clipMergesName = clipMergesName;
    }

    public OrtEnvironment environment() {
        return environment;
    }

    public synchronized OrtSession imageSession() {
        if (imageSession != null) {
            return imageSession;
        }
        if (imageFailure != null) {
            throw ApiException.unavailable(imageFailure);
        }
        try {
            imageSession = open(imageModelName);
            return imageSession;
        } catch (ApiException e) {
            imageFailure = e.getMessage();
            throw e;
        }
    }

    public synchronized OrtSession audioSession() {
        if (audioSession != null) {
            return audioSession;
        }
        if (audioFailure != null) {
            throw ApiException.unavailable(audioFailure);
        }
        try {
            audioSession = open(audioModelName);
            return audioSession;
        } catch (ApiException e) {
            audioFailure = e.getMessage();
            throw e;
        }
    }

    public ImageNetLabels imageLabels() {
        ImageNetLabels loaded = imageLabels;
        if (loaded == null) {
            synchronized (this) {
                if (imageLabels == null) {
                    imageLabels = ImageNetLabels.loadOrFallback(modelDir.resolve(imageLabelsName));
                }
                loaded = imageLabels;
            }
        }
        return loaded;
    }

    /** Directory holding the CLIP export (models + tokenizer assets). */
    public Path clipDir() {
        return clipDir;
    }

    public Path clipVocabPath() {
        return clipDir.resolve(clipVocabName);
    }

    public Path clipVisionPath() {
        return clipDir.resolve(clipVisionName);
    }

    public Path clipTextPath() {
        return clipDir.resolve(clipTextName);
    }

    public Path clipMergesPath() {
        return clipDir.resolve(clipMergesName);
    }

    /** True when every CLIP file is on disk, so identification can use it. */
    public boolean clipInstalled() {
        return Files.isRegularFile(clipDir.resolve(clipVisionName))
                && Files.isRegularFile(clipDir.resolve(clipTextName))
                && Files.isRegularFile(clipDir.resolve(clipVocabName))
                && Files.isRegularFile(clipDir.resolve(clipMergesName));
    }

    public synchronized OrtSession clipVisionSession() {
        return clipSession(true);
    }

    public synchronized OrtSession clipTextSession() {
        return clipSession(false);
    }

    private OrtSession clipSession(boolean vision) {
        if (vision ? clipVisionSession != null : clipTextSession != null) {
            return vision ? clipVisionSession : clipTextSession;
        }
        if (clipFailure != null) {
            throw ApiException.unavailable(clipFailure);
        }
        try {
            OrtSession opened = open(clipDir.resolve(vision ? clipVisionName : clipTextName));
            if (vision) {
                clipVisionSession = opened;
            } else {
                clipTextSession = opened;
            }
            return opened;
        } catch (ApiException e) {
            clipFailure = e.getMessage();
            throw e;
        }
    }

    /** Absolute path of the model directory, used by diagnostics and tests. */
    public Path modelDir() {
        return modelDir;
    }

    private OrtSession open(String fileName) {
        return open(modelDir.resolve(fileName));
    }

    private OrtSession open(Path path) {
        if (!Files.isRegularFile(path)) {
            throw ApiException.unavailable("Model " + path.getFileName() + " is missing from " + path.getParent()
                    + ". Run scripts/fetch-models.ps1 to download it.");
        }
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            log.info("Loading ONNX model {}", path);
            return environment.createSession(path.toString(), options);
        } catch (OrtException e) {
            throw ApiException.unavailable("Could not load model " + path.getFileName() + ": " + e.getMessage());
        }
    }
}
