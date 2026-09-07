package com.hyperframe.core;

public record FrameSample(
        long frameNumber,
        long timestampNanos,
        double frameTimeMs
) {
    public double fps() {
        if (frameTimeMs <= 0.0) {
            return 0.0;
        }

        return 1000.0 / frameTimeMs;
    }

    public boolean isSpike(double thresholdMs) {
        return frameTimeMs >= thresholdMs;
    }
}
