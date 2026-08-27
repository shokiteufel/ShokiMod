package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.GameState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Alles, was diese Mod über das Spielbild legt. Aufgerufen aus dem HudRenderMixin. */
public class HudRenderer {

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        // Die Einblendung läuft auch außerhalb von SkyBlock, damit der Testknopf
        // im Einstellungsfenster überall etwas zeigt
        AlertBanner.render(graphics);

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (client.level == null) return;
        if (!GameState.Server.isSkyblock()) return;

        // Namensschilder werden als HUD gezeichnet, nicht als Text in der Welt:
        // so liegen sie sicher vor dem Glow-Nacheffekt
        BossNameplateRenderer.render(graphics, client, deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
