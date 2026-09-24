package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.HudRenderer;
import net.minecraft.client.DeltaTracker;
// 26.2: extractRenderState(GuiGraphicsExtractor, DeltaTracker) ist von Gui nach Hud gewandert
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor; // GuiGraphicsから置き換え
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudRenderMixin {

    /**
     * Einstieg ins Zeichnen ab 26.2 (vorher: Gui#extractRenderState):
     * public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // HudRenderer.render も GuiGraphicsExtractor を受け取るように修正済み
        HudRenderer.render(graphics, deltaTracker);
    }
}