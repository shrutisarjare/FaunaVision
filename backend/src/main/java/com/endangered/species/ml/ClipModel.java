package com.endangered.species.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.endangered.species.web.ApiException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * CLIP (clip-vit-base-patch32) inference. Both encoders project into the same 512-d
 * space, so an image embedding can be compared with species-name embeddings by a plain
 * dot product. Unlike MobileNetV2 this is open-vocabulary: it can score any label the
 * catalogue contains, not just the 1000 ImageNet classes.
 */
@Service
public class ClipModel {

    static final int SIZE = 224;
    private static final int BATCH = 16;

    /** Values from the export's {@code preprocessor_config.json}. */
    private static final float[] MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] STD = {0.26862954f, 0.26130258f, 0.27577711f};

    private final MlModelRegistry models;
    private volatile ClipTokenizer tokenizer;

    public ClipModel(MlModelRegistry models) {
        this.models = models;
    }

    /** True when the CLIP files are installed; otherwise callers use the ImageNet path. */
    public boolean available() {
        return models.clipInstalled();
    }

    /** Unit-length image embedding from the standard centre crop. */
    public float[] embedImage(BufferedImage source) {
        if (source == null) {
            throw ApiException.badRequest("No image data");
        }
        OrtSession session = models.clipVisionSession();
        float[] pixels = toTensor(source);
        try (OnnxTensor input = OnnxTensor.createTensor(models.environment(),
                FloatBuffer.wrap(pixels), new long[]{1, 3, SIZE, SIZE})) {
            String inputName = session.getInputNames().iterator().next();
            try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
                float[][] rows = OnnxValues.matrix(OnnxValues.first(result));
                if (rows.length == 0) {
                    throw ApiException.unavailable("The CLIP vision model produced no embedding");
                }
                return OnnxValues.unit(rows[0]);
            }
        } catch (OrtException e) {
            throw ApiException.unavailable("CLIP vision model failed: " + e.getMessage());
        }
    }

    /** Unit-length embedding of one caption, so a description can be matched too. */
    public float[] embedText(String text) {
        List<float[]> embeddings = embedTexts(List.of(text == null ? "" : text));
        if (embeddings.isEmpty()) {
            throw ApiException.unavailable("The CLIP text model produced no embedding");
        }
        return embeddings.get(0);
    }

    /** Unit-length text embeddings, encoded in batches to keep start-up work short. */
    public List<float[]> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        ClipTokenizer tokens = tokenizer();
        OrtSession session = models.clipTextSession();
        String inputName = session.getInputNames().iterator().next();
        List<float[]> embeddings = new ArrayList<>(texts.size());

        for (int start = 0; start < texts.size(); start += BATCH) {
            int count = Math.min(BATCH, texts.size() - start);
            long[] ids = new long[count * ClipTokenizer.CONTEXT_LENGTH];
            for (int i = 0; i < count; i++) {
                int[] encoded = tokens.encode(texts.get(start + i));
                int offset = i * ClipTokenizer.CONTEXT_LENGTH;
                for (int j = 0; j < encoded.length; j++) {
                    ids[offset + j] = encoded[j];
                }
            }
            try (OnnxTensor input = OnnxTensor.createTensor(models.environment(),
                    LongBuffer.wrap(ids), new long[]{count, ClipTokenizer.CONTEXT_LENGTH})) {
                try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
                    for (float[] row : OnnxValues.matrix(OnnxValues.first(result))) {
                        embeddings.add(OnnxValues.unit(row));
                    }
                }
            } catch (OrtException e) {
                throw ApiException.unavailable("CLIP text model failed: " + e.getMessage());
            }
        }
        return embeddings;
    }

    /**
     * CLIP preprocessing: resize the shortest edge to 224, centre-crop, rescale and
     * normalise per channel. The crop keeps the aspect ratio, unlike the plain 224×224
     * stretch used for the ImageNet fallback.
     */
    static float[] toTensor(BufferedImage source) {
        float[] tensor = new float[3 * SIZE * SIZE];
        fill(tensor, centreCrop(source, SIZE));
        return tensor;
    }

    private static void fill(float[] target, BufferedImage image) {
        int plane = SIZE * SIZE;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = image.getRGB(x, y);
                int index = y * SIZE + x;
                target[index] = normalize((rgb >> 16) & 0xFF, 0);
                target[plane + index] = normalize((rgb >> 8) & 0xFF, 1);
                target[2 * plane + index] = normalize(rgb & 0xFF, 2);
            }
        }
    }

    private static BufferedImage centreCrop(BufferedImage source, int size) {
        int width = Math.max(1, source.getWidth());
        int height = Math.max(1, source.getHeight());
        double scale = (double) size / Math.min(width, height);
        int targetWidth = Math.max(size, (int) Math.round(width * scale));
        int targetHeight = Math.max(size, (int) Math.round(height * scale));

        BufferedImage resized = resize(source, targetWidth, targetHeight);
        return resized.getSubimage((targetWidth - size) / 2, (targetHeight - size) / 2, size, size);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static float normalize(int channel, int index) {
        return (channel / 255f - MEAN[index]) / STD[index];
    }

    private ClipTokenizer tokenizer() {
        ClipTokenizer loaded = tokenizer;
        if (loaded == null) {
            synchronized (this) {
                if (tokenizer == null) {
                    try {
                        tokenizer = ClipTokenizer.load(models.clipVocabPath(), models.clipMergesPath());
                    } catch (IOException e) {
                        throw ApiException.unavailable("Could not load the CLIP tokenizer: " + e.getMessage());
                    }
                }
                loaded = tokenizer;
            }
        }
        return loaded;
    }
}
