package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.hud.NearbyOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zeichnet den Nearby-Kasten zuletzt, damit er ueber dem offenen Fenster liegt. */
@Mixin(Screen.class)
public class NearbyOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void shokimod$drawNearby(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        if (!NearbyOverlay.anyPanel()) return;
        NearbyOverlay.draw(graphics);
    }
}
