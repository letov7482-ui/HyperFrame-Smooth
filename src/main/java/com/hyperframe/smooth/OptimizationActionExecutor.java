package com.hyperframe.smooth;

import com.hyperframe.core.FrameMonitor;

/**
 * Executes concrete HyperFrame optimization actions.
 *
 * This class is the "hands" of HyperFrame.
 *
 * The decision system decides WHAT may be done.
 * This class is responsible for safely executing it.
 *
 * Important:
 *
 * - No artificial delays.
 * - No mouse changes.
 * - No keyboard changes.
 * - No FOV changes.
 * - No sensitivity changes.
 * - No FPS limiting.
 *
 * An action is only considered successful when the
 * adaptive controller confirms a measurable benefit.
 */
public final class OptimizationActionExecutor {

    private final AdaptiveActionController
            adaptiveController;

    private boolean enabled = true;

    private ExecutionState state =
            ExecutionState.IDLE;

    private AdaptiveActionController.Action
            activeAction =
            AdaptiveActionController.Action.NONE;

    private long executions;

    private long successfulExecutions;

    private long rolledBackExecutions;

    private long rejectedExecutions;

    public OptimizationActionExecutor(
            AdaptiveActionController adaptiveController
    ) {
        if (adaptiveController == null) {
            throw new IllegalArgumentException(
                    "adaptiveController cannot be null"
            );
        }

        this.adaptiveController =
                adaptiveController;
    }

    /**
     * Attempts to begin an optimization action.
     *
     * The action is not considered successful yet.
     * It enters a measurement phase first.
     */
    public boolean begin(
            AdaptiveActionController.Action action,
            FrameMonitor monitor,
            double baselineFrameTimeMs,
            double baselineDeviationMs
    ) {
        if (!enabled) {
            rejectedExecutions++;

            state =
                    ExecutionState.DISABLED;

            return false;
        }

        if (state != ExecutionState.IDLE) {
            rejectedExecutions++;
            return false;
        }

        if (action == null
                || action
                == AdaptiveActionController.Action.NONE) {

            rejectedExecutions++;
            return false;
        }

        if (monitor == null) {
            rejectedExecutions++;

            state =
                    ExecutionState.SAFETY_HOLD;

            return false;
        }

        /*
         * IMPORTANT:
         *
         * We currently do not modify Minecraft here.
         *
         * This method establishes the execution boundary.
         * Concrete render actions will be connected here
         * only after their safety and compatibility are verified.
         */
        if (!applyAction(action)) {
            rejectedExecutions++;

            state =
                    ExecutionState.SAFETY_HOLD;

            return false;
        }

        boolean started =
                adaptiveController.start(
                        action,
                        baselineFrameTimeMs,
                        baselineDeviationMs
                );

        if (!started) {
            rollbackAction(action);

            rejectedExecutions++;

            state =
                    ExecutionState.SAFETY_HOLD;

            return false;
        }

        activeAction = action;

        executions++;

        state =
                ExecutionState.MEASURING;

        return true;
    }

    /**
     * Updates the measurement of the active action.
     */
    public void update(
            FrameMonitor monitor
    ) {
        if (state
                != ExecutionState.MEASURING) {
            return;
        }

        if (monitor == null) {
            rollback();

            return;
        }

        adaptiveController.update(
                monitor
        );

        AdaptiveActionController.State
                adaptiveState =
                adaptiveController.getState();

        if (adaptiveState
                == AdaptiveActionController.State.KEEP) {

            state =
                    ExecutionState.ACTIVE;

            successfulExecutions++;

            return;
        }

        if (adaptiveState
                == AdaptiveActionController.State.ROLLBACK
                || adaptiveState
                == AdaptiveActionController.State.NO_EFFECT
                || adaptiveState
                == AdaptiveActionController.State.SAFETY_HOLD) {

            rollback();
        }
    }

    /**
     * Forces an evaluation of the current action.
     */
    public void evaluateNow() {
        if (state
                != ExecutionState.MEASURING) {
            return;
        }

        AdaptiveActionController.State
                result =
                adaptiveController.evaluateNow();

        if (result
                == AdaptiveActionController.State.KEEP) {

            state =
                    ExecutionState.ACTIVE;

            successfulExecutions++;

            return;
        }

        if (result
                == AdaptiveActionController.State.ROLLBACK
                || result
                == AdaptiveActionController.State.NO_EFFECT
                || result
                == AdaptiveActionController.State.SAFETY_HOLD) {

            rollback();
        }
    }

    /**
     * Rolls back the active optimization.
     */
    public void rollback() {
        if (activeAction
                == AdaptiveActionController.Action.NONE) {

            state =
                    ExecutionState.IDLE;

            return;
        }

        rollbackAction(
                activeAction
        );

        adaptiveController.abort();

        rolledBackExecutions++;

        activeAction =
                AdaptiveActionController.Action.NONE;

        state =
                ExecutionState.ROLLED_BACK;
    }

    /**
     * Keeps the current optimization active.
     */
    public void keep() {
        if (state
                != ExecutionState.ACTIVE) {
            return;
        }

        adaptiveController.complete();

        state =
                ExecutionState.IDLE;

        activeAction =
                AdaptiveActionController.Action.NONE;
    }

    /**
     * Immediately disables the current action.
     */
    public void disable() {
        rollback();

        enabled = false;

        state =
                ExecutionState.DISABLED;
    }

    public void setEnabled(
            boolean enabled
    ) {
        if (!enabled) {
            rollback();
        }

        this.enabled = enabled;

        state =
                enabled
                        ? ExecutionState.IDLE
                        : ExecutionState.DISABLED;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isActive() {
        return state
                == ExecutionState.ACTIVE;
    }

    public boolean isMeasuring() {
        return state
                == ExecutionState.MEASURING;
    }

    public ExecutionState getState() {
        return state;
    }

    public AdaptiveActionController.Action
    getActiveAction() {
        return activeAction;
    }

    public long getExecutions() {
        return executions;
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions;
    }

    public long getRolledBackExecutions() {
        return rolledBackExecutions;
    }

    public long getRejectedExecutions() {
        return rejectedExecutions;
    }

    public AdaptiveActionController
    getAdaptiveController() {
        return adaptiveController;
    }

    public void reset() {
        rollback();

        adaptiveController.reset();

        executions = 0L;
        successfulExecutions = 0L;
        rolledBackExecutions = 0L;
        rejectedExecutions = 0L;

        activeAction =
                AdaptiveActionController.Action.NONE;

        state =
                enabled
                        ? ExecutionState.IDLE
                        : ExecutionState.DISABLED;
    }

    /**
     * Applies a concrete optimization.
     *
     * This is deliberately conservative for now.
     *
     * Returning true means the action is structurally
     * supported by the executor.
     *
     * The actual Minecraft render optimization will be
     * connected in the next implementation step.
     */
    private boolean applyAction(
            AdaptiveActionController.Action action
    ) {
        return switch (action) {

            case FRAME_SMOOTHING ->
                    true;

            case NONE ->
                    false;
        };
    }

    /**
     * Reverses a concrete optimization.
     */
    private void rollbackAction(
            AdaptiveActionController.Action action
    ) {
        if (action == null) {
            return;
        }

        switch (action) {

            case FRAME_SMOOTHING -> {
                /*
                 * Concrete rollback logic will be added
                 * together with the real render action.
                 */
            }

            case NONE -> {
                // Nothing to roll back.
            }
        }
    }

    public enum ExecutionState {
        IDLE,
        MEASURING,
        ACTIVE,
        ROLLED_BACK,
        SAFETY_HOLD,
        DISABLED
    }
          }
