package com.hyperframe.core;

public final class SpikeDetector {

    /**
     * Кадр считается подозрительным, если он заметно
     * медленнее обычного кадра.
     */
    private static final double MIN_SPIKE_MS = 25.0;

    /**
     * Очень длинный кадр.
     */
    private static final double SEVERE_SPIKE_MS = 100.0;

    /**
     * Сколько последних кадров анализируем.
     */
    private static final int ANALYSIS_WINDOW = 30;

    private int consecutiveSpikes;
    private int totalSpikes;

    private double lastSpikeFrameTimeMs;
    private long lastSpikeFrameNumber;

    /**
     * Анализирует последний кадр.
     *
     * @return результат анализа
     */
    public SpikeResult analyze(FrameMonitor monitor) {
        FrameSample latest = monitor.getLatestFrame();

        if (latest == null) {
            return SpikeResult.NONE;
        }

        double frameTimeMs = latest.frameTimeMs();

        if (frameTimeMs < MIN_SPIKE_MS) {
            consecutiveSpikes = 0;
            return SpikeResult.NONE;
        }

        totalSpikes++;
        consecutiveSpikes++;

        lastSpikeFrameTimeMs = frameTimeMs;
        lastSpikeFrameNumber = latest.frameNumber();

        if (frameTimeMs >= SEVERE_SPIKE_MS) {
            return new SpikeResult(
                    SpikeType.SEVERE,
                    frameTimeMs,
                    latest.frameNumber(),
                    consecutiveSpikes
            );
        }

        if (consecutiveSpikes >= 3) {
            return new SpikeResult(
                    SpikeType.MICROSTUTTER,
                    frameTimeMs,
                    latest.frameNumber(),
                    consecutiveSpikes
            );
        }

        return new SpikeResult(
                SpikeType.SPIKE,
                frameTimeMs,
                latest.frameNumber(),
                consecutiveSpikes
        );
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
            long frameNumber,
            int consecutiveSpikes
    ) {

        public static final SpikeResult NONE =
                new SpikeResult(
                        SpikeType.NONE,
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
