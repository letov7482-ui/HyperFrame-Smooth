package com.hyperframe;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.FrameMonitor;
import com.hyperframe.core.FrameSample;
import com.hyperframe.core.FrameStatistics;
import com.hyperframe.core.LagEvent;
import com.hyperframe.core.LagEventManager;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.PerformanceEvent;
import com.hyperframe.core.PerformanceHistory;
import com.hyperframe.core.PerformanceReplay;
import com.hyperframe.core.PerformanceSnapshot;
import com.hyperframe.core.PerformanceSnapshotHistory;
import com.hyperframe.core.PerformanceTimeline;
import com.hyperframe.core.SpikeDetector;
import com.hyperframe.smooth.SafetyGuard;
import com.hyperframe.smooth.SmoothController;
import com.hyperframe.smooth.SmoothEngine;
import net.fabricmc.api.ClientModInitializer;

public class HyperFrame implements ClientModInitializer {

    /**
     * Collects real render-frame timing data.
     */
    public static final FrameMonitor FRAME_MONITOR =
            new FrameMonitor();

    /**
     * Calculates FPS, frame-time lows,
     * percentiles, variance and stability.
     */
    public static final FrameStatistics FRAME_STATISTICS =
            new FrameStatistics(FRAME_MONITOR);

    /**
     * Detects frame-time spikes and microstutter.
     */
    public static final SpikeDetector SPIKE_DETECTOR =
            new SpikeDetector();

    /**
     * Stores detected performance events.
     */
    public static final PerformanceHistory PERFORMANCE_HISTORY =
            new PerformanceHistory();

    /**
     * Analyzes performance patterns.
     */
    public static final PerformanceAnalyzer PERFORMANCE_ANALYZER =
            new PerformanceAnalyzer();

    /**
     * Stores recent performance snapshots.
     */
    public static final PerformanceSnapshotHistory SNAPSHOT_HISTORY =
            new PerformanceSnapshotHistory();

    /**
     * Stores recent raw frame samples.
     *
     * Foundation of the Performance Time Machine.
     */
    public static final PerformanceTimeline PERFORMANCE_TIMELINE =
            new PerformanceTimeline();

    /**
     * Owns the complete LagEvent lifecycle.
     */
    public static final LagEventManager LAG_EVENT_MANAGER =
            new LagEventManager();

    /**
     * Decides whether frame smoothing is justified.
     */
    public static final SmoothEngine SMOOTH_ENGINE =
            new SmoothEngine();

    /**
     * Final safety layer before an optimization action.
     */
    public static final SafetyGuard SAFETY_GUARD =
            new SafetyGuard();

    /**
     * Central coordinator between SmoothEngine
     * and SafetyGuard.
     *
     * Uses the exact same shared instances above.
     */
    public static final SmoothController SMOOTH_CONTROLLER =
            new SmoothController(
                    SMOOTH_ENGINE,
                    SAFETY_GUARD
            );

    /**
     * General spike threshold used by statistics.
     */
    private static final double SPIKE_THRESHOLD_MS = 25.0;

    @Override
    public void onInitializeClient() {

        System.out.println(
                "[HyperFrame] Frame Stability Core initialized."
        );

        System.out.println(
                "[HyperFrame] Performance Time Machine initialized."
        );

        System.out.println(
                "[HyperFrame] Lag Event System initialized."
        );

        System.out.println(
                "[HyperFrame] Smooth Engine initialized."
        );

        System.out.println(
                "[HyperFrame] Safety Guard initialized."
        );

        System.out.println(
                "[HyperFrame] Smooth Controller initialized."
        );

        System.out.println(
                "[HyperFrame] Smooth pipeline ready."
        );
    }

    /**
     * Called once for every measured render frame.
     */
    public static void analyzeFrame() {

        /*
         * Get the latest frame measured by FrameMonitor.
         */
        FrameSample latestFrame =
                FRAME_MONITOR.getLatestFrame();

        /*
         * The first render call does not have a previous
         * frame to compare against.
         */
        if (latestFrame == null) {
            return;
        }

        /*
         * Store the frame in the Performance Time Machine.
         */
        PERFORMANCE_TIMELINE.record(
                latestFrame
        );

        /*
         * Continue an active LagEvent replay.
         */
        LagEvent completedLagEvent =
                LAG_EVENT_MANAGER.update(
                        PERFORMANCE_TIMELINE
                );

        if (completedLagEvent != null) {
            onLagEventCompleted(
                    completedLagEvent
            );
        }

        /*
         * Analyze the latest frame for spikes.
         */
        SpikeDetector.SpikeResult result =
                SPIKE_DETECTOR.analyze(
                        FRAME_MONITOR
                );

        /*
         * Capture the current performance snapshot.
         */
        PerformanceSnapshot snapshot =
                PerformanceSnapshot.capture(
                        FRAME_MONITOR,
                        FRAME_STATISTICS,
                        SPIKE_THRESHOLD_MS
                );

        SNAPSHOT_HISTORY.record(
                snapshot
        );

        /*
         * Normal frame:
         *
         * SmoothController still receives the information,
         * but SmoothEngine will normally return no action.
         */
        if (!result.detected()) {

            SMOOTH_CONTROLLER.evaluate(
                    result,
                    PerformanceAnalyzer.AnalysisResult.none(),
                    SPIKE_DETECTOR.getBaseline(),
                    FRAME_STATISTICS.getStabilityScore()
            );

            return;
        }

        /*
         * Current adaptive baseline.
         */
        AdaptiveBaseline baseline =
                SPIKE_DETECTOR.getBaseline();

        double baselineMs =
                baseline.getBaselineMs();

        /*
         * Create a detailed performance event.
         */
        PerformanceEvent event =
                new PerformanceEvent(
                        result.frameNumber(),
                        latestFrame.timestampNanos(),
                        result.frameTimeMs(),
                        baselineMs,
                        result.relativeCost(),
                        result.type()
                );

        PERFORMANCE_HISTORY.record(
                event
        );

        /*
         * Analyze the detected pattern.
         */
        PerformanceAnalyzer.AnalysisResult analysis =
                PERFORMANCE_ANALYZER.analyze(
                        PERFORMANCE_HISTORY,
                        baseline
                );

        /*
         * Complete decision pipeline:
         *
         * SmoothEngine
         *       ↓
         * SafetyGuard
         *
         * Both are coordinated by SmoothController.
         */
        SmoothController.Decision smoothDecision =
                SMOOTH_CONTROLLER.evaluate(
                        result,
                        analysis,
                        baseline,
                        FRAME_STATISTICS.getStabilityScore()
                );

        /*
         * Handle the resulting decision.
         */
        onSmoothDecision(
                smoothDecision
        );

        /*
         * Start a LagEvent replay if another replay
         * is not currently being collected.
         */
        if (!LAG_EVENT_MANAGER.isRecording()) {

            LAG_EVENT_MANAGER.start(
                    event,
                    analysis,
                    PERFORMANCE_TIMELINE
            );
        }

        /*
         * Diagnostic output.
         */
        System.out.println(
                "[HyperFrame] "
                        + analysis.pattern()
                        + " | "
                        + analysis.description()
                        + " | frame="
                        + result.frameNumber()
                        + " | frametime="
                        + format(
                        result.frameTimeMs()
                )
                        + "ms"
                        + " | baseline="
                        + format(
                        baselineMs
                )
                        + "ms"
                        + " | confidence="
                        + analysis.confidencePercent()
                        + "%"
        );
    }

    /**
     * Handles the result of the complete
     * SmoothController decision pipeline.
     */
    private static void onSmoothDecision(
            SmoothController.Decision decision
    ) {

        if (decision == null) {
            return;
        }

        if (decision.approved()) {

            System.out.println(
                    "[HyperFrame] "
                            + "SMOOTH ACTION APPROVED"
                            + " | action="
                            + decision.action()
            );

            /*
             * IMPORTANT:
             *
             * The action is approved, but no real
             * frame-smoothing operation is executed yet.
             *
             * This is the controlled entry point for
             * the first real optimization mechanism.
             */
            return;
        }

        if (decision.active()) {

            System.out.println(
                    "[HyperFrame] "
                            + "SMOOTH ACTION ACTIVE"
                            + " | action="
                            + decision.action()
            );

            return;
        }

        if (decision.blocked()) {

            if (decision.status()
                    == SmoothController.Status.BLOCKED_BY_SAFETY) {

                System.out.println(
                        "[HyperFrame] "
                                + "SMOOTH ACTION BLOCKED"
                                + " | layer=SAFETY"
                                + " | reason="
                                + decision.safetyRejectReason()
                );

            } else {

                System.out.println(
                        "[HyperFrame] "
                                + "SMOOTH ACTION BLOCKED"
                                + " | layer=ENGINE"
                                + " | reason="
                                + decision.engineRejectReason()
                );
            }
        }
    }

    /**
     * Called when a complete LagEvent has been created.
     */
    private static void onLagEventCompleted(
            LagEvent event
    ) {

        System.out.println(
                "[HyperFrame] "
                        + "LAG EVENT #"
                        + event.id()
                        + " COMPLETE"
        );

        System.out.println(
                "[HyperFrame] "
                        + "LagEvent"
                        + " | frame="
                        + event.frameNumber()
                        + " | frametime="
                        + format(
                        event.frameTimeMs()
                )
                        + "ms"
                        + " | baseline="
                        + format(
                        event.baselineMs()
                )
                        + "ms"
        );

        System.out.println(
                "[HyperFrame] "
                        + "Analysis"
                        + " | type="
                        + event.type()
                        + " | pattern="
                        + event.pattern()
                        + " | confidence="
                        + event.confidencePercent()
                        + "%"
        );

        System.out.println(
                "[HyperFrame] "
                        + "Replay"
                        + " | frames="
                        + event.replayFrameCount()
        );

        System.out.println(
                "[HyperFrame] "
                        + "Excess"
                        + " | "
                        + format(
                        event.excessFrameTimeMs()
                )
                        + "ms"
        );
    }

    /**
     * Returns the latest frame from the Time Machine.
     */
    public static FrameSample getLatestFrame() {
        return PERFORMANCE_TIMELINE.getLatest();
    }

    /**
     * Returns frames around a specified frame.
     */
    public static FrameSample[] getTimelineAround(
            long frameNumber,
            int before,
            int after
    ) {
        return PERFORMANCE_TIMELINE.getFramesAround(
                frameNumber,
                before,
                after
        );
    }

    /**
     * Returns the worst frame inside a frame range.
     */
    public static FrameSample getWorstTimelineFrame(
            long startFrame,
            long endFrame
    ) {
        return PERFORMANCE_TIMELINE.findWorst(
                startFrame,
                endFrame
        );
    }

    /**
     * Finds the frame nearest to a requested frame number.
     */
    public static FrameSample findTimelineFrame(
            long frameNumber
    ) {
        return PERFORMANCE_TIMELINE.findNearest(
                frameNumber
        );
    }

    /**
     * Returns the latest completed replay.
     */
    public static PerformanceReplay.Replay getLatestReplay() {
        return LAG_EVENT_MANAGER.getLatestReplay();
    }

    /**
     * Returns whether a replay is being collected.
     */
    public static boolean isReplayRecording() {
        return LAG_EVENT_MANAGER.isRecording();
    }

    /**
     * Returns replay progress from 0.0 to 1.0.
     */
    public static double getReplayProgress() {
        return LAG_EVENT_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns remaining replay frames.
     */
    public static int getReplayRemainingFrames() {
        return LAG_EVENT_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns the latest completed LagEvent.
     */
    public static LagEvent getLatestLagEvent() {
        return LAG_EVENT_MANAGER.getLatest();
    }

    /**
     * Returns the number of LagEvents.
     */
    public static int getLagEventCount() {
        return LAG_EVENT_MANAGER.getEventCount();
    }

    /**
     * Returns the number of severe LagEvents.
     */
    public static int getSevereLagEventCount() {
        return LAG_EVENT_MANAGER.getSevereCount();
    }

    /**
     * Returns the number of microstutter LagEvents.
     */
    public static int getMicrostutterLagEventCount() {
        return LAG_EVENT_MANAGER.getMicrostutterCount();
    }

    /**
     * Returns whether a LagEvent replay is active.
     */
    public static boolean isLagEventRecording() {
        return LAG_EVENT_MANAGER.isRecording();
    }

    /**
     * Returns LagEvent replay progress.
     */
    public static double getLagEventProgress() {
        return LAG_EVENT_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns remaining LagEvent replay frames.
     */
    public static int getLagEventRemainingFrames() {
        return LAG_EVENT_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns all recorded LagEvents.
     */
    public static LagEvent[] getLagEvents() {
        return LAG_EVENT_MANAGER
                .getHistory()
                .getEvents();
    }

    /*
     * =========================================================
     * SMOOTH ENGINE
     * =========================================================
     */

    /**
     * Returns whether the Smooth Engine is enabled.
     */
    public static boolean isSmoothEngineEnabled() {
        return SMOOTH_ENGINE.isEnabled();
    }

    /**
     * Enables or disables the Smooth Engine.
     */
    public static void setSmoothEngineEnabled(
            boolean enabled
    ) {
        SMOOTH_ENGINE.setEnabled(
                enabled
        );
    }

    /**
     * Returns the current Smooth Engine state.
     */
    public static SmoothEngine.EngineState
    getSmoothEngineState() {
        return SMOOTH_ENGINE.getState();
    }

    /**
     * Returns the number of Smooth Engine decisions.
     */
    public static long getSmoothDecisions() {
        return SMOOTH_ENGINE.getDecisions();
    }

    /**
     * Returns accepted Smooth Engine decisions.
     */
    public static long getAcceptedSmoothDecisions() {
        return SMOOTH_ENGINE.getAcceptedDecisions();
    }

    /**
     * Returns rejected Smooth Engine decisions.
     */
    public static long getRejectedSmoothDecisions() {
        return SMOOTH_ENGINE.getRejectedDecisions();
    }

    /*
     * =========================================================
     * SAFETY GUARD
     * =========================================================
     */

    /**
     * Returns the current SafetyGuard state.
     */
    public static SafetyGuard.SafetyState
    getSafetyGuardState() {
        return SAFETY_GUARD.getState();
    }

    /**
     * Returns how many safety checks were performed.
     */
    public static long getSafetyChecks() {
        return SAFETY_GUARD.getChecks();
    }

    /**
     * Returns how many actions passed SafetyGuard.
     */
    public static long getApprovedSafetyActions() {
        return SAFETY_GUARD.getApproved();
    }

    /**
     * Returns how many actions were rejected by SafetyGuard.
     */
    public static long getRejectedSafetyActions() {
        return SAFETY_GUARD.getRejected();
    }

    /*
     * =========================================================
     * SMOOTH CONTROLLER
     * =========================================================
     */

    /**
     * Returns whether SmoothController is enabled.
     */
    public static boolean isSmoothControllerEnabled() {
        return SMOOTH_CONTROLLER.isEnabled();
    }

    /**
     * Enables or disables the SmoothController.
     */
    public static void setSmoothControllerEnabled(
            boolean enabled
    ) {
        SMOOTH_CONTROLLER.setEnabled(
                enabled
        );
    }

    /**
     * Returns the current controller state.
     */
    public static SmoothController.ControllerState
    getSmoothControllerState() {
        return SMOOTH_CONTROLLER.getState();
    }

    /**
     * Returns whether an optimization action
     * is currently active.
     */
    public static boolean isSmoothActionActive() {
        return SMOOTH_CONTROLLER.isActionActive();
    }

    /**
     * Returns the currently active optimization action.
     */
    public static SmoothEngine.OptimizationAction
    getActiveSmoothAction() {
        return SMOOTH_CONTROLLER.getActiveAction();
    }

    /**
     * Returns the number of controller evaluations.
     */
    public static long getSmoothControllerEvaluations() {
        return SMOOTH_CONTROLLER.getEvaluations();
    }

    /**
     * Returns the number of approved actions.
     */
    public static long getAcceptedSmoothActions() {
        return SMOOTH_CONTROLLER.getAcceptedActions();
    }

    /**
     * Returns the number of blocked actions.
     */
    public static long getBlockedSmoothActions() {
        return SMOOTH_CONTROLLER.getBlockedActions();
    }

    /**
     * Returns the number of completed actions.
     */
    public static long getCompletedSmoothActions() {
        return SMOOTH_CONTROLLER.getCompletedActions();
    }

    /**
     * Completes the currently active smoothing action.
     */
    public static void completeSmoothAction() {
        SMOOTH_CONTROLLER.completeAction();
    }

    /**
     * Cancels the currently active smoothing action.
     */
    public static void cancelSmoothAction() {
        SMOOTH_CONTROLLER.cancelAction();
            }

    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    /**
     * Resets every HyperFrame performance subsystem.
     */
    public static void resetPerformanceData() {

        FRAME_MONITOR.reset();

        SPIKE_DETECTOR.reset();

        PERFORMANCE_HISTORY.reset();

        SNAPSHOT_HISTORY.reset();

        PERFORMANCE_TIMELINE.reset();

        LAG_EVENT_MANAGER.reset();

        SMOOTH_CONTROLLER.reset();

        SMOOTH_ENGINE.reset();

        SAFETY_GUARD.reset();
    }

    /**
     * Formats a metric for diagnostic output.
     */
    private static String format(
            double value
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "%.2f",
                value
        );
    }
}
