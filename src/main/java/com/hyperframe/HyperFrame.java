package com.hyperframe;

import com.hyperframe.core.FrameMonitor;
import net.fabricmc.api.ClientModInitializer;

public class HyperFrame implements ClientModInitializer {

    public static final FrameMonitor FRAME_MONITOR =
            new FrameMonitor();

    @Override
    public void onInitializeClient() {
        System.out.println("[HyperFrame] Frame Stability Core initialized.");
    }
}
