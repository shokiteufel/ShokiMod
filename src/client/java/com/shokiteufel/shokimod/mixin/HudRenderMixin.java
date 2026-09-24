package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor; // GuiGraphicsから置き換え
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudRenderMixin {

    /**
     * Der Einstieg ins Zeichnen (26.1.2):
     * public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
     *
     * Am Ende, nicht am Anfang. Vorn gezeichnet liegen die Kaesten unter allem, was das
     * Spiel danach noch ueber den Bildschirm legt - und dazu gehoert die Vignette, der
     * dunkle Saum zu den Raendern hin. Genau dort sitzen die Kaesten. Sie hat die Schrift
     * matt gefaerbt, und zwar unterschiedlich stark, weil ihre Staerke am Lichtwert
     * haengt: in der Hoehle dunkler als drueber.
     *
     * Bei offenem Fenster fiel das nie auf - dort zeichnet der NearbyOverlay die Kaesten
     * ueber das Fenster, also nach allem. Genau so sahen sie aus, wie sie aussehen
     * sollen, und genau das ist jetzt auch ohne offenes Fenster der Fall.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // HudRenderer.render も GuiGraphicsExtractor を受け取るように修正済み
        HudRenderer.render(graphics, deltaTracker);
    }
}