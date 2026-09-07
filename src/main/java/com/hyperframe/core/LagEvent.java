package com.hyperframe.core;

import java.util.Objects;

/**
 * Represents a complete HyperFrame performance event.
 *
 * A LagEvent combines:
 *
 * - the detected spike;
 * - the analyzed pattern;
 * - the confidence of that analysis;
 * - the replay surrounding the event.
 *
 * This class does not decide why a lag happened.
 * It only stores measured and analyzed information.
 */
public record LagEvent(
        long id,
        long createdAtNanos,
        PerformanceEvent event,
        PerformanceAnalyzer.AnalysisResult analysis,
        PerformanceReplay.Replay replay
) {

    public LagEvent {
        Objects.requireNonNull(
                event,
                "event"
        );

        Objects.requireNonNull(
                analysis,
                "analysis"
        );
    }

    /**
     * Returns the frame number where the event happened.
     */
    public long frameNumber() {
        return event.frameNumber();
    }

    /**
     * Returns the measured frame time.
     */
    public double frameTimeMs() {
        return event.frameTimeMs();
    }

    /**
     * Returns the baseline frame time.
     */
    public double baselineMs() {
        return event.baselineMs();
    }

    /**
     * Returns how many times more expensive the frame
     * was compared with the current baseline.
     */
    public double relativeCost() {
        return event.relativeCost();
    }

    /**
     * Returns the detected spike type.
     */
    public SpikeDetector.SpikeType type() {
        return event.type();
    }

    /**
     * Returns the detected performance pattern.
     */
    public PerformanceAnalyzer.Pattern pattern() {
        return analysis.pattern();
    }

    /**
     * Returns the human-readable analysis description.
     */
    public String description() {
        return analysis.description();
    }

    /**
     * Returns the analysis confidence from 0 to 100.
     *
     * Important:
     * this is confidence in the detected pattern,
     * not confidence about the actual root cause.
     */
    public int confidencePercent() {
        return analysis.confidencePercent();
    }

    /**
     * Returns whether this was a severe spike.
     */
    public boolean isSevere() {
        return event.isSevere();
    }

    /**
     * Returns whether this was classified as microstutter.
     */
    public boolean isMicrostutter() {
        return event.isMicrostutter();
    }

    /**
     * Returns whether a replay is already attached.
     */
    public boolean hasReplay() {
        return replay != null;
    }

    /**
     * Returns the replay frame count.
     */
    public int replayFrameCount() {
        if (replay == null) {
            return 0;
        }

        return replay.getFrameCount();
    }

    /**
     * Returns how much the spike exceeded the baseline.
     */
    public double excessFrameTimeMs() {
        return event.excessFrameTimeMs();
    }

    /**
     * Returns the FPS represented by the problematic frame.
     */
    public double eventFps() {
        if (event.frameTimeMs() <= 0.0) {
            return 0.0;
        }

        return 1000.0 / event.frameTimeMs();
    }

    /**
     * Returns the baseline FPS.
     */
    public double baselineFps() {
        if (event.baselineMs() <= 0.0) {
            return 0.0;
        }

        return 1000.0 / event.baselineMs();
    }

    /**
     * Returns a short diagnostic summary.
     *
     * This will later be replaced by the GUI presentation.
     */
    public String summary() {
        return "LagEvent #"
                + id
                + " | frame="
                + frameNumber()
                + " | frametime="
                + format(frameTimeMs())
                + "ms"
                + " | baseline="
                + format(baselineMs())
                + "ms"
                + " | type="
                + type()
                + " | pattern="
                + pattern()
                + " | confidence="
                + confidencePercent()
                + "%";
    }

    private static String format(double value) {
        return String.format(
                java.util.Locale.ROOT,
                "%.2f",
                value
        );
    }
}
