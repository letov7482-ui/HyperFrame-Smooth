package com.hyperframe;

import com.hyperframe.core.FrameMonitor;
import com.hyperframe.core.FrameStatistics;
import com.hyperframe.core.PerformanceHistory;
import com.hyperframe.core.PerformanceEvent;
import com.hyperframe.core.SpikeDetector;
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

    @Override
    public void onInitializeClient() {
        System.out.println(
                "[HyperFrame] Frame Stability Core initialized."
        );
    }

    public static void analyzeFrame() {
        SpikeDetector.SpikeResult result =
                SPIKE_DETECTOR.analyze(FRAME_MONITOR);

        if (!result.detected()) {
            return;
        }

        double baseline =
                SPIKE_DETECTOR
                        .getBaseline()
                        .getBaselineMs();

        PerformanceEvent event =
                new PerformanceEvent(
                        result.frameNumber(),
                        System.nanoTime(),
                        result.frameTimeMs(),
                        baseline,
                        result.relativeCost(),
                        result.type()
                );

        PERFORMANCE_HISTORY.record(event);
    }
}
