package com.hyperframe.smooth;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.SpikeDetector;

/**
 * HyperFrame Smooth Controller.
 *
 * Central coordinator for the smoothing decision pipeline:
 *
 * SpikeDetector
 *      ↓
 * PerformanceAnalyzer
 *      ↓
 * SmoothController
 *      ↓
 * SmoothEngine
 *      ↓
 * SafetyGuard
 *      ↓
 * Approved optimization action
 *
 * The controller owns neither the SmoothEngine nor the
 * SafetyGuard. They are injected through the constructor,
 * so HyperFrame can keep exactly one shared instance of each.
 *
 * IMPORTANT:
 *
 * This class never modifies:
 *
 * - mouse sensitivity
 * - mouse DPI
 * - raw input
 * - keyboard behaviour
 * - FOV
 * - artificial input latency
 *
 * It also never adds artificial frame delays.
 */
public final class SmoothController {

    private final SmoothEngine smoothEngine;
    private final SafetyGuard safetyGuard;

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
     * Creates a controller using the shared HyperFrame
     * SmoothEngine and SafetyGuard instances.
     */
    public SmoothController(
            SmoothEngine smoothEngine,
            SafetyGuard safetyGuard
    ) {
        if (smoothEngine == null) {
            throw new IllegalArgumentException(
                    "smoothEngine cannot be null"
            );
        }

        if (safetyGuard == null) {
            throw new IllegalArgumentException(
                    "safetyGuard cannot be null"
            );
        }

        this.smoothEngine = smoothEngine;
        this.safetyGuard = safetyGuard;
    }

    /**
     * Evaluates the current performance situation.
     *
     * Pipeline:
     *
     * SmoothEngine → SafetyGuard
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

            lastEngineRejectReason =
                    SmoothEngine.RejectReason.ENGINE_DISABLED;

            return Decision.blockedByEngine(
                    SmoothEngine.RejectReason.ENGINE_DISABLED
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

            return Decision.blockedByEngine(
                    SmoothEngine.RejectReason.INVALID_DATA
            );
        }

        /*
         * Never start another optimization while one
         * is already active.
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
                smoothEngine.evaluate(
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

            return Decision.blockedByEngine(
                    engineDecision.rejectReason()
            );
        }

        /*
         * Second decision layer.
         *
         * SafetyGuard gets the final word.
         */
        SafetyGuard.Decision safetyDecision =
                safetyGuard.check(
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

            return Decision.blockedBySafety(
                    safetyDecision.rejectReason()
            );
        }

        /*
         * Both layers approved the action.
         *
         * Register it as active.
         *
         * The controller itself does NOT perform an
         * optimization here. A future real optimization
         * implementation will consume this approved action.
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
     * Completes the currently active action.
     *
     * This releases the corresponding SafetyGuard slot.
     */
    public void completeAction() {

        if (!isActionActive()) {
            return;
        }

        safetyGuard.completeAction();

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
     * Used as a fail-safe.
     */
    public void cancelAction() {

        if (!isActionActive()) {
            return;
        }

        safetyGuard.completeAction();

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
     * Returns the current controller state.
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
     * Returns the shared SmoothEngine instance.
     */
    public SmoothEngine getSmoothEngine() {
        return smoothEngine;
    }

    /**
     * Returns the shared SafetyGuard instance.
     */
    public SafetyGuard getSafetyGuard() {
        return safetyGuard;
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
     * Resets the controller.
     */
    public void reset() {

        if (isActionActive()) {
            safetyGuard.completeAction();
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
     * Converts a SmoothEngine decision into
     * an appropriate controller state.
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
     * Controller state.
     */
    public enum ControllerState {

        IDLE,

        WARMING_UP,

        MONITORING,

        ACTION_APPROVED,

        ACTION_ACTIVE,

        SAFETY_HOLD,

        DISABLED
    }

    /**
     * Result of a controller evaluation.
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

        public static Decision blockedByEngine(
                SmoothEngine.RejectReason reason
        ) {
            return new Decision(
                    Status.BLOCKED_BY_ENGINE,
                    SmoothEngine.OptimizationAction.NONE,
                    reason,
                    null
            );
        }

        public static Decision blockedBySafety(
                SafetyGuard.RejectReason reason
        ) {
            return new Decision(
                    Status.BLOCKED_BY_SAFETY,
                    SmoothEngine.OptimizationAction.NONE,
                    null,
                    reason
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

    /**
     * Controller decision status.
     */
    public enum Status {

        APPROVED,

        ACTIVE,

        BLOCKED_BY_ENGINE,

        BLOCKED_BY_SAFETY
    }
            }
