package com.hyperframe.core;

public record PerformanceSnapshot(
        long timestampNanos,
        long frameNumber,
        double averageFps,
        double averageFrameTimeMs,
        double onePercentLowFps,
        double zeroPointOnePercentLowFps,
        double p95FrameTimeMs,
        double p99FrameTimeMs,
        double worstFrameTimeMs,
        double frameTimeStandardDeviation,
        double stabilityScore,
        int spikeCount
) {

    public static PerformanceSnapshot capture(
            FrameMonitor monitor,
            FrameStatistics statistics,
            double spikeThresholdMs
    ) {
        return new PerformanceSnapshot(
                System.nanoTime(),
                monitor.getFrameNumber(),
                statistics.getAverageFps(),
                statistics.getAverageFrameTimeMs(),
                statistics.getOnePercentLowFps(),
                statistics.getZeroPointOnePercentLowFps(),
                statistics.getP95FrameTimeMs(),
                statistics.getP99FrameTimeMs(),
                statistics.getWorstFrameTimeMs(),
                statistics.getFrameTimeStandardDeviation(),
                statistics.getStabilityScore(),
                monitor.getSpikeCount(spikeThresholdMs)
        );
    }

    public boolean hasData() {
        return frameNumber > 0;
    }

    public boolean isStable() {
        return stabilityScore >= 90.0;
    }

    public boolean isUnstable() {
        return stabilityScore < 70.0;
    }
          }
