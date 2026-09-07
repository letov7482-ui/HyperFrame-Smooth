package com.hyperframe.smooth;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.PerformanceEvent;
import com.hyperframe.core.SpikeDetector;

/**
 * HyperFrame Smooth Engine.
 *
 * The Smooth Engine decides whether HyperFrame should
 * attempt a safe performance action.
 *
 * IMPORTANT:
 *
 * This class does not directly modify Minecraft rendering,
 * input, FPS limits or game state yet.
 *
 * Its first responsibility is to make a conservative,
 * measurable decision about whether intervention is justified.
 *
 * Philosophy:
 *
 * Stable game
 *      ↓
 * Do nothing
 *
 * Repeated instability
 *      ↓
 * Analyze
 *
 * Safe optimization available
 *      ↓
 * Allow intervention
 *
 * Unsafe / unknown situation
 *      ↓
 * Do nothing
 */
public final class SmoothEngine {

    private static final int MIN_BASELINE_SAMPLES = 30;

    private static final double MIN_RELATIVE_SPIKE = 2.5;

    private static final double MIN_STABILITY_SCORE = 70.0;

    private static final int MAX_CONSECUTIVE_SPIKES = 8;

    private boolean enabled = true;

    private EngineState state =
            EngineState.IDLE;

    private OptimizationAction lastAction =
            OptimizationAction.NONE;

    private long decisions;

    private long acceptedDecisions;

    private long rejectedDecisions;

    /**
     * Evaluates the current performance situation.
     *
     * No Minecraft state is modified here.
     */
    public Decision evaluate(
            SpikeDetector.SpikeResult spike,
            PerformanceAnalyzer.AnalysisResult analysis,
            AdaptiveBaseline baseline,
            double stabilityScore
    ) {
        decisions++;

        if (!enabled) {
            state = EngineState.DISABLED;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.ENGINE_DISABLED
            );
        }

        if (spike == null
                || analysis == null
                || baseline == null) {

            state = EngineState.SAFETY_HOLD;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.INVALID_DATA
            );
        }

        /*
         * No spike means there is currently
         * no reason for intervention.
         */
        if (!spike.detected()) {
            state = EngineState.IDLE;

            lastAction =
                    OptimizationAction.NONE;

            return Decision.none();
        }

        /*
         * Do not attempt optimization until
         * the adaptive baseline has enough data.
         */
        if (!baseline.isReady()
                || baseline.getSampleCount()
                < MIN_BASELINE_SAMPLES) {

            state = EngineState.WARMING_UP;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.BASELINE_NOT_READY
            );
        }

        /*
         * An extreme relative cost is interesting,
         * but by itself it does not justify blindly
         * changing the game.
         */
        if (spike.relativeCost()
                < MIN_RELATIVE_SPIKE) {

            state = EngineState.MONITORING;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.SPIKE_TOO_SMALL
            );
        }

        /*
         * Very unstable data requires more caution.
         *
         * We don't want the optimizer fighting the game
         * while the performance situation is chaotic.
         */
        if (stabilityScore > 0.0
                && stabilityScore < MIN_STABILITY_SCORE) {

            state = EngineState.SAFETY_HOLD;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.TOO_UNSTABLE
            );
        }

        /*
         * Repeated spikes are a stronger signal than
         * one isolated event.
         */
        if (spike.consecutiveSpikes()
                > MAX_CONSECUTIVE_SPIKES) {

            state = EngineState.SAFETY_HOLD;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.TOO_MANY_CONSECUTIVE_SPIKES
            );
        }

        /*
         * Only patterns that we currently understand
         * are allowed to reach the optimization layer.
         */
        if (!isActionablePattern(
                analysis.pattern()
        )) {
            state = EngineState.MONITORING;

            rejectedDecisions++;

            return Decision.rejected(
                    RejectReason.UNKNOWN_PATTERN
            );
        }

        /*
         * At this stage we have evidence that
         * intervention may be useful.
         *
         * The actual optimization action is deliberately
         * separated from this decision.
         */
        state = EngineState.READY;

        lastAction =
                OptimizationAction.FRAME_SMOOTHING;

        acceptedDecisions++;

        return Decision.accepted(
                OptimizationAction.FRAME_SMOOTHING
        );
    }

    /**
     * Determines whether an analyzed pattern is
     * currently interesting enough for the Smooth Engine.
     */
    private boolean isActionablePattern(
            PerformanceAnalyzer.Pattern pattern
    ) {
        if (pattern == null) {
            return false;
        }

        return switch (pattern) {

            case REPEATED_SPIKES ->
                    true;

            case MICROSTUTTER_BURST ->
                    true;

            case REPEATED_SEVERE_SPIKES ->
                    true;

            case UNSTABLE_FRAME_TIME ->
                    true;

            /*
             * A single isolated spike can be caused by
             * almost anything and should not immediately
             * trigger optimization.
             */
            case ISOLATED_SPIKE ->
                    false;

            case ISOLATED_SEVERE_SPIKE ->
                    false;

            case NONE ->
                    false;
        };
    }

    /**
     * Enables or disables the Smooth Engine.
     */
    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;

        if (!enabled) {
            state = EngineState.DISABLED;
            lastAction =
                    OptimizationAction.NONE;
        } else if (state
                == EngineState.DISABLED) {
            state = EngineState.IDLE;
        }
    }

    /**
     * Returns whether the engine is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the current engine state.
     */
    public EngineState getState() {
        return state;
    }

    /**
     * Returns the last selected action.
     */
    public OptimizationAction getLastAction() {
        return lastAction;
    }

    /**
     * Returns how many decisions were evaluated.
     */
    public long getDecisions() {
        return decisions;
    }

    /**
     * Returns how many decisions were accepted.
     */
    public long getAcceptedDecisions() {
        return acceptedDecisions;
    }

    /**
     * Returns how many decisions were rejected.
     */
    public long getRejectedDecisions() {
        return rejectedDecisions;
    }

    /**
     * Resets engine state and statistics.
     */
    public void reset() {
        state = enabled
                ? EngineState.IDLE
                : EngineState.DISABLED;

        lastAction =
                OptimizationAction.NONE;

        decisions = 0L;
        acceptedDecisions = 0L;
        rejectedDecisions = 0L;
    }

    public enum EngineState {

        /**
         * Nothing needs to be done.
         */
        IDLE,

        /**
         * Not enough data has been collected yet.
         */
        WARMING_UP,

        /**
         * Performance is being monitored.
         */
        MONITORING,

        /**
         * A safe optimization may be considered.
         */
        READY,

        /**
         * The engine deliberately refuses
         * to intervene for safety reasons.
         */
        SAFETY_HOLD,

        /**
         * Engine is disabled.
         */
        DISABLED
    }

    public enum OptimizationAction {

        /**
         * No optimization.
         */
        NONE,

        /**
         * Placeholder for the first real
         * frame-smoothing mechanism.
         */
        FRAME_SMOOTHING
    }

    public enum RejectReason {

        ENGINE_DISABLED,

        INVALID_DATA,

        BASELINE_NOT_READY,

        SPIKE_TOO_SMALL,

        TOO_UNSTABLE,

        TOO_MANY_CONSECUTIVE_SPIKES,

        UNKNOWN_PATTERN
    }

    /**
     * Result of a Smooth Engine decision.
     */
    public record Decision(
            boolean accepted,
            OptimizationAction action,
            RejectReason rejectReason
    ) {

        public static Decision accepted(
                OptimizationAction action
        ) {
            return new Decision(
                    true,
                    action,
                    null
            );
        }

        public static Decision rejected(
                RejectReason reason
        ) {
            return new Decision(
                    false,
                    OptimizationAction.NONE,
                    reason
            );
        }

        public static Decision none() {
            return new Decision(
                    false,
                    OptimizationAction.NONE,
                    null
            );
        }

        public boolean hasAction() {
            return accepted
                    && action
                    != OptimizationAction.NONE;
        }

        public boolean wasRejected() {
            return !accepted
                    && rejectReason != null;
        }
    }
        }
