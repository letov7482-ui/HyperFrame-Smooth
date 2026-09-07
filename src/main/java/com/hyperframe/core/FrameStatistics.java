package com.hyperframe.core;

/**
 * Lightweight frame statistics facade.
 *
 * Statistics are cached for a small number of frames so HyperFrame
 * does not repeatedly perform expensive calculations every render
 * frame.
 *
 * This is intentionally conservative:
 *
 * - no input changes
 * - no render changes
 * - no artificial delays
 * - no FPS limiting
 *
 * The goal is to make HyperFrame itself as cheap as possible.
 */
public final class FrameStatistics {

    private static final int CACHE_INTERVAL_FRAMES = 8;

    private final FrameMonitor monitor;

    private long lastCalculatedFrame = -1L;

    private double averageFps;
    private double averageFrameTimeMs;
    private double onePercentLowFps;
    private double zeroPointOnePercentLowFps;
    private double p95FrameTimeMs;
    private double p99FrameTimeMs;
    private double worstFrameTimeMs;
    private double frameTimeStandardDeviation;
    private double stabilityScore;

    public FrameStatistics(FrameMonitor monitor) {
        if (monitor == null) {
            throw new IllegalArgumentException(
                    "monitor cannot be null"
            );
        }

        this.monitor = monitor;
    }

    public double getAverageFps() {
        updateIfNeeded();
        return averageFps;
    }

    public double getAverageFrameTimeMs() {
        updateIfNeeded();
        return averageFrameTimeMs;
    }

    public double getOnePercentLowFps() {
        updateIfNeeded();
        return onePercentLowFps;
    }

    public double getZeroPointOnePercentLowFps() {
        updateIfNeeded();
        return zeroPointOnePercentLowFps;
    }

    public double getP95FrameTimeMs() {
        updateIfNeeded();
        return p95FrameTimeMs;
    }

    public double getP99FrameTimeMs() {
        updateIfNeeded();
        return p99FrameTimeMs;
    }

    public double getWorstFrameTimeMs() {
        updateIfNeeded();
        return worstFrameTimeMs;
    }

    public int getSpikeCount(
            double thresholdMs
    ) {
        return monitor.getSpikeCount(
                thresholdMs
        );
    }

    public double getFrameTimeStandardDeviation() {
        updateIfNeeded();
        return frameTimeStandardDeviation;
    }

    public double getStabilityScore() {
        updateIfNeeded();
        return stabilityScore;
    }

    /**
     * Forces the next statistics request to recalculate.
     */
    public void invalidate() {
        lastCalculatedFrame = -1L;
    }

    /**
     * Resets the cached statistics.
     */
    public void reset() {
        lastCalculatedFrame = -1L;

        averageFps = 0.0;
        averageFrameTimeMs = 0.0;
        onePercentLowFps = 0.0;
        zeroPointOnePercentLowFps = 0.0;
        p95FrameTimeMs = 0.0;
        p99FrameTimeMs = 0.0;
        worstFrameTimeMs = 0.0;
        frameTimeStandardDeviation = 0.0;
        stabilityScore = 100.0;
    }

    private void updateIfNeeded() {
        long currentFrame =
                monitor.getFrameNumber();

        if (currentFrame <= 0L) {
            return;
        }

        if (lastCalculatedFrame >= 0L
                && currentFrame - lastCalculatedFrame
                < CACHE_INTERVAL_FRAMES) {
            return;
        }

        calculate();

        lastCalculatedFrame =
                currentFrame;
    }

    private void calculate() {
        averageFrameTimeMs =
                monitor.getAverageFrameTimeMs();

        if (averageFrameTimeMs > 0.0) {
            averageFps =
                    1000.0 / averageFrameTimeMs;
        } else {
            averageFps = 0.0;
        }

        double p99 =
                monitor.getPercentile(0.99);

        double p999 =
                monitor.getPercentile(0.999);

        p95FrameTimeMs =
                monitor.getP95FrameTimeMs();

        p99FrameTimeMs =
                p99;

        worstFrameTimeMs =
                monitor.getWorstFrameTimeMs();

        onePercentLowFps =
                frameTimeToFps(p99);

        zeroPointOnePercentLowFps =
                frameTimeToFps(p999);

        frameTimeStandardDeviation =
                calculateStandardDeviation(
                        averageFrameTimeMs
                );

        stabilityScore =
                calculateStabilityScore(
                        frameTimeStandardDeviation
                );
    }

    private double calculateStandardDeviation(
            double average
    ) {
        FrameSample[] samples =
                monitor.getSamples();

        if (samples.length == 0) {
            return 0.0;
        }

        double squaredDifference = 0.0;

        for (FrameSample sample : samples) {
            double difference =
                    sample.frameTimeMs()
                            - average;

            squaredDifference +=
                    difference * difference;
        }

        return Math.sqrt(
                squaredDifference
                        / samples.length
        );
    }

    private double calculateStabilityScore(
            double deviation
    ) {
        if (deviation <= 0.0) {
            return 100.0;
        }

        double score =
                100.0 - (deviation * 12.0);

        return Math.max(
                0.0,
                Math.min(
                        100.0,
                        score
                )
        );
    }

    private double frameTimeToFps(
            double frameTimeMs
    ) {
        if (frameTimeMs <= 0.0) {
            return 0.0;
        }

        return 1000.0 / frameTimeMs;
    }
}
