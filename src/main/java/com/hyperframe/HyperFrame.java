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
import com.hyperframe.smooth.SmoothEngine;
import net.fabricmc.api.ClientModInitializer;

public class HyperFrame implements ClientModInitializer {

    public static final FrameMonitor FRAME_MONITOR =
            new FrameMonitor();

    public static final FrameStatistics FRAME_STATISTICS =
            new FrameStatistics(FRAME_MONITOR);

    public static final SpikeDetector SPIKE_DETECTOR =
            new SpikeDetector();

    public static final PerformanceHistory PERFORMANCE_HISTORY =
            new PerformanceHistory();

    public static final PerformanceAnalyzer PERFORMANCE_ANALYZER =
            new PerformanceAnalyzer();

    public static final PerformanceSnapshotHistory SNAPSHOT_HISTORY =
            new PerformanceSnapshotHistory();

    public static final PerformanceTimeline PERFORMANCE_TIMELINE =
            new PerformanceTimeline();

    /**
     * Single owner of the complete LagEvent lifecycle.
     */
    public static final LagEventManager LAG_EVENT_MANAGER =
            new LagEventManager();

    /**
     * Decides whether a smoothing action is justified.
     */
    public static final SmoothEngine SMOOTH_ENGINE =
            new SmoothEngine();

    /**
     * Final safety layer before any optimization action.
     */
    public static final SafetyGuard SAFETY_GUARD =
            new SafetyGuard();

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
    }

    /**
     * Called once for every measured render frame.
     */
    public static void analyzeFrame() {

        FrameSample latestFrame =
                FRAME_MONITOR.getLatestFrame();

        if (latestFrame == null) {
            return;
        }

        /*
         * Store the latest frame in the Performance Time Machine.
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
         * Detect frame-time spikes.
         */
        SpikeDetector.SpikeResult result =
                SPIKE_DETECTOR.analyze(
                        FRAME_MONITOR
                );

        /*
         * Capture current performance statistics.
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
         * No spike = no optimization decision required.
         */
        if (!result.detected()) {

            SMOOTH_ENGINE.evaluate(
                    result,
                    PerformanceAnalyzer.AnalysisResult.none(),
                    SPIKE_DETECTOR.getBaseline(),
                    FRAME_STATISTICS.getStabilityScore()
            );

            return;
        }

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
         * First decision layer:
         * should HyperFrame even consider acting?
         */
        SmoothEngine.Decision smoothDecision =
                SMOOTH_ENGINE.evaluate(
                        result,
                        analysis,
                        baseline,
                        FRAME_STATISTICS.getStabilityScore()
                );

        /*
         * Second decision layer:
         * is the requested action safe enough?
         */
        if (smoothDecision.hasAction()) {

            SafetyGuard.Decision safetyDecision =
                    SAFETY_GUARD.check(
                            smoothDecision.action(),
                            result.frameTimeMs(),
                            baselineMs,
                            baseline.getDeviationMs(),
                            baseline.getSampleCount()
                    );

            onSmoothDecision(
                    smoothDecision,
                    safetyDecision
            );
        }

        /*
         * Start LagEvent replay if another replay
         * is not already being collected.
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
                        + format(result.frameTimeMs())
                        + "ms"
                        + " | baseline="
                        + format(baselineMs)
                        + "ms"
                        + " | confidence="
                        + analysis.confidencePercent()
                        + "%"
        );
    }

    /**
     * Handles the Smooth Engine + SafetyGuard decision chain.
     *
     * IMPORTANT:
     * No real Minecraft optimization is executed here yet.
     *
     * This is the controlled entry point for the first
     * real frame-smoothing mechanism.
     */
    private static void onSmoothDecision(
            SmoothEngine.Decision smoothDecision,
            SafetyGuard.Decision safetyDecision
    ) {

        if (safetyDecision.approved()) {

            System.out.println(
                    "[HyperFrame] "
                            + "SMOOTH ACTION APPROVED"
                            + " | action="
                            + safetyDecision.action()
            );

            return;
        }

        System.out.println(
                "[HyperFrame] "
                        + "SMOOTH ACTION BLOCKED"
                        + " | action="
                        + smoothDecision.action()
                        + " | reason="
                        + safetyDecision.rejectReason()
        );
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

    public static FrameSample getLatestFrame() {
        return PERFORMANCE_TIMELINE.getLatest();
    }

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

    public static FrameSample getWorstTimelineFrame(
            long startFrame,
            long endFrame
    ) {
        return PERFORMANCE_TIMELINE.findWorst(
                startFrame,
                endFrame
        );
    }

    public static FrameSample findTimelineFrame(
            long frameNumber
    ) {
        return PERFORMANCE_TIMELINE.findNearest(
                frameNumber
        );
    }

    public static PerformanceReplay.Replay getLatestReplay() {
        return LAG_EVENT_MANAGER.getLatestReplay();
    }

    public static boolean isReplayRecording() {
        return LAG_EVENT_MANAGER.isRecording();
    }

    public static double getReplayProgress() {
        return LAG_EVENT_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    public static int getReplayRemainingFrames() {
        return LAG_EVENT_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
    }

    public static LagEvent getLatestLagEvent() {
        return LAG_EVENT_MANAGER.getLatest();
    }

    public static int getLagEventCount() {
        return LAG_EVENT_MANAGER.getEventCount();
    }

    public static int getSevereLagEventCount() {
        return LAG_EVENT_MANAGER.getSevereCount();
    }

    public static int getMicrostutterLagEventCount() {
        return LAG_EVENT_MANAGER.getMicrostutterCount();
    }

    public static boolean isLagEventRecording() {
        return LAG_EVENT_MANAGER.isRecording();
    }

    public static double getLagEventProgress() {
        return LAG_EVENT_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    public static int getLagEventRemainingFrames() {
        return LAG_EVENT_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
    }

    public static LagEvent[] getLagEvents() {
        return LAG_EVENT_MANAGER
                .getHistory()
                .getEvents();
    }

    /**
     * Smooth Engine status.
     */
    public static boolean isSmoothEngineEnabled() {
        return SMOOTH_ENGINE.isEnabled();
    }

    public static void setSmoothEngineEnabled(
            boolean enabled
    ) {
        SMOOTH_ENGINE.setEnabled(
                enabled
        );
    }

    public static SmoothEngine.EngineState
    getSmoothEngineState() {
        return SMOOTH_ENGINE.getState();
    }

    /**
     * SafetyGuard status.
     */
    public static SafetyGuard.SafetyState
    getSafetyGuardState() {
        return SAFETY_GUARD.getState();
    }

    public static long getSmoothDecisions() {
        return SMOOTH_ENGINE.getDecisions();
    }

    public static long getAcceptedSmoothDecisions() {
        return SMOOTH_ENGINE.getAcceptedDecisions();
    }

    public static long getApprovedSafetyActions() {
        return SAFETY_GUARD.getApproved();
    }

    public static long getRejectedSafetyActions() {
        return SAFETY_GUARD.getRejected();
    }

    /**
     * Reset every HyperFrame performance subsystem.
     */
    public static void resetPerformanceData() {
        FRAME_MONITOR.reset();
        SPIKE_DETECTOR.reset();
        PERFORMANCE_HISTORY.reset();
        SNAPSHOT_HISTORY.reset();
        PERFORMANCE_TIMELINE.reset();
        LAG_EVENT_MANAGER.reset();
        SMOOTH_ENGINE.reset();
        SAFETY_GUARD.reset();
    }

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
