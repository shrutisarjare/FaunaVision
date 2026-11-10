package com.endangered.species.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtSession;
import java.util.Map;

/** Small helpers for reading ONNX outputs whose rank depends on the model export. */
final class OnnxValues {

    private OnnxValues() {
    }

    static Object first(OrtSession.Result result) {
        for (Map.Entry<String, OnnxValue> entry : result) {
            if (entry.getValue() instanceof OnnxTensor tensor) {
                return read(tensor);
            }
        }
        return null;
    }

    /**
     * The output whose last axis has {@code classes} entries. YAMNet exports the class
     * scores, a 1024-d embedding and a spectrogram side by side, and the map order is not
     * guaranteed, so picking the first tensor silently reads the wrong head.
     */
    static Object classScores(OrtSession.Result result, int classes) {
        Object fallback = null;
        for (Map.Entry<String, OnnxValue> entry : result) {
            if (!(entry.getValue() instanceof OnnxTensor tensor)) {
                continue;
            }
            long[] shape = tensor.getInfo().getShape();
            if (shape.length > 0 && shape[shape.length - 1] == classes) {
                return read(tensor);
            }
            if (fallback == null) {
                fallback = read(tensor);
            }
        }
        return fallback;
    }

    private static Object read(OnnxTensor tensor) {
        try {
            return tensor.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    /** Flattens the trailing classification axis into one score array. */
    static float[] scores(Object value) {
        if (value instanceof float[] flat) {
            return flat;
        }
        if (value instanceof float[][] rows) {
            return rows.length == 0 ? new float[0] : rows[0];
        }
        if (value instanceof float[][][] deep) {
            return deep.length == 0 || deep[0].length == 0 ? new float[0] : deep[0][0];
        }
        return new float[0];
    }

    /** Averages a [frames][classes] output over time, as the Python service did. */
    static float[] meanOverFrames(Object value) {
        if (value instanceof float[][] rows) {
            if (rows.length == 0) {
                return new float[0];
            }
            int classes = rows[0].length;
            float[] sums = new float[classes];
            for (float[] row : rows) {
                for (int i = 0; i < classes; i++) {
                    sums[i] += row[i];
                }
            }
            for (int i = 0; i < classes; i++) {
                sums[i] /= rows.length;
            }
            return sums;
        }
        return scores(value);
    }

    /** All rows of a 2-D output, e.g. {@code [batch][512]} embeddings. */
    static float[][] matrix(Object value) {
        if (value instanceof float[][] rows) {
            return rows;
        }
        if (value instanceof float[] flat) {
            return new float[][]{flat};
        }
        return new float[0][];
    }

    /** Scales a vector to unit length so a dot product becomes cosine similarity. */
    static float[] unit(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        if (sum <= 0 || vector.length == 0) {
            return vector.clone();
        }
        float scale = (float) (1.0 / Math.sqrt(sum));
        float[] unit = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            unit[i] = vector[i] * scale;
        }
        return unit;
    }

    static float dot(float[] left, float[] right) {
        int length = Math.min(left.length, right.length);
        float sum = 0;
        for (int i = 0; i < length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    static int argmax(float[] scores) {
        if (scores.length == 0) {
            return -1;
        }
        int best = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        return best;
    }
}
