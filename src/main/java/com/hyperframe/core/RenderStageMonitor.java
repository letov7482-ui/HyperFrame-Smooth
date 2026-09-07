package com.hyperframe.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Measures the time spent in important rendering stages.
 *
 * This class is diagnostic only.
 *
 * It does NOT:
 * - change rendering;
 * - change input;
 * - add delays;
 * - limit FPS;
 * - modify sensitivity;
 * - modify FOV.
 *
 * The collected data will later allow HyperFrame to determine
 * where frame-time spikes are actually coming from.
 */
public final class RenderStageMonitor {

    private final Map<RenderStage, StageMeasurement> measurements =
            new EnumMap<>(RenderStage.class);

    private RenderStage activeStage;
    private long stageStartNanos;

    public RenderStageMonitor() {
        reset();
    }

    /**
     * Starts measuring a rendering stage.
     *
     * If another stage is currently active, it is
     * automatically completed first.
     */
    public void begin(RenderStage stage) {
        if (stage == null) {
            return;
        }

        if (activeStage != null) {
            end();
        }

        activeStage = stage;
        stageStartNanos = System.nanoTime();
    }

    /**
     * Completes the currently active stage.
     */
    public void end() {
        if (activeStage == null) {
            return;
        }

        long now = System.nanoTime();

        double durationMs =
                (now - stageStartNanos)
                        / 1_000_000.0;

        record(
                activeStage,
                durationMs
        );

        activeStage = null;
        stageStartNanos = 0L;
    }

    /**
     * Records a completed stage measurement.
     */
    public void record(
            RenderStage stage,
            double durationMs
    ) {
        if (stage == null
                || !isValid(durationMs)
                || durationMs < 0.0) {
            return;
        }

        StageMeasurement measurement =
                measurements.get(stage);

        if (measurement == null) {
            measurement =
                    new StageMeasurement();

            measurements.put(
                    stage,
                    measurement
            );
        }

        measurement.record(durationMs);
    }

    public double getLatestMs(
            RenderStage stage
    ) {
        StageMeasurement measurement =
                measurements.get(stage);

        if (measurement == null) {
            return 0.0;
        }

        return measurement.latestMs;
    }

    public double getAverageMs(
            RenderStage stage
    ) {
        StageMeasurement measurement =
                measurements.get(stage);

        if (measurement == null
                || measurement.samples == 0) {
            return 0.0;
        }

        return measurement.totalMs
                / measurement.samples;
    }

    public double getWorstMs(
            RenderStage stage
    ) {
        StageMeasurement measurement =
                measurements.get(stage);

        if (measurement == null) {
            return 0.0;
        }

        return measurement.worstMs;
    }

    public long getSampleCount(
            RenderStage stage
    ) {
        StageMeasurement measurement =
                measurements.get(stage);

        if (measurement == null) {
            return 0L;
        }

        return measurement.samples;
    }

    /**
     * Returns the currently active stage.
     */
    public RenderStage getActiveStage() {
        return activeStage;
    }

    public boolean isMeasuring() {
        return activeStage != null;
    }

    /**
     * Returns the total measured time of all stages.
     *
     * This is useful for diagnostics, but should not be
     * interpreted as the complete frame-time because stages
     * may not cover every operation performed by Minecraft.
     */
    public double getTotalMeasuredMs() {
        double total = 0.0;

        for (StageMeasurement measurement :
                measurements.values()) {

            total += measurement.totalMs;
        }

        return total;
    }

    /**
     * Returns the stage with the largest average cost.
     */
    public RenderStage getMostExpensiveStage() {
        RenderStage result = null;
        double worstAverage = 0.0;

        for (RenderStage stage :
                RenderStage.values()) {

            double average =
                    getAverageMs(stage);

            if (average > worstAverage) {
                worstAverage = average;
                result = stage;
            }
        }

        return result;
    }

    /**
     * Returns true when a stage has enough measurements
     * to be considered useful for analysis.
     */
    public boolean isReady(
            RenderStage stage
    ) {
        return getSampleCount(stage) >= 30;
    }

    public void reset() {
        measurements.clear();

        activeStage = null;
        stageStartNanos = 0L;
    }

    private boolean isValid(
            double value
    ) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value);
    }

    /**
     * Rendering stages HyperFrame can measure.
     *
     * These are deliberately broad categories.
     * We can add more precise stages later when we have
     * a safe and useful hook for them.
     */
    public enum RenderStage {

        /**
         * Main world rendering.
         */
        WORLD,

        /**
         * Entities and block entities.
         */
        ENTITIES,

        /**
         * Particles.
         */
        PARTICLES,

        /**
         * Weather and clouds.
         */
        WEATHER,

        /**
         * HUD and GUI rendering.
         */
        GUI
    }

    private static final class StageMeasurement {

        private double latestMs;
        private double totalMs;
        private double worstMs;
        private long samples;

        private void record(
                double durationMs
        ) {
            latestMs = durationMs;

            totalMs += durationMs;

            worstMs =
                    Math.max(
                            worstMs,
                            durationMs
                    );

            samples++;
        }
    }
              }
