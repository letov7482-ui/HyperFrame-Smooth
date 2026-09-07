package com.hyperframe.smooth;

import com.hyperframe.smooth.SmoothEngine.OptimizationAction;

/**
 * Safety layer for HyperFrame optimizations.
 *
 * The SafetyGuard is intentionally conservative.
 *
 * Its job is not to optimize Minecraft directly.
 * Its job is to decide whether an optimization is safe
 * enough to be executed.
 *
 * Safety principles:
 *
 * - Never modify mouse sensitivity.
 * - Never modify keyboard behaviour.
 * - Never add artificial input latency.
 * - Never modify FOV.
 * - Never modify DPI or raw mouse settings.
 * - Never apply an unknown optimization.
 * - Never allow an optimization when the game state
 *   is considered unsafe.
 * - When uncertain, reject the action.
 */
public final class SafetyGuard {

    /**
     * Minimum amount of measured frames required
     * before an optimization can be considered.
     */
    private static final int MIN_FRAME_SAMPLES = 30;

    /**
     * Maximum frame-time deviation considered acceptable
     * before the guard enters a safety hold.
     */
    private static final double MAX_DEVIATION_MS = 50.0;

    /**
     * Maximum allowed optimization attempts before
     * the guard becomes conservative.
     *
     * This protects against an optimization loop repeatedly
     * trying to change the same game state.
     */
    private static final int MAX_RECENT_ACTIONS = 8;

    /**
     * Whether the guard itself is enabled.
     */
    private boolean enabled = true;

    /**
     * Number of safety checks performed.
     */
    private long checks;

    /**
     * Number of approved actions.
     */
    private long approved;

    /**
     * Number of rejected actions.
     */
    private long rejected;

    /**
     * Number of actions recently approved.
     */
    private int recentActions;

    /**
     * Current safety state.
     */
    private SafetyState state =
            SafetyState.SAFE;

    /**
     * Checks whether an optimization action is safe.
     *
     * This method does not modify Minecraft.
     */
    public Decision check(
            OptimizationAction action,
            double frameTimeMs,
            double baselineMs,
            double deviationMs,
            int sampleCount
    ) {
        checks++;

        /*
         * Disabled guard means that optimization
         * is not allowed.
         */
        if (!enabled) {
            state =
                    SafetyState.DISABLED;

            rejected++;

            return Decision.rejected(
                    RejectReason.GUARD_DISABLED
            );
        }

        /*
         * Basic numerical validation.
         */
        if (!isFinite(frameTimeMs)
                || !isFinite(baselineMs)
                || !isFinite(deviationMs)
                || frameTimeMs <= 0.0
                || baselineMs <= 0.0
                || deviationMs < 0.0
                || sampleCount < 0) {

            state =
                    SafetyState.BLOCKED;

            rejected++;

            return Decision.rejected(
                    RejectReason.INVALID_METRICS
            );
        }

        /*
         * There must be enough historical data.
         *
         * We never want to react to an almost empty
         * performance history.
         */
        if (sampleCount < MIN_FRAME_SAMPLES) {
            state =
                    SafetyState.WARMING_UP;

            rejected++;

            return Decision.rejected(
                    RejectReason.NOT_ENOUGH_DATA
            );
        }

        /*
         * There is currently only one known action.
         *
         * Unknown actions are always rejected.
         */
        if (action == null
                || action
                == OptimizationAction.NONE) {

            state =
                    SafetyState.SAFE;

            rejected++;

            return Decision.rejected(
                    RejectReason.NO_ACTION
            );
        }

        if (!isSupportedAction(action)) {
            state =
                    SafetyState.BLOCKED;

            rejected++;

            return Decision.rejected(
                    RejectReason.UNSUPPORTED_ACTION
            );
        }

        /*
         * If frame-time variance becomes extreme,
         * changing the game state could make the situation
         * harder to diagnose.
         */
        if (deviationMs > MAX_DEVIATION_MS) {
            state =
                    SafetyState.CONSERVATIVE;

            rejected++;

            return Decision.rejected(
                    RejectReason.EXTREME_VARIANCE
            );
        }

        /*
         * Prevent an optimization loop from repeatedly
         * requesting actions.
         */
        if (recentActions >= MAX_RECENT_ACTIONS) {
            state =
                    SafetyState.CONSERVATIVE;

            rejected++;

            return Decision.rejected(
                    RejectReason.ACTION_RATE_LIMIT
            );
        }

        /*
         * The action passed all current safety checks.
         */
        state =
                SafetyState.APPROVED;

        approved++;
        recentActions++;

        return Decision.approved(
                action
        );
    }

    /**
     * Checks whether the action is currently known
     * and supported by the SafetyGuard.
     */
    private boolean isSupportedAction(
            OptimizationAction action
    ) {
        return switch (action) {

            case FRAME_SMOOTHING ->
                    true;

            case NONE ->
                    false;
        };
    }

    /**
     * Marks one optimization cycle as completed.
     *
     * This allows the rate limiter to slowly recover
     * instead of staying blocked forever.
     */
    public void completeAction() {
        if (recentActions > 0) {
            recentActions--;
        }

        if (state == SafetyState.APPROVED) {
            state =
                    SafetyState.SAFE;
        }
    }

    /**
     * Completely clears the recent-action limiter.
     */
    public void clearActionLimit() {
        recentActions = 0;

        if (enabled) {
            state =
                    SafetyState.SAFE;
        }
    }

    /**
     * Enables or disables the SafetyGuard.
     */
    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;

        if (!enabled) {
            state =
                    SafetyState.DISABLED;
        } else {
            state =
                    SafetyState.SAFE;
        }
    }

    /**
     * Returns whether the guard is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the current safety state.
     */
    public SafetyState getState() {
        return state;
    }

    /**
     * Returns the number of safety checks.
     */
    public long getChecks() {
        return checks;
    }

    /**
     * Returns the number of approved actions.
     */
    public long getApproved() {
        return approved;
    }

    /**
     * Returns the number of rejected actions.
     */
    public long getRejected() {
        return rejected;
    }

    /**
     * Returns the number of actions currently counted
     * by the rate limiter.
     */
    public int getRecentActions() {
        return recentActions;
    }

    /**
     * Resets all statistics and state.
     */
    public void reset() {
        checks = 0L;
        approved = 0L;
        rejected = 0L;

        recentActions = 0;

        state = enabled
                ? SafetyState.SAFE
                : SafetyState.DISABLED;
    }

    /**
     * Validates a floating-point metric.
     */
    private boolean isFinite(
            double value
    ) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value);
    }

    /**
     * Current state of the SafetyGuard.
     */
    public enum SafetyState {

        /**
         * Everything is normal.
         */
        SAFE,

        /**
         * Not enough performance data yet.
         */
        WARMING_UP,

        /**
         * An optimization has been approved.
         */
        APPROVED,

        /**
         * Guard is being extra conservative.
         */
        CONSERVATIVE,

        /**
         * Requested action is blocked.
         */
        BLOCKED,

        /**
         * Guard is disabled.
         */
        DISABLED
    }

    /**
     * Reason why an optimization was rejected.
     */
    public enum RejectReason {

        GUARD_DISABLED,

        INVALID_METRICS,

        NOT_ENOUGH_DATA,

        NO_ACTION,

        UNSUPPORTED_ACTION,

        EXTREME_VARIANCE,

        ACTION_RATE_LIMIT
    }

    /**
     * Result of a safety check.
     */
    public record Decision(
            boolean approved,
            OptimizationAction action,
            RejectReason rejectReason
    ) {

        /**
         * Creates an approved decision.
         */
        public static Decision approved(
                OptimizationAction action
        ) {
            return new Decision(
                    true,
                    action,
                    null
            );
        }

        /**
         * Creates a rejected decision.
         */
        public static Decision rejected(
                RejectReason reason
        ) {
            return new Decision(
                    false,
                    OptimizationAction.NONE,
                    reason
            );
        }

        /**
         * Returns whether the decision contains
         * an actual optimization action.
         */
        public boolean hasAction() {
            return approved
                    && action
                    != OptimizationAction.NONE;
        }

        /**
         * Returns whether the request was rejected.
         */
        public boolean wasRejected() {
            return !approved
                    && rejectReason != null;
        }
    }
}
