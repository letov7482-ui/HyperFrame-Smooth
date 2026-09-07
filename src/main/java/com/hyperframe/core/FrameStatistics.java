package com.hyperframe.core;

public final class FrameStatistics {

    private final FrameMonitor monitor;

    public FrameStatistics(FrameMonitor monitor) {
        this.monitor = monitor;
    }

    public double getAverageFps() {
        return monitor.getAverageFps();
    }

    public double getAverageFrameTimeMs() {
        return monitor.getAverageFrameTimeMs();
    }

    /**
     * 1% low FPS.
     *
     * Берём 99-й percentile frametime:
     * самые медленные 1% кадров.
     */
    public double getOnePercentLowFps() {
        return frameTimeToFps(
                monitor.getPercentile(0.99)
        );
    }

    /**
     * 0.1% low FPS.
     *
     * Берём 99.9-й percentile frametime:
     * самые медленные 0.1% кадров.
     */
    public double getZeroPointOnePercentLowFps() {
        return frameTimeToFps(
                monitor.getPercentile(0.999)
        );
    }

    public double getP95FrameTimeMs() {
        return monitor.getP95FrameTimeMs();
    }

    public double getP99FrameTimeMs() {
        return monitor.getP99FrameTimeMs();
    }

    public double getWorstFrameTimeMs() {
        return monitor.getWorstFrameTimeMs();
    }

    public int getSpikeCount(double thresholdMs) {
        return monitor.getSpikeCount(thresholdMs);
    }

    /**
     * Среднеквадратичное отклонение frametime.
     *
     * Чем меньше значение, тем стабильнее кадры.
     */
    public double getFrameTimeStandardDeviation() {
        FrameSample[] samples = monitor.getSamples();

        if (samples.length == 0) {
            return 0.0;
        }

        double average =
                monitor.getAverageFrameTimeMs();

        double squaredDifference = 0.0;

        for (FrameSample sample : samples) {
            double difference =
                    sample.frameTimeMs() - average;

            squaredDifference +=
                    difference * difference;
        }

        return Math.sqrt(
                squaredDifference / samples.length
        );
    }

    /**
     * Простая оценка стабильности кадра от 0 до 100.
     *
     * Это пока диагностический показатель,
     * а не "магическая оценка производительности".
     */
    public double getStabilityScore() {
        double deviation =
                getFrameTimeStandardDeviation();

        if (deviation <= 0.0) {
            return 100.0;
        }

        double score =
                100.0 - (deviation * 12.0);

        return Math.max(
                0.0,
                Math.min(100.0, score)
        );
    }

    private double frameTimeToFps(double frameTimeMs) {
        if (frameTimeMs <= 0.0) {
            return 0.0;
        }

        return 1000.0 / frameTimeMs;
    }
}
