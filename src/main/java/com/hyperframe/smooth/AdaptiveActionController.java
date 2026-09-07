package com.hyperframe.smooth;

import com.hyperframe.core.FrameMonitor;

/**
 * Adaptive controller for real HyperFrame optimization actions.
 *
 * The controller follows a simple closed-loop principle:
 *
 * APPROVE
 *    ↓
 * APPLY
 *    ↓
 * MEASURE
 *    ↓
 * KEEP / ROLLBACK / DISABLE
 *
 * It never introduces artificial frame delays.
 *
 * The controller itself does not change Minecraft settings.
 * Concrete optimization actions will be connected later.
 */
public final class AdaptiveActionController {

    private static final int MIN_MEASUREMENT_FRAMES = 30;

    private static final int MAX_MEASUREMENT_FRAMES = 120;

    private static final double MIN_IMPROVEMENT_RATIO = 0.05;

    private static final double MAX_REGRESSION_RATIO = 0.05;

    private static final double MAX_SAFE_FRAME_TIME_MS = 1000.0;

    private State state = State.IDLE;

    private Action activeAction =
            Action.NONE;

    private double baselineFrameTimeMs;

    private double baselineDeviationMs;

    private double measuredFrameTimeMs;

    private double measuredDeviationMs;

    private int measurementFrames;

    private long actionsStarted;

    private long actionsKept;

    private long actionsRolledBack;

    private long actionsRejected;

    /**
     * Starts an adaptive measurement cycle.
     *
     * The actual optimization is intentionally not executed here.
     * A concrete action will be connected in the next layer.
     */
    public boolean start(
            Action action,
            double baselineFrameTimeMs,
            double baselineDeviationMs
    ) {
        if (state != State.IDLE) {
            actionsRejected++;
            return false;
        }

        if (action == null
                || action == Action.NONE) {
            actionsRejected++;
            return false;
        }

        if (!isValidMetric(baselineFrameTimeMs)
                || !isValidMetric(baselineDeviationMs)
                || baselineFrameTimeMs <= 0.0
                || baselineDeviationMs < 0.0) {

            actionsRejected++;
            state = State.SAFETY_HOLD;
            return false;
        }

        this.activeAction = action;

        this.baselineFrameTimeMs =
                baselineFrameTimeMs;

        this.baselineDeviationMs =
                baselineDeviationMs;

        this.measuredFrameTimeMs = 0.0;
        this.measuredDeviationMs = 0.0;

        this.measurementFrames = 0;

        this.state = State.MEASURING;

        actionsStarted++;

        return true;
    }

    /**
     * Adds the latest performance measurement.
     */
    public void update(
            FrameMonitor monitor
    ) {
        if (state != State.MEASURING
                || monitor == null) {
            return;
        }

        double frameTime =
                monitor.getAverageFrameTimeMs();

        if (!isValidMetric(frameTime)
                || frameTime <= 0.0
                || frameTime > MAX_SAFE_FRAME_TIME_MS) {

            state = State.SAFETY_HOLD;
            return;
        }

        measuredFrameTimeMs = frameTime;

        measuredDeviationMs =
                calculateDeviation(monitor);

        measurementFrames++;

        if (measurementFrames
                >= MAX_MEASUREMENT_FRAMES) {

            state = evaluateResult();
        }
    }

    /**
     * Forces evaluation once enough frames have been measured.
     */
    public State evaluateNow() {
        if (state != State.MEASURING) {
            return state;
        }

        if (measurementFrames
                < MIN_MEASUREMENT_FRAMES) {
            return state;
        }

        state = evaluateResult();

        return state;
    }

    private State evaluateResult() {

        if (!isValidMetric(measuredFrameTimeMs)
                || !isValidMetric(measuredDeviationMs)) {

            actionsRolledBack++;
            return State.ROLLBACK;
        }

        double frameTimeChange =
                relativeChange(
                        baselineFrameTimeMs,
                        measuredFrameTimeMs
                );

        double deviationChange =
                relativeChange(
                        baselineDeviationMs,
                        measuredDeviationMs
                );

        /*
         * We care primarily about frame-time consistency.
         *
         * A small increase in average frame time can still be
         * acceptable if variance improves substantially.
         */
        boolean frameTimeImproved =
                frameTimeChange
                        <= -MIN_IMPROVEMENT_RATIO;

        boolean varianceImproved =
                baselineDeviationMs > 0.0
                        && deviationChange
                        <= -MIN_IMPROVEMENT_RATIO;

        boolean frameTimeRegressed =
                frameTimeChange
                        >= MAX_REGRESSION_RATIO;

        boolean varianceRegressed =
                baselineDeviationMs > 0.0
                        && deviationChange
                        >= MAX_REGRESSION_RATIO;

        if (frameTimeRegressed
                || varianceRegressed) {

            actionsRolledBack++;

            return State.ROLLBACK;
        }

        if (frameTimeImproved
                || varianceImproved) {

            actionsKept++;

            return State.KEEP;
        }

        actionsRolledBack++;

        return State.NO_EFFECT;
    }

    /**
     * Completes the current cycle and returns to idle.
     */
    public void complete() {
        activeAction = Action.NONE;

        baselineFrameTimeMs = 0.0;
        baselineDeviationMs = 0.0;

        measuredFrameTimeMs = 0.0;
        measuredDeviationMs = 0.0;

        measurementFrames = 0;

        state = State.IDLE;
    }

    /**
     * Immediately aborts the current optimization cycle.
     */
    public void abort() {
        activeAction = Action.NONE;

        measurementFrames = 0;

        state = State.IDLE;
    }

    public boolean isActive() {
        return state == State.MEASURING
                || state == State.KEEP;
    }

    public boolean isMeasuring() {
        return state == State.MEASURING;
    }

    public boolean shouldKeep() {
        return state == State.KEEP;
    }

    public boolean shouldRollback() {
        return state == State.ROLLBACK
                || state == State.NO_EFFECT
                || state == State.SAFETY_HOLD;
    }

    public State getState() {
        return state;
    }

    public Action getActiveAction() {
        return activeAction;
    }

    public double getBaselineFrameTimeMs() {
        return baselineFrameTimeMs;
    }

    public double getBaselineDeviationMs() {
        return baselineDeviationMs;
    }

    public double getMeasuredFrameTimeMs() {
        return measuredFrameTimeMs;
    }

    public double getMeasuredDeviationMs() {
        return measuredDeviationMs;
    }

    public int getMeasurementFrames() {
        return measurementFrames;
    }

    public int getMinimumMeasurementFrames() {
        return MIN_MEASUREMENT_FRAMES;
    }

    public int getMaximumMeasurementFrames() {
        return MAX_MEASUREMENT_FRAMES;
    }

    public long getActionsStarted() {
        return actionsStarted;
    }

    public long getActionsKept() {
        return actionsKept;
    }

    public long getActionsRolledBack() {
        return actionsRolledBack;
    }

    public long getActionsRejected() {
        return actionsRejected;
    }

    public double getImprovementRatio() {
        if (baselineFrameTimeMs <= 0.0
                || measuredFrameTimeMs <= 0.0) {
            return 0.0;
        }

        return (
                baselineFrameTimeMs
                        - measuredFrameTimeMs
        ) / baselineFrameTimeMs;
    }

    public double getVarianceImprovementRatio() {
        if (baselineDeviationMs <= 0.0
                || measuredDeviationMs < 0.0) {
            return 0.0;
        }

        return (
                baselineDeviationMs
                        - measuredDeviationMs
        ) / baselineDeviationMs;
    }

    public void reset() {
        activeAction = Action.NONE;

        baselineFrameTimeMs = 0.0;
        baselineDeviationMs = 0.0;

        measuredFrameTimeMs = 0.0;
        measuredDeviationMs = 0.0;

        measurementFrames = 0;

        actionsStarted = 0L;
        actionsKept = 0L;
        actionsRolledBack = 0L;
        actionsRejected = 0L;

        state = State.IDLE;
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

    private double relativeChange(
            double baseline,
            double current
    ) {
        if (baseline <= 0.0) {
            return 0.0;
        }

        return (
                current - baseline
        ) / baseline;
    }

    private boolean isValidMetric(
            double value
    ) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value);
    }

    public enum State {
        IDLE,
        MEASURING,
        KEEP,
        ROLLBACK,
        NO_EFFECT,
        SAFETY_HOLD
    }

    /**
     * Optimization actions that can be connected
     * to actual Minecraft systems.
     */
    public enum Action {
        NONE,

        /**
         * Reserved for the first real frame-smoothing
         * implementation.
         */
        FRAME_SMOOTHING
    }
  }
