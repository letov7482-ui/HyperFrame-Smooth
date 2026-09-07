package com.hyperframe.core;

import java.util.Arrays;

public final class FrameMonitor {

    private static final int HISTORY_SIZE = 600;

    private final FrameSample[] history =
            new FrameSample[HISTORY_SIZE];

    private long frameNumber;
    private long lastFrameTimeNanos;

    public void beginFrame() {
        long now = System.nanoTime();

        if (lastFrameTimeNanos != 0L) {
            double frameTimeMs =
                    (now - lastFrameTimeNanos) / 1_000_000.0;

            recordFrame(frameTimeMs);
        }

        lastFrameTimeNanos = now;
    }

    private void recordFrame(double frameTimeMs) {
        int index = (int) (frameNumber % HISTORY_SIZE);

        history[index] = new FrameSample(
                frameNumber,
                System.nanoTime(),
                frameTimeMs
        );

        frameNumber++;
    }

    public FrameSample getLatestFrame() {
        if (frameNumber == 0) {
            return null;
        }

        int index = (int) ((frameNumber - 1) % HISTORY_SIZE);
        return history[index];
    }

    public long getFrameNumber() {
        return frameNumber;
    }

    public double getAverageFrameTimeMs() {
        FrameSample[] samples = getSamples();

        if (samples.length == 0) {
            return 0.0;
        }

        double total = 0.0;

        for (FrameSample sample : samples) {
            total += sample.frameTimeMs();
        }

        return total / samples.length;
    }

    public double getAverageFps() {
        double frameTime = getAverageFrameTimeMs();

        if (frameTime <= 0.0) {
            return 0.0;
        }

        return 1000.0 / frameTime;
    }

    public double getPercentile(double percentile) {
        FrameSample[] samples = getSamples();

        if (samples.length == 0) {
            return 0.0;
        }

        double[] values = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            values[i] = samples[i].frameTimeMs();
        }

        Arrays.sort(values);

        double position =
                percentile * (values.length - 1);

        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);

        if (lower == upper) {
            return values[lower];
        }

        double fraction = position - lower;

        return values[lower]
                + (values[upper] - values[lower]) * fraction;
    }

    public double getP95FrameTimeMs() {
        return getPercentile(0.95);
    }

    public double getP99FrameTimeMs() {
        return getPercentile(0.99);
    }

    public double getWorstFrameTimeMs() {
        FrameSample[] samples = getSamples();

        double worst = 0.0;

        for (FrameSample sample : samples) {
            worst = Math.max(worst, sample.frameTimeMs());
        }

        return worst;
    }

    public int getSpikeCount(double thresholdMs) {
        int count = 0;

        for (FrameSample sample : getSamples()) {
            if (sample.isSpike(thresholdMs)) {
                count++;
            }
        }

        return count;
    }

    public FrameSample[] getSamples() {
        int count =
                (int) Math.min(frameNumber, HISTORY_SIZE);

        FrameSample[] result = new FrameSample[count];

        for (int i = 0; i < count; i++) {
            long number =
                    frameNumber - count + i;

            int index =
                    (int) (number % HISTORY_SIZE);

            result[i] = history[index];
        }

        return result;
    }

    public void reset() {
        Arrays.fill(history, null);
        frameNumber = 0L;
        lastFrameTimeNanos = 0L;
    }
}
