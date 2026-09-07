package com.hyperframe.smooth;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.SpikeDetector;

/**
 * HyperFrame Smooth Controller.
 *
 * Coordinates the complete smoothing decision pipeline:
 *
 * SpikeDetector
 *      ↓
 * SmoothEngine
 *      ↓
 * SafetyGuard
 *      ↓
 * SmoothController
 *      ↓
 * Safe optimization action
 *
 * IMPORTANT:
 *
 * This controller does not modify mouse input,
 * keyboard input, sensitivity, DPI, FOV or raw input.
 *
 * It also does not add artificial frame delays.
 *
 * Until a real optimization mechanism is implemented,
 * approved FRAME_SMOOTHING actions remain observational.
 */
public final class SmoothController {

    private boolean enabled = true;

    private ControllerState state =
            ControllerState.IDLE;

    private SmoothEngine.OptimizationAction
            activeAction =
            SmoothEngine.OptimizationAction.NONE;

    private SmoothEngine.RejectReason
            lastEngineRejectReason;

    private SafetyGuard.RejectReason
            lastSafetyRejectReason;

    private long evaluations;
    private long acceptedActions;
    private long blockedActions;
    private long completedActions;

    /**
     * Evaluates the current performance situation.
     *
     * This method performs the complete:
     *
     * SmoothEngine → SafetyGuard
     *
     * decision chain.
     */
    public Decision evaluate(
            SpikeDetector.SpikeResult spike,
            PerformanceAnalyzer.AnalysisResult analysis,
            AdaptiveBaseline baseline,
            double stabilityScore
    ) {
        evaluations++;

        if (!enabled) {
            state =
                    ControllerState.DISABLED;

            blockedActions++;

            return Decision.blocked(
                    SmoothEngine.RejectReason.ENGINE_DISABLED,
                    null
            );
        }

        if (spike == null
                || analysis == null
                || baseline == null) {

            state =
                    ControllerState.SAFETY_HOLD;

            blockedActions++;

            lastEngineRejectReason =
                    SmoothEngine.RejectReason.INVALID_DATA;

            return Decision.blocked(
                    SmoothEngine.RejectReason.INVALID_DATA,
                    null
            );
        }

        /*
         * Do not allow a new action while another
         * action is active.
         */
        if (isActionActive()) {
            state =
                    ControllerState.ACTION_ACTIVE;

            return Decision.active(
                    activeAction
            );
        }

        /*
         * First decision layer.
         */
        SmoothEngine.Decision engineDecision =
                HyperFrameSmoothEngineHolder
                        .ENGINE
                        .evaluate(
                                spike,
                                analysis,
                                baseline,
                                stabilityScore
                        );

        if (!engineDecision.hasAction()) {

            lastEngineRejectReason =
                    engineDecision.rejectReason();

            state =
                    determineStateFromEngine(
                            engineDecision
                    );

            if (engineDecision.wasRejected()) {
                blockedActions++;
            }

            return Decision.engineRejected(
                    engineDecision.rejectReason()
            );
        }

        /*
         * Second decision layer.
         *
         * SafetyGuard gets the final word.
         */
        SafetyGuard.Decision safetyDecision =
                HyperFrameSmoothEngineHolder
                        .GUARD
                        .check(
                                engineDecision.action(),
                                spike.frameTimeMs(),
                                baseline.getBaselineMs(),
                                baseline.getDeviationMs(),
                                baseline.getSampleCount()
                        );

        if (!safetyDecision.approved()) {

            lastSafetyRejectReason =
                    safetyDecision.rejectReason();

            state =
                    ControllerState.SAFETY_HOLD;

            blockedActions++;

            return Decision.safetyRejected(
                    safetyDecision.rejectReason()
            );
        }

        /*
         * Action is approved.
         *
         * We only register the action here.
         * No artificial delay or input manipulation
         * is performed.
         */
        activeAction =
                safetyDecision.action();

        state =
                ControllerState.ACTION_APPROVED;

        acceptedActions++;

        return Decision.approved(
                activeAction
        );
    }

    /**
     * Marks the currently active optimization action
     * as completed.
     *
     * This releases the SafetyGuard action slot.
     */
    public void completeAction() {

        if (!isActionActive()) {
            return;
        }

        HyperFrameSmoothEngineHolder
                .GUARD
                .completeAction();

        completedActions++;

        activeAction =
                SmoothEngine.OptimizationAction.NONE;

        state =
                enabled
                        ? ControllerState.IDLE
                        : ControllerState.DISABLED;
    }

    /**
     * Cancels the currently active action.
     *
     * Used as a fail-safe when the controller decides
     * that an action should no longer continue.
     */
    public void cancelAction() {

        if (!isActionActive()) {
            return;
        }

        HyperFrameSmoothEngineHolder
                .GUARD
                .completeAction();

        activeAction =
                SmoothEngine.OptimizationAction.NONE;

        state =
                enabled
                        ? ControllerState.IDLE
                        : ControllerState.DISABLED;
    }

    /**
     * Returns whether an optimization action
     * is currently active.
     */
    public boolean isActionActive() {
        return activeAction
                != SmoothEngine.OptimizationAction.NONE;
    }

    /**
     * Returns the currently active action.
     */
    public SmoothEngine.OptimizationAction
    getActiveAction() {
        return activeAction;
    }

    /**
     * Returns current controller state.
     */
    public ControllerState getState() {
        return state;
    }

    /**
     * Returns the number of evaluations.
     */
    public long getEvaluations() {
        return evaluations;
    }

    /**
     * Returns how many actions passed both
     * decision layers.
     */
    public long getAcceptedActions() {
        return acceptedActions;
    }

    /**
     * Returns how many actions were blocked.
     */
    public long getBlockedActions() {
        return blockedActions;
    }

    /**
     * Returns how many actions were completed.
     */
    public long getCompletedActions() {
        return completedActions;
    }

    /**
     * Returns the last SmoothEngine rejection reason.
     */
    public SmoothEngine.RejectReason
    getLastEngineRejectReason() {
        return lastEngineRejectReason;
    }

    /**
     * Returns the last SafetyGuard rejection reason.
     */
    public SafetyGuard.RejectReason
    getLastSafetyRejectReason() {
        return lastSafetyRejectReason;
    }

    /**
     * Enables or disables the controller.
     */
    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;

        if (!enabled) {
            cancelAction();

            state =
                    ControllerState.DISABLED;

            return;
        }

        if (state
                == ControllerState.DISABLED) {
            state =
                    ControllerState.IDLE;
        }
    }

    /**
     * Returns whether the controller is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Resets controller state and counters.
     */
    public void reset() {

        if (isActionActive()) {
            HyperFrameSmoothEngineHolder
                    .GUARD
                    .completeAction();
        }

        activeAction =
                SmoothEngine.OptimizationAction.NONE;

        state =
                enabled
                        ? ControllerState.IDLE
                        : ControllerState.DISABLED;

        lastEngineRejectReason = null;
        lastSafetyRejectReason = null;

        evaluations = 0L;
        acceptedActions = 0L;
        blockedActions = 0L;
        completedActions = 0L;
    }

    /**
     * Converts SmoothEngine state into a controller state.
     */
    private ControllerState determineStateFromEngine(
            SmoothEngine.Decision decision
    ) {
        if (decision == null) {
            return ControllerState.SAFETY_HOLD;
        }

        if (!decision.wasRejected()) {
            return ControllerState.IDLE;
        }

        if (decision.rejectReason()
                == SmoothEngine.RejectReason.BASELINE_NOT_READY) {

            return ControllerState.WARMING_UP;
        }

        if (decision.rejectReason()
                == SmoothEngine.RejectReason.ENGINE_DISABLED) {

            return ControllerState.DISABLED;
        }

        if (decision.rejectReason()
                == SmoothEngine.RejectReason.TOO_UNSTABLE
                || decision.rejectReason()
                == SmoothEngine.RejectReason.TOO_MANY_CONSECUTIVE_SPIKES) {

            return ControllerState.SAFETY_HOLD;
        }

        return ControllerState.MONITORING;
    }

    /**
     * Controller states.
     */
    public enum ControllerState {

        /**
         * No action is currently required.
         */
        IDLE,

        /**
         * Not enough baseline data yet.
         */
        WARMING_UP,

        /**
         * HyperFrame is monitoring the situation.
         */
        MONITORING,

        /**
         * An action passed both decision layers.
         */
        ACTION_APPROVED,

        /**
         * An approved action is currently registered.
         */
        ACTION_ACTIVE,

        /**
         * Safety system blocked the action.
         */
        SAFETY_HOLD,

        /**
         * Controller is disabled.
         */
        DISABLED
    }

    /**
     * Result returned after a controller evaluation.
     */
    public record Decision(
            Status status,
            SmoothEngine.OptimizationAction action,
            SmoothEngine.RejectReason
                    engineRejectReason,
            SafetyGuard.RejectReason
                    safetyRejectReason
    ) {

        public static Decision approved(
                SmoothEngine.OptimizationAction action
        ) {
            return new Decision(
                    Status.APPROVED,
                    action,
                    null,
                    null
            );
        }

        public static Decision active(
                SmoothEngine.OptimizationAction action
        ) {
            return new Decision(
                    Status.ACTIVE,
                    action,
                    null,
                    null
            );
        }

        public static Decision engineRejected(
                SmoothEngine.RejectReason reason
        ) {
            return new Decision(
                    Status.BLOCKED_BY_ENGINE,
                    SmoothEngine.OptimizationAction.NONE,
                    reason,
                    null
            );
        }

        public static Decision safetyRejected(
                SafetyGuard.RejectReason reason
        ) {
            return new Decision(
                    Status.BLOCKED_BY_SAFETY,
                    SmoothEngine.OptimizationAction.NONE,
                    null,
                    reason
            );
        }

        public static Decision blocked(
                SmoothEngine.RejectReason reason,
                SafetyGuard.RejectReason safetyReason
        ) {
            return new Decision(
                    Status.BLOCKED_BY_ENGINE,
                    SmoothEngine.OptimizationAction.NONE,
                    reason,
                    safetyReason
            );
        }

        public boolean approved() {
            return status == Status.APPROVED;
        }

        public boolean active() {
            return status == Status.ACTIVE;
        }

        public boolean blocked() {
            return status == Status.BLOCKED_BY_ENGINE
                    || status == Status.BLOCKED_BY_SAFETY;
        }

        public boolean hasAction() {
            return action
                    != SmoothEngine.OptimizationAction.NONE;
        }
    }

    public enum Status {
        APPROVED,
        ACTIVE,
        BLOCKED_BY_ENGINE,
        BLOCKED_BY_SAFETY
    }

    /**
     * Temporary dependency holder.
     *
     * The actual HyperFrame integration will replace this
     * with the single shared instances from HyperFrame.java.
     *
     * Keeping the holder here makes the controller compile
     * independently before that integration step.
     */
    private static final class HyperFrameSmoothEngineHolder {

        private static final SmoothEngine ENGINE =
                new SmoothEngine();

        private static final SafetyGuard GUARD =
                new SafetyGuard();

        private HyperFrameSmoothEngineHolder() {
        }
    }
          }
