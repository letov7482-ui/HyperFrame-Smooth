package com.hyperframe;

import com.hyperframe.core.FrameMonitor;
import com.hyperframe.core.FrameStatistics;
import net.fabricmc.api.ClientModInitializer;

public class HyperFrame implements ClientModInitializer {

    public static final FrameMonitor FRAME_MONITOR =
            new FrameMonitor();

    public static final FrameStatistics FRAME_STATISTICS =
            new FrameStatistics(FRAME_MONITOR);

    @Override
    public void onInitializeClient() {
        System.out.println(
                "[HyperFrame] Frame Stability Core initialized."
        );
    }
}
