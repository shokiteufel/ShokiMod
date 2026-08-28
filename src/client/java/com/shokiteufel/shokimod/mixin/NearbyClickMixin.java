package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.hud.NearbyOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faengt den Klick auf den Nearby-Kasten ab, bevor ihn das offene Fenster bekommt.
 *
 * Hier statt im Screen: Screen deklariert mouseClicked nicht selbst, es ist eine
 * Standardmethode der Schnittstelle - und ausgerechnet das Inventar ueberschreibt
 * sie. Durch onButton geht dagegen jeder Klick.
 */
@Mixin(MouseHandler.class)
public class NearbyClickMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void shokimod$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        // Nur bei offenem Fenster und nur beim Druecken, nicht beim Loslassen
        if (client.screen == null || action != 1 || client.getWindow() == null) return;

        double scaleX = (double) client.getWindow().getGuiScaledWidth()
                / client.getWindow().getScreenWidth();
        double scaleY = (double) client.getWindow().getGuiScaledHeight()
                / client.getWindow().getScreenHeight();

        if (NearbyOverlay.handleClick(client.mouseHandler.xpos() * scaleX,
                client.mouseHandler.ypos() * scaleY)) {
            ci.cancel();
        }
    }
}
