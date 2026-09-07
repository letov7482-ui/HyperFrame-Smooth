package com.hyperframe.core;

public final class SpikeDetector {

    private static final double MIN_SPIKE_MS = 25.0;
    private static final double SEVERE_SPIKE_MS = 100.0;

    private static final double SPIKE_MULTIPLIER = 2.5;
    private static final double MICROSTUTTER_MULTIPLIER = 1.8;

    private final AdaptiveBaseline baseline =
            new AdaptiveBaseline();

    private int consecutiveSpikes;
    private int totalSpikes;

    private double lastSpikeFrameTimeMs;
    private long lastSpikeFrameNumber;

    public SpikeResult analyze(FrameMonitor monitor) {
        FrameSample latest =
                monitor.getLatestFrame();

        if (latest == null) {
            return SpikeResult.NONE;
        }

        double frameTimeMs =
                latest.frameTimeMs();

        /*
         * Сначала анализируем текущий кадр
         * относительно уже накопленной нормы.
         */
        double relativeCost =
                baseline.getRelativeCost(frameTimeMs);

        boolean absoluteSpike =
                frameTimeMs >= MIN_SPIKE_MS;

        boolean relativeSpike =
                baseline.isReady()
                        && relativeCost >= SPIKE_MULTIPLIER;

        boolean spike =
                absoluteSpike || relativeSpike;

        /*
         * После анализа добавляем кадр
         * в baseline.
         *
         * Поэтому текущий spike не может
         * мгновенно уничтожить нашу норму.
         */
        baseline.record(frameTimeMs);

        if (!spike) {
            consecutiveSpikes = 0;

            return SpikeResult.NONE;
        }

        totalSpikes++;
        consecutiveSpikes++;

        lastSpikeFrameTimeMs =
                frameTimeMs;

        lastSpikeFrameNumber =
                latest.frameNumber();

        if (frameTimeMs >= SEVERE_SPIKE_MS) {
            return new SpikeResult(
                    SpikeType.SEVERE,
                    frameTimeMs,
                    relativeCost,
                    latest.frameNumber(),
                    consecutiveSpikes
            );
        }

        if (consecutiveSpikes >= 3
                || (
                baseline.isReady()
                        && relativeCost
                        >= MICROSTUTTER_MULTIPLIER
        )) {
            return new SpikeResult(
                    SpikeType.MICROSTUTTER,
                    frameTimeMs,
                    relativeCost,
                    latest.frameNumber(),
                    consecutiveSpikes
            );
        }

        return new SpikeResult(
                SpikeType.SPIKE,
                frameTimeMs,
                relativeCost,
                latest.frameNumber(),
                consecutiveSpikes
        );
    }

    public AdaptiveBaseline getBaseline() {
        return baseline;
    }

    public int getTotalSpikes() {
        return totalSpikes;
    }

    public int getConsecutiveSpikes() {
        return consecutiveSpikes;
    }

    public double getLastSpikeFrameTimeMs() {
        return lastSpikeFrameTimeMs;
    }

    public long getLastSpikeFrameNumber() {
        return lastSpikeFrameNumber;
    }

    public void reset() {
        baseline.reset();

        consecutiveSpikes = 0;
        totalSpikes = 0;

        lastSpikeFrameTimeMs = 0.0;
        lastSpikeFrameNumber = 0L;
    }

    public enum SpikeType {
        NONE,
        SPIKE,
        MICROSTUTTER,
        SEVERE
    }

    public record SpikeResult(
            SpikeType type,
            double frameTimeMs,
            double relativeCost,
            long frameNumber,
            int consecutiveSpikes
    ) {

        public static final SpikeResult NONE =
                new SpikeResult(
                        SpikeType.NONE,
                        0.0,
                        0.0,
                        -1L,
                        0
                );

        public boolean detected() {
            return type != SpikeType.NONE;
        }

        public boolean isSevere() {
            return type == SpikeType.SEVERE;
        }

        public boolean isMicrostutter() {
            return type == SpikeType.MICROSTUTTER;
        }
    }
}
