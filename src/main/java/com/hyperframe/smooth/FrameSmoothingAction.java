package com.hyperframe.smooth;

import com.hyperframe.core.FrameMonitor;

/**
 * First real HyperFrame frame-smoothing action.
 *
 * This class deliberately does not add artificial delays
 * and does not modify input, sensitivity, FOV or FPS limits.
 *
 * It provides a safe activation window for future
 * render-side optimizations and continuously measures
 * whether the active strategy should remain enabled.
 */
public final class FrameSmoothingAction {

    private static final int WARMUP_FRAMES = 8;

    private static final int MAX_ACTIVE_FRAMES = 120;

    private static final double MAX_ALLOWED_REGRESSION = 0.05;

    private boolean enabled = true;

    private boolean active;

    private int activeFrames;

    private double activationBaselineMs;

    private double activationBaselineDeviationMs;

    private double latestFrameTimeMs;

    private double latestDeviationMs;

    private long activations;

    private long successfulWindows;

    private long abortedWindows;

    public boolean activate(
            FrameMonitor monitor
    ) {
        if (!enabled
                || active
                || monitor == null) {
            return false;
        }

        double baseline =
                monitor.getAverageFrameTimeMs();

        double deviation =
                calculateDeviation(monitor);

        if (!isValid(baseline)
                || !isValid(deviation)
                || baseline <= 0.0
                || deviation < 0.0) {
            return false;
        }

        activationBaselineMs = baseline;
        activationBaselineDeviationMs = deviation;

        latestFrameTimeMs = baseline;
        latestDeviationMs = deviation;

        activeFrames = 0;
        active = true;

        activations++;

        return true;
    }

    public Result update(
            FrameMonitor monitor
    ) {
        if (!active
                || monitor == null) {
            return Result.INACTIVE;
        }

        latestFrameTimeMs =
                monitor.getAverageFrameTimeMs();

        latestDeviationMs =
                calculateDeviation(monitor);

        if (!isValid(latestFrameTimeMs)
                || !isValid(latestDeviationMs)
                || latestFrameTimeMs <= 0.0
                || latestDeviationMs < 0.0) {

            abort();

            return Result.ABORTED;
        }

        activeFrames++;

        if (activeFrames < WARMUP_FRAMES) {
            return Result.MEASURING;
        }

        if (isRegressing()) {
            abortedWindows++;

            active = false;
            activeFrames = 0;

            return Result.ROLLBACK;
        }

        if (activeFrames >= MAX_ACTIVE_FRAMES) {
            successfulWindows++;

            active = false;
            activeFrames = 0;

            return Result.COMPLETED;
        }

        return Result.ACTIVE;
    }

    /**
     * Returns true when the current action window has
     * remained within the allowed regression boundary.
     */
    public boolean isHealthy() {
        if (!active) {
            return false;
        }

        if (activationBaselineMs <= 0.0) {
            return false;
        }

        double frameTimeChange =
                (
                        latestFrameTimeMs
                                - activationBaselineMs
                ) / activationBaselineMs;

        if (frameTimeChange
                > MAX_ALLOWED_REGRESSION) {
            return false;
        }

        if (activationBaselineDeviationMs > 0.0) {

            double deviationChange =
                    (
                            latestDeviationMs
                                    - activationBaselineDeviationMs
                    ) / activationBaselineDeviationMs;

            if (deviationChange
                    > MAX_ALLOWED_REGRESSION) {
                return false;
            }
        }

        return true;
    }

    private boolean isRegressing() {
        return !isHealthy();
    }

    public void abort() {
        if (active) {
            abortedWindows++;
        }

        active = false;
        activeFrames = 0;
    }

    public void setEnabled(
            boolean enabled
    ) {
        if (!enabled) {
            abort();
        }

        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isActive() {
        return active;
    }

    public int getActiveFrames() {
        return activeFrames;
    }

    public int getWarmupFrames() {
        return WARMUP_FRAMES;
    }

    public int getMaxActiveFrames() {
        return MAX_ACTIVE_FRAMES;
    }

    public double getActivationBaselineMs() {
        return activationBaselineMs;
    }

    public double getActivationBaselineDeviationMs() {
        return activationBaselineDeviationMs;
    }

    public double getLatestFrameTimeMs() {
        return latestFrameTimeMs;
    }

    public double getLatestDeviationMs() {
        return latestDeviationMs;
    }

    public long getActivations() {
        return activations;
    }

    public long getSuccessfulWindows() {
        return successfulWindows;
    }

    public long getAbortedWindows() {
        return abortedWindows;
    }

    public void reset() {
        active = false;

        activeFrames = 0;

        activationBaselineMs = 0.0;
        activationBaselineDeviationMs = 0.0;

        latestFrameTimeMs = 0.0;
        latestDeviationMs = 0.0;

        activations = 0L;
        successfulWindows = 0L;
        abortedWindows = 0L;
    }

    private double calculateDeviation(
            FrameMonitor monitor
    ) {
        double average =
                monitor.getAverageFrameTimeMs();

        if (average <= 0.0) {
            return 0.0;
        }

        var samples =
                monitor.getSamples();

        if (samples.length == 0) {
            return 0.0;
        }

        double squared = 0.0;

        for (var sample : samples) {
            double difference =
                    sample.frameTimeMs()
                            - average;

            squared +=
                    difference * difference;
        }

        return Math.sqrt(
                squared / samples.length
        );
    }

    private boolean isValid(
            double value
    ) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value);
    }

    public enum Result {
        INACTIVE,
        MEASURING,
        ACTIVE,
        COMPLETED,
        ROLLBACK,
        ABORTED
    }
          }
