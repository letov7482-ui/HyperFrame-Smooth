package com.hyperframe;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.FrameMonitor;
import com.hyperframe.core.FrameSample;
import com.hyperframe.core.FrameStatistics;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.PerformanceEvent;
import com.hyperframe.core.PerformanceHistory;
import com.hyperframe.core.PerformanceReplay;
import com.hyperframe.core.PerformanceReplayManager;
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
     * This is the foundation of the
     * HyperFrame Performance Time Machine.
     */
    public static final PerformanceTimeline PERFORMANCE_TIMELINE =
            new PerformanceTimeline();

    /**
     * Manages performance replay capture.
     *
     * When a spike is detected, the manager waits for
     * additional frames and then completes the replay.
     */
    public static final PerformanceReplayManager REPLAY_MANAGER =
            new PerformanceReplayManager();

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
                "[HyperFrame] Performance Replay initialized."
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
         * Continue an already active replay.
         *
         * If enough frames have appeared after the
         * original spike, the replay becomes complete.
         */
        PerformanceReplay.Replay completedReplay =
                REPLAY_MANAGER.update(
                        PERFORMANCE_TIMELINE
                );

        if (completedReplay != null) {
            onReplayCompleted(completedReplay);
        }

        /*
         * Analyze the current frame for spikes.
         */
        SpikeDetector.SpikeResult result =
                SPIKE_DETECTOR.analyze(
                        FRAME_MONITOR
                );

        /*
         * Store a complete performance snapshot.
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
         * Start a replay for the first spike
         * that is not already being recorded.
         *
         * The manager will wait for the future frames.
         */
        if (!REPLAY_MANAGER.isRecording()) {
            REPLAY_MANAGER.start(
                    event,
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
     * Called when a replay has collected enough
     * frames after the performance event.
     */
    private static void onReplayCompleted(
            PerformanceReplay.Replay replay
    ) {
        FrameSample center =
                replay.getCenterFrame();

        FrameSample worst =
                replay.getWorstFrame();

        System.out.println(
                "[HyperFrame] "
                        + "REPLAY COMPLETE"
                        + " | eventFrame="
                        + replay.event().frameNumber()
                        + " | frames="
                        + replay.getFrameCount()
                        + " | type="
                        + replay.type()
        );

        if (center != null) {
            System.out.println(
                    "[HyperFrame] "
                            + "Replay center"
                            + " | frametime="
                            + format(center.frameTimeMs())
                            + "ms"
            );
        }

        if (worst != null) {
            System.out.println(
                    "[HyperFrame] "
                            + "Replay worst"
                            + " | frame="
                            + worst.frameNumber()
                            + " | frametime="
                            + format(worst.frameTimeMs())
                            + "ms"
            );
        }
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
        return REPLAY_MANAGER.getLatestReplay();
    }

    /**
     * Returns whether HyperFrame is currently
     * collecting a replay.
     */
    public static boolean isReplayRecording() {
        return REPLAY_MANAGER.isRecording();
    }

    /**
     * Returns replay collection progress
     * from 0.0 to 1.0.
     */
    public static double getReplayProgress() {
        return REPLAY_MANAGER.getProgress(
                PERFORMANCE_TIMELINE
        );
    }

    /**
     * Returns the number of frames still needed
     * to complete the current replay.
     */
    public static int getReplayRemainingFrames() {
        return REPLAY_MANAGER.getRemainingFrames(
                PERFORMANCE_TIMELINE
        );
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
        REPLAY_MANAGER.reset();
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
