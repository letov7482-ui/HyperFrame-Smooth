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
import net.fabricmc.api.ClientModInitializer;

public class HyperFrame implements ClientModInitializer {

    /**
     * Collects real render-frame timing data.
     */
    public static final FrameMonitor FRAME_MONITOR =
            new FrameMonitor();

    /**
     * Calculates FPS, lows, percentiles,
     * variance and stability metrics.
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
     * Analyzes patterns inside performance events.
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
     * Foundation of the HyperFrame Performance Time Machine.
     */
    public static final PerformanceTimeline PERFORMANCE_TIMELINE =
            new PerformanceTimeline();

    /**
     * Owns the complete lag-event lifecycle.
     *
     * This is now the single owner of performance replays.
     */
    public static final LagEventManager LAG_EVENT_MANAGER =
            new LagEventManager();

    /**
     * Spike threshold used for general statistics.
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
                "[HyperFrame] Replay ownership unified."
        );
    }

    /**
     * Called once for every real render frame.
     */
    public static void analyzeFrame() {

        /*
         * Get the frame that FrameMonitor has just measured.
         */
        FrameSample latestFrame =
                FRAME_MONITOR.getLatestFrame();

        /*
         * There is no measurable frame yet during
         * the very first render call.
         */
        if (latestFrame == null) {
            return;
        }

        /*
         * Store every measured frame in the Time Machine.
         */
        PERFORMANCE_TIMELINE.record(latestFrame);

        /*
         * Continue collecting the currently active
         * LagEvent replay.
         *
         * If enough future frames have appeared,
         * LagEventManager finalizes the event.
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
         * Analyze the current frame for spikes.
         */
        SpikeDetector.SpikeResult result =
                SPIKE_DETECTOR.analyze(
                        FRAME_MONITOR
                );

        /*
         * Store a performance snapshot.
         */
        PerformanceSnapshot snapshot =
                PerformanceSnapshot.capture(
                        FRAME_MONITOR,
                        FRAME_STATISTICS,
                        SPIKE_THRESHOLD_MS
                );

        SNAPSHOT_HISTORY.record(snapshot);

        /*
         * Nothing more to do when the current frame
         * is considered normal.
         */
        if (!result.detected()) {
            return;
        }

        AdaptiveBaseline baseline =
                SPIKE_DETECTOR.getBaseline();

        double baselineMs =
                baseline.getBaselineMs();

        /*
         * Create a detailed event describing
         * the detected performance problem.
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

        PERFORMANCE_HISTORY.record(event);

        /*
         * Analyze the accumulated event pattern.
         */
        PerformanceAnalyzer.AnalysisResult analysis =
                PERFORMANCE_ANALYZER.analyze(
                        PERFORMANCE_HISTORY,
                        baseline
                );

        /*
         * Start the complete LagEvent lifecycle.
         *
         * LagEventManager owns the replay and waits
         * for the required future frames.
         */
        if (!LAG_EVENT_MANAGER.isRecording()) {
            LAG_EVENT_MANAGER.start(
                    event,
                    analysis,
                    PERFORMANCE_TIMELINE
            );
        }

        /*
         * Console diagnostic.
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
                        + format(event.frameTimeMs())
                        + "ms"
                        + " | baseline="
                        + format(event.baselineMs())
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
                        + format(event.excessFrameTimeMs())
                        + "ms"
        );
    }

    /**
     * Returns the most recent frame.
     */
    public static FrameSample getLatestFrame() {
        return PERFORMANCE_TIMELINE.getLatest();
    }

    /**
     * Returns frames around a performance event.
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
     * Returns the frame nearest to a requested
     * frame number.
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
        return LAG_EVENT_MANAGER
                .getLatestReplay();
    }

    /**
     * Returns whether a replay is currently
     * being collected.
     */
    public static boolean isReplayRecording() {
        return LAG_EVENT_MANAGER.isRecording();
    }

    /**
     * Returns replay collection progress
     * from 0.0 to 1.0.
     */
    public static double getReplayProgress() {
        return LAG_EVENT_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns how many frames are still needed
     * to complete the current replay.
     */
    public static int getReplayRemainingFrames() {
        return LAG_EVENT_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns the latest complete LagEvent.
     */
    public static LagEvent getLatestLagEvent() {
        return LAG_EVENT_MANAGER.getLatest();
    }

    /**
     * Returns the total number of complete LagEvents.
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
     * Returns whether a complete LagEvent replay
     * is currently being collected.
     */
    public static boolean isLagEventRecording() {
        return LAG_EVENT_MANAGER.isRecording();
    }

    /**
     * Returns LagEvent replay progress
     * from 0.0 to 1.0.
     */
    public static double getLagEventProgress() {
        return LAG_EVENT_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns the number of frames still needed
     * for the current LagEvent replay.
     */
    public static int getLagEventRemainingFrames() {
        return LAG_EVENT_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns the complete LagEvent history.
     */
    public static LagEvent[] getLagEvents() {
        return LAG_EVENT_MANAGER
                .getHistory()
                .getEvents();
    }

    /**
     * Clears all collected performance data.
     */
    public static void resetPerformanceData() {
        FRAME_MONITOR.reset();
        SPIKE_DETECTOR.reset();
        PERFORMANCE_HISTORY.reset();
        SNAPSHOT_HISTORY.reset();
        PERFORMANCE_TIMELINE.reset();
        LAG_EVENT_MANAGER.reset();
    }

    /**
     * Small formatting helper for console diagnostics.
     */
    private static String format(double value) {
        return String.format(
                java.util.Locale.ROOT,
                "%.2f",
                value
        );
    }
}
