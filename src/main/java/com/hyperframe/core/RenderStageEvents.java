package com.hyperframe.core;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Connects HyperFrame's RenderStageMonitor to the
 * real Minecraft 1.21.11 world-render pipeline.
 *
 * Diagnostic only:
 *
 * - does not change rendering;
 * - does not change input;
 * - does not add delays;
 * - does not cancel render passes.
 *
 * The purpose is to discover where frame-time is being spent.
 */
public final class RenderStageEvents {

    private RenderStageEvents() {
    }

    /**
     * Registers HyperFrame world-render measurements.
     */
    public static void register(
            RenderStageMonitor monitor
    ) {
        if (monitor == null) {
            throw new IllegalArgumentException(
                    "monitor cannot be null"
            );
        }

        /*
         * START_MAIN:
         *
         * Called after render chunks that need to be drawn
         * have been uploaded to the GPU and before terrain
         * drawing begins.
         */
        WorldRenderEvents.START_MAIN.register(
                context -> {
                    monitor.begin(
                            RenderStageMonitor.RenderStage.WORLD
                    );
                }
        );

        /*
         * BEFORE_ENTITIES:
         *
         * The main opaque terrain layers have been drawn.
         *
         * We finish the WORLD measurement here and begin
         * measuring the remainder of the main world pass.
         */
        WorldRenderEvents.BEFORE_ENTITIES.register(
                context -> {
                    monitor.end();

                    monitor.begin(
                            RenderStageMonitor.RenderStage.WORLD_MAIN_REMAINDER
                    );
                }
        );

        /*
         * END_MAIN:
         *
         * The main world render pass has finished.
         *
         * This measurement includes the work between
         * BEFORE_ENTITIES and END_MAIN, so we intentionally
         * do NOT call it "entities only".
         */
        WorldRenderEvents.END_MAIN.register(
                context -> {
                    monitor.end();
                }
        );
    }
}
