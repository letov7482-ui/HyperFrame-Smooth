package com.hyperframe.core;

public record PerformanceEvent(
        long frameNumber,
        long timestampNanos,
        double frameTimeMs,
        double baselineMs,
        double relativeCost,
        SpikeDetector.SpikeType type
) {

    public boolean isSpike() {
        return type != SpikeDetector.SpikeType.NONE;
    }

    public boolean isSevere() {
        return type == SpikeDetector.SpikeType.SEVERE;
    }

    public boolean isMicrostutter() {
        return type == SpikeDetector.SpikeType.MICROSTUTTER;
    }

    public double excessFrameTimeMs() {
        return Math.max(
                0.0,
                frameTimeMs - baselineMs
        );
    }
}
