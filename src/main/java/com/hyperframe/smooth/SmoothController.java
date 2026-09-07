package com.hyperframe.smooth;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.FrameMonitor;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.SpikeDetector;

/**
 * HyperFrame Smooth Controller.
 *
 * Coordinates:
 *
 * SpikeDetector
 *      ↓
 * PerformanceAnalyzer
 *      ↓
 * SmoothEngine
 *      ↓
 * SafetyGuard
 *      ↓
 * AdaptiveActionController
 *
 * The controller does not directly modify Minecraft input,
 * sensitivity, FOV or add artificial delays.
 */
public final class SmoothController {

    private final SmoothEngine smoothEngine;
    private final SafetyGuard safetyGuard;
    private final AdaptiveActionController adaptiveController;

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

        this.adaptiveController =
                new AdaptiveActionController();
    }

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

        if (isActionActive()) {
            state =
                    ControllerState.ACTION_ACTIVE;

            return Decision.active(
                    activeAction
            );
        }

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
         * The action is approved by both layers.
         *
         * The AdaptiveActionController will become the
         * measurement/rollback layer once a concrete
         * Minecraft optimization action is connected.
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
     * Starts adaptive measurement for an approved action.
     *
     * The concrete action itself is intentionally not
     * executed here yet.
     */
    public boolean startAdaptiveMeasurement(
            double baselineFrameTimeMs,
            double baselineDeviationMs
    ) {
        if (!isActionActive()) {
            return false;
        }

        AdaptiveActionController.Action action =
                toAdaptiveAction(
                        activeAction
                );

        boolean started =
                adaptiveController.start(
                        action,
                        baselineFrameTimeMs,
                        baselineDeviationMs
                );

        if (started) {
            state =
                    ControllerState.ACTION_MEASURING;
        }

        return started;
    }

    /**
     * Updates the adaptive measurement cycle.
     */
    public void updateAdaptiveMeasurement(
            FrameMonitor monitor
    ) {
        if (!adaptiveController.isMeasuring()) {
            return;
        }

        adaptiveController.update(
                monitor
        );
    }

    /**
     * Evaluates the current adaptive measurement.
     */
    public AdaptiveActionController.State
    evaluateAdaptiveMeasurement() {

        return adaptiveController.evaluateNow();
    }

    /**
     * Returns true when the adaptive system decided
     * that the action produced a measurable benefit.
     */
    public boolean shouldKeepAdaptiveAction() {
        return adaptiveController.shouldKeep();
    }

    /**
     * Returns true when the adaptive system decided
     * that the action should be removed.
     */
    public boolean shouldRollbackAdaptiveAction() {
        return adaptiveController.shouldRollback();
    }

    /**
     * Completes the current action.
     */
    public void completeAction() {

        if (!isActionActive()) {
            return;
        }

        safetyGuard.completeAction();

        adaptiveController.complete();

        completedActions++;

        activeAction =
                SmoothEngine.OptimizationAction.NONE;

        state =
                enabled
                        ? ControllerState.IDLE
                        : ControllerState.DISABLED;
    }

    /**
     * Cancels the current action.
     */
    public void cancelAction() {

        if (!isActionActive()) {
            return;
        }

        safetyGuard.completeAction();

        adaptiveController.abort();

        activeAction =
                SmoothEngine.OptimizationAction.NONE;

        state =
                enabled
                        ? ControllerState.IDLE
                        : ControllerState.DISABLED;
    }

    private AdaptiveActionController.Action
    toAdaptiveAction(
            SmoothEngine.OptimizationAction action
    ) {
        if (action
                == SmoothEngine.OptimizationAction.FRAME_SMOOTHING) {

            return AdaptiveActionController.Action
                    .FRAME_SMOOTHING;
        }

        return AdaptiveActionController.Action.NONE;
    }

    public boolean isActionActive() {
        return activeAction
                != SmoothEngine.OptimizationAction.NONE;
    }

    public SmoothEngine.OptimizationAction
    getActiveAction() {
        return activeAction;
    }

    public AdaptiveActionController
    getAdaptiveController() {
        return adaptiveController;
    }

    public ControllerState getState() {
        return state;
    }

    public long getEvaluations() {
        return evaluations;
    }

    public long getAcceptedActions() {
        return acceptedActions;
    }

    public long getBlockedActions() {
        return blockedActions;
    }

    public long getCompletedActions() {
        return completedActions;
    }

    public SmoothEngine.RejectReason
    getLastEngineRejectReason() {
        return lastEngineRejectReason;
    }

    public SafetyGuard.RejectReason
    getLastSafetyRejectReason() {
        return lastSafetyRejectReason;
    }

    public SmoothEngine getSmoothEngine() {
        return smoothEngine;
    }

    public SafetyGuard getSafetyGuard() {
        return safetyGuard;
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {

        if (isActionActive()) {
            safetyGuard.completeAction();
        }

        adaptiveController.reset();

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

    public enum ControllerState {
        IDLE,
        WARMING_UP,
        MONITORING,
        ACTION_APPROVED,
        ACTION_MEASURING,
        ACTION_ACTIVE,
        SAFETY_HOLD,
        DISABLED
    }

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

    public enum Status {
        APPROVED,
        ACTIVE,
        BLOCKED_BY_ENGINE,
        BLOCKED_BY_SAFETY
    }
            }
