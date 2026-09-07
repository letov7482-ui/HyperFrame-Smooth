package com.hyperframe;

import com.hyperframe.core.AdaptiveBaseline;
import com.hyperframe.core.FrameMonitor;
import com.hyperframe.core.FrameStatistics;
import com.hyperframe.core.PerformanceAnalyzer;
import com.hyperframe.core.PerformanceEvent;
import com.hyperframe.core.PerformanceHistory;
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

    @Override
    public void onInitializeClient() {
        System.out.println(
                "[HyperFrame] Frame Stability Core initialized."
        );
    }

    /**
     * Called once for every real render frame.
     */
    public static void analyzeFrame() {

        SpikeDetector.SpikeResult result =
                SPIKE_DETECTOR.analyze(FRAME_MONITOR);

        if (!result.detected()) {
            return;
        }

        AdaptiveBaseline baseline =
                SPIKE_DETECTOR.getBaseline();

        double baselineMs =
                baseline.getBaselineMs();

        PerformanceEvent event =
                new PerformanceEvent(
                        result.frameNumber(),
                        System.nanoTime(),
                        result.frameTimeMs(),
                        baselineMs,
                        result.relativeCost(),
                        result.type()
                );

        PERFORMANCE_HISTORY.record(event);

        PerformanceAnalyzer.AnalysisResult analysis =
                PERFORMANCE_ANALYZER.analyze(
                        PERFORMANCE_HISTORY,
                        baseline
                );

        System.out.println(
                "[HyperFrame] "
                        + analysis.pattern()
                        + " | "
                        + analysis.description()
                        + " | confidence="
                        + analysis.confidencePercent()
                        + "%"
        );
    }
}
