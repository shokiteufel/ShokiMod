package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.gui.BannerDesignScreen;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Alles, was diese Mod über das Spielbild legt. Aufgerufen aus dem HudRenderMixin. */
public class HudRenderer {

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        // Die Einblendung läuft auch außerhalb von SkyBlock, damit der Testknopf
        // im Einstellungsfenster überall etwas zeigt
        AlertBanner.render(graphics);
        // Der Sandbox zeichnet die Vorschau selbst, ueber seinem Fenster - hier wuerde sie darunter liegen
        if (!(Minecraft.getInstance().screen instanceof BannerDesignScreen)) DropBanner.render(graphics);

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (client.level == null) return;
        if (!GameState.Server.isSkyblock()) return;

        // Namensschilder werden als HUD gezeichnet, nicht als Text in der Welt:
        // so liegen sie sicher vor dem Glow-Nacheffekt
        BossNameplateRenderer.render(graphics, client, deltaTracker.getGameTimeDeltaPartialTick(true));

        // Waehrend man Banner oder Kaesten einrichtet, sollen die Kaesten nicht dazwischenliegen
        if (client.screen instanceof HudEditorScreen || client.screen instanceof BannerDesignScreen) return;
        SafariHud.render(graphics);
    }
}
