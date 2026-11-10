package com.endangered.species.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.endangered.species.web.ApiException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * MobileNetV2 (ImageNet) inference through ONNX Runtime. Kept as the fallback when the
 * CLIP files are not installed. Preprocessing matches the ONNX Model Zoo export: RGB,
 * {@code pixel / 255}, then ImageNet mean/std normalisation.
 */
@Service
public class ImageClassifier {

    static final int SIZE = 224;

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private final MlModelRegistry models;

    public ImageClassifier(MlModelRegistry models) {
        this.models = models;
    }

    /** Returns the cleaned, lower-case species name for the image. */
    public String classify(BufferedImage source) {
        if (source == null) {
            throw ApiException.badRequest("No image data");
        }
        OrtSession session = models.imageSession();
        float[] pixels = toTensor(source);
        try (OnnxTensor input = OnnxTensor.createTensor(models.environment(),
                FloatBuffer.wrap(pixels), new long[]{1, 3, SIZE, SIZE})) {
            String inputName = session.getInputNames().iterator().next();
            try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
                float[] scores = OnnxValues.scores(OnnxValues.first(result));
                int index = OnnxValues.argmax(scores);
                if (index < 0) {
                    throw ApiException.unavailable("The image model produced no predictions");
                }
                return LabelCleaner.clean(models.imageLabels().label(index));
            }
        } catch (OrtException e) {
            throw ApiException.unavailable("Image model failed: " + e.getMessage());
        }
    }

    static float[] toTensor(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, SIZE, SIZE, null);
        } finally {
            graphics.dispose();
        }

        int plane = SIZE * SIZE;
        float[] tensor = new float[3 * plane];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = scaled.getRGB(x, y);
                int offset = y * SIZE + x;
                tensor[offset] = normalize((rgb >> 16) & 0xFF, 0);
                tensor[plane + offset] = normalize((rgb >> 8) & 0xFF, 1);
                tensor[2 * plane + offset] = normalize(rgb & 0xFF, 2);
            }
        }
        return tensor;
    }

    private static float normalize(int channel, int index) {
        return (channel / 255f - MEAN[index]) / STD[index];
    }
}
