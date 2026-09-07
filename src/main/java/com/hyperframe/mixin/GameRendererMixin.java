package com.hyperframe.mixin;

import com.hyperframe.HyperFrame;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void hyperFrame$beginFrame(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        HyperFrame.FRAME_MONITOR.beginFrame();
        HyperFrame.analyzeFrame();
    }
}
