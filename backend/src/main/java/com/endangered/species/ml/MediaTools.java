package com.endangered.species.ml;

import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes uploaded media with the FFmpeg natives shipped by the {@code bytedeco}
 * dependencies, replacing the OpenCV/librosa calls of the Python service.
 */
final class MediaTools {

    private static final Logger log = LoggerFactory.getLogger(MediaTools.class);

    private MediaTools() {
    }

    /** Reads an audio/video file as mono 16 kHz float samples (the YAMNet contract). */
    static float[] decodeMono(Path file, int sampleRate) {
        if (file == null) {
            return new float[0];
        }
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile());
        grabber.setSampleRate(sampleRate);
        grabber.setAudioChannels(1);
        grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_FLT);
        try {
            grabber.start();
            List<float[]> chunks = new ArrayList<>();
            int total = 0;
            Frame frame;
            while ((frame = grabber.grabSamples()) != null) {
                float[] chunk = toFloats(frame.samples);
                if (chunk.length > 0) {
                    chunks.add(chunk);
                    total += chunk.length;
                }
            }
            float[] merged = new float[total];
            int offset = 0;
            for (float[] chunk : chunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.length);
                offset += chunk.length;
            }
            return merged;
        } catch (Exception e) {
            log.warn("Could not decode audio from {}: {}", file, e.getMessage());
            return new float[0];
        } finally {
            close(grabber);
        }
    }

    /** Grabs every {@code step}-th video frame, up to {@code limit} frames. */
    static List<BufferedImage> sampleFrames(Path file, int step, int limit) {
        List<BufferedImage> frames = new ArrayList<>();
        if (file == null) {
            return frames;
        }
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile());
        Java2DFrameConverter converter = new Java2DFrameConverter();
        try {
            grabber.start();
            int index = 0;
            Frame frame;
            while (frames.size() < limit && (frame = grabber.grabImage()) != null) {
                if (index % step == 0) {
                    BufferedImage image = converter.getBufferedImage(frame);
                    if (image != null) {
                        frames.add(image);
                    }
                }
                index++;
            }
        } catch (Exception e) {
            log.warn("Could not decode video from {}: {}", file, e.getMessage());
        } finally {
            converter.close();
            close(grabber);
        }
        return frames;
    }

    /**
     * Evenly spaced frames from the whole clip. Sampling from the start only missed
     * anything that appears later than the first few seconds; seeking spreads the
     * evidence over the full duration.
     */
    static List<BufferedImage> sampleFramesAcross(Path file, int count) {
        List<BufferedImage> frames = seekFrames(file, count);
        if (frames.isEmpty()) {
            log.debug("Seeking returned no frames for {}; falling back to stream sampling", file);
            frames = sampleFrames(file, 24, Math.max(count, 1));
        }
        return frames;
    }

    private static List<BufferedImage> seekFrames(Path file, int count) {
        List<BufferedImage> frames = new ArrayList<>();
        if (file == null || count <= 0) {
            return frames;
        }
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile());
        Java2DFrameConverter converter = new Java2DFrameConverter();
        try {
            grabber.start();
            long duration = grabber.getLengthInTime();
            if (duration <= 0) {
                return frames;
            }
            for (int i = 0; i < count; i++) {
                long target = duration * (2L * i + 1) / (2L * count);
                try {
                    grabber.setTimestamp(target);
                    Frame frame = grabber.grabImage();
                    if (frame == null) {
                        continue;
                    }
                    BufferedImage image = converter.getBufferedImage(frame);
                    if (image != null) {
                        frames.add(image);
                    }
                } catch (Exception e) {
                    log.debug("Could not seek to {} us in {}: {}", target, file, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not decode video from {}: {}", file, e.getMessage());
        } finally {
            converter.close();
            close(grabber);
        }
        return frames;
    }

    private static float[] toFloats(Buffer[] samples) {
        if (samples == null || samples.length == 0 || samples[0] == null) {
            return new float[0];
        }
        Buffer buffer = samples[0];
        if (buffer instanceof FloatBuffer floats) {
            float[] out = new float[floats.remaining()];
            floats.get(out);
            return out;
        }
        if (buffer instanceof ShortBuffer shorts) {
            float[] out = new float[shorts.remaining()];
            for (int i = 0; i < out.length; i++) {
                out[i] = shorts.get() / 32768f;
            }
            return out;
        }
        return new float[0];
    }

    private static void close(FFmpegFrameGrabber grabber) {
        try {
            grabber.stop();
            grabber.close();
        } catch (Exception e) {
            log.debug("Ignoring FFmpeg shutdown problem: {}", e.getMessage());
        }
    }
}
