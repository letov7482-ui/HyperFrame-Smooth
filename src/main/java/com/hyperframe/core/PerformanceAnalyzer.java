package com.hyperframe.core;

public final class PerformanceAnalyzer {

    private static final int RECENT_EVENT_WINDOW = 10;

    public AnalysisResult analyze(
            PerformanceHistory history,
            AdaptiveBaseline baseline
    ) {
        PerformanceEvent latest =
                history.getLatest();

        if (latest == null) {
            return AnalysisResult.none();
        }

        PerformanceEvent[] events =
                history.getEvents();

        int recentEvents =
                countRecentEvents(events);

        if (latest.isSevere()) {
            if (recentEvents >= 3) {
                return new AnalysisResult(
                        Pattern.REPEATED_SEVERE_SPIKES,
                        "Repeated severe frame-time spikes",
                        confidence(recentEvents, 5)
                );
            }

            return new AnalysisResult(
                    Pattern.ISOLATED_SEVERE_SPIKE,
                    "Isolated severe frame-time spike",
                    0.90
            );
        }

        if (latest.isMicrostutter()) {
            return new AnalysisResult(
                    Pattern.MICROSTUTTER_BURST,
                    "Repeated frame-time spikes",
                    confidence(recentEvents, 4)
            );
        }

        if (recentEvents >= 3) {
            return new AnalysisResult(
                    Pattern.REPEATED_SPIKES,
                    "Repeated frame-time spikes",
                    confidence(recentEvents, 5)
            );
        }

        double deviation =
                baseline.getDeviationMs();

        double relativeCost =
                latest.relativeCost();

        if (relativeCost >= 2.0
                && deviation > 2.0) {
            return new AnalysisResult(
                    Pattern.UNSTABLE_FRAME_TIME,
                    "Unstable frame-time pattern",
                    0.75
            );
        }

        return new AnalysisResult(
                Pattern.ISOLATED_SPIKE,
                "Isolated frame-time spike",
                0.70
        );
    }

    private int countRecentEvents(
            PerformanceEvent[] events
    ) {
        int count = 0;

        int start =
                Math.max(
                        0,
                        events.length - RECENT_EVENT_WINDOW
                );

        for (int i = start; i < events.length; i++) {
            if (events[i].isSpike()) {
                count++;
            }
        }

        return count;
    }

    private double confidence(
            int events,
            int divisor
    ) {
        return Math.min(
                0.98,
                0.60 + ((double) events / divisor)
        );
    }

    public enum Pattern {
        NONE,
        ISOLATED_SPIKE,
        REPEATED_SPIKES,
        MICROSTUTTER_BURST,
        ISOLATED_SEVERE_SPIKE,
        REPEATED_SEVERE_SPIKES,
        UNSTABLE_FRAME_TIME
    }

    public record AnalysisResult(
            Pattern pattern,
            String description,
            double confidence
    ) {

        public static AnalysisResult none() {
            return new AnalysisResult(
                    Pattern.NONE,
                    "No performance event",
                    0.0
            );
        }

        public boolean detected() {
            return pattern != Pattern.NONE;
        }

        public int confidencePercent() {
            return (int) Math.round(
                    confidence * 100.0
            );
        }
    }
}
