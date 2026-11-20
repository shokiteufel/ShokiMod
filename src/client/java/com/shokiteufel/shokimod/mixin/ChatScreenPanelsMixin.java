package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.hud.NearbyOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bei offenem Chat liegen die Kaesten unter dem Chat (User 19.11.: "der Chat soll wie Tab auch ueber den Fenstern von Shoki liegen").
 *
 * ChatScreen.extractRenderState (26.1, 26.2 und 26.3 gleich, per javap nachgesehen) zeichnet zuerst die Leiste der Eingabe, dann den Chatverlauf
 * und ruft zuletzt Screen.extractRenderState - und dort haengt der NearbyOverlayMixin die Kaesten an, also ueber den Chat. Hier, am Anfang der
 * Methode, kommen sie vor all das: Was danach extrahiert wird, liegt darueber.
 *
 * Findet der Einsprung sein Ziel nicht ({@code require = 0}), laeuft das Spiel weiter, und der NearbyOverlayMixin zeichnet die Kaesten wie bisher
 * ueber dem Chat - sie fehlen nie.
 */
@Mixin(ChatScreen.class)
public class ChatScreenPanelsMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void shokimod$panelsUnderChat(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        NearbyOverlay.drawUnderChat(graphics);
    }
}
