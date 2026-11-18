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
     *
     * Seit 2.3.15 zeichnet das Ende des Bildes nur noch Banner und Namensschilder; die Kaesten stehen kurz vorher, vor der Spielerliste
     * (siehe {@link #beforeTabList}) - auch das liegt hinter der Vignette, die ganz vorn kommt.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // HudRenderer.render も GuiGraphicsExtractor を受け取るように修正済み
        HudRenderer.render(graphics, deltaTracker);
    }

    /**
     * Die Kaesten kommen vor die Spielerliste (Taste Tab), damit diese ueber ihnen liegt (User 18.11.).
     *
     * In extractRenderState folgt auf den Chat der Aufruf des privaten extractTabList(GuiGraphicsExtractor, DeltaTracker); was davor
     * extrahiert wird, liegt darunter. Die Kaesten stehen so weiter ueber dem Chat und unter der Spielerliste. Banner und Namensschilder
     * bleiben am Ende des Bildes (siehe oben), damit ein Alarm auch bei gedruckter Taste zu sehen ist.
     *
     * Das Ziel ist per javap in den Jars von 26.1, 26.2 und 26.3 nachgesehen: ein Aufruf, in 26.1 in Gui, ab 26.2 in Hud.
     */
    @Inject(method = "extractRenderState", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractTabList(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void beforeTabList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudRenderer.renderPanelsBeforeTabList(graphics);
    }
}