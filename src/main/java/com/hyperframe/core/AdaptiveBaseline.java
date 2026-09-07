package com.hyperframe.core;

public final class AdaptiveBaseline {

    private static final int HISTORY_SIZE = 120;

    private static final double MIN_BASELINE_MS = 1.0;
    private static final double MAX_BASELINE_MS = 1000.0;

    private final double[] frameTimes =
            new double[HISTORY_SIZE];

    private int size;
    private int index;

    public void record(double frameTimeMs) {
        if (frameTimeMs <= 0.0
                || Double.isNaN(frameTimeMs)
                || Double.isInfinite(frameTimeMs)) {
            return;
        }

        frameTimes[index] = frameTimeMs;
        index = (index + 1) % HISTORY_SIZE;

        if (size < HISTORY_SIZE) {
            size++;
        }
    }

    public double getBaselineMs() {
        if (size == 0) {
            return 0.0;
        }

        double[] values = getValues();
        java.util.Arrays.sort(values);

        /*
         * Используем медиану.
         *
         * Это важно:
         * один огромный spike не должен
         * мгновенно испортить нашу "норму".
         */
        double baseline = values[values.length / 2];

        return clamp(
                baseline,
                MIN_BASELINE_MS,
                MAX_BASELINE_MS
        );
    }

    public double getMeanMs() {
        if (size == 0) {
            return 0.0;
        }

        double total = 0.0;

        for (int i = 0; i < size; i++) {
            total += frameTimes[i];
        }

        return total / size;
    }

    public double getDeviationMs() {
        if (size < 2) {
            return 0.0;
        }

        double mean = getMeanMs();
        double squared = 0.0;

        for (int i = 0; i < size; i++) {
            double difference =
                    frameTimes[i] - mean;

            squared += difference * difference;
        }

        return Math.sqrt(squared / size);
    }

    /**
     * Показывает, во сколько раз текущий кадр
     * медленнее обычного.
     */
    public double getRelativeCost(double frameTimeMs) {
        double baseline = getBaselineMs();

        if (baseline <= 0.0
                || frameTimeMs <= 0.0) {
            return 0.0;
        }

        return frameTimeMs / baseline;
    }

    public boolean isReady() {
        return size >= 30;
    }

    public int getSampleCount() {
        return size;
    }

    public void reset() {
        java.util.Arrays.fill(frameTimes, 0.0);

        size = 0;
        index = 0;
    }

    private double[] getValues() {
        double[] values = new double[size];

        System.arraycopy(
                frameTimes,
                0,
                values,
                0,
                size
        );

        return values;
    }

    private double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
