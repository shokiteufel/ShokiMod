package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.gui.BannerDesignScreen;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Alles, was diese Mod über das Spielbild legt. Aufgerufen aus dem HudRenderMixin. */
public class HudRenderer {

    /** Hat der Einsprung vor der Spielerliste in diesem Bild die Kaesten schon gezeichnet? Wird mit dem Bildende zurueckgesetzt */
    private static boolean panelsDone = false;
    private static boolean hookNoted = false;
    private static boolean fallbackNoted = false;

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        boolean done = panelsDone;
        panelsDone = false;
        // Fuers Performance log (Reiter Logs): ein Aufruf je Bild, ohne den Schalter kostet es einen Blick auf ein Flag
        com.shokiteufel.shokimod.util.PerfSampler.bild();
        com.shokiteufel.shokimod.util.DungeonLog.bild();
        // Die Einblendung läuft auch außerhalb von SkyBlock, damit der Testknopf
        // im Einstellungsfenster überall etwas zeigt.
        // Der Sandbox zeichnet die Vorschau selbst, ueber seinem Fenster - hier wuerde sie darunter liegen
        if (!(Minecraft.getInstance().screen instanceof BannerDesignScreen)) DropBanner.render(graphics);

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (client.level == null) return;
        if (!GameState.Server.isSkyblock()) return;

        // Namensschilder werden als HUD gezeichnet, nicht als Text in der Welt:
        // so liegen sie sicher vor dem Glow-Nacheffekt
        BossNameplateRenderer.render(graphics, client, deltaTracker.getGameTimeDeltaPartialTick(true));

        // Rueckfall: Kam der Aufruf vor der Spielerliste in diesem Bild nicht an (der Einsprung fand sein Ziel in dieser Spielversion nicht),
        // kommen die Kaesten hier, wie bis 2.3.14 - dann liegen sie ueber der Spielerliste, aber sie fehlen nie
        if (!done) {
            if (!fallbackNoted) {
                fallbackNoted = true;
                com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Hud] the panels are drawn at the end of the frame: the hook before the player list did not fire");
            }
            renderPanels(graphics);
        }
    }

    /** Der Einsprung vor dem Chat (HudRenderMixin): zeichnet die Kaesten und merkt, dass das Bildende es nicht noch einmal tun muss */
    public static void renderPanelsBeforeChat(GuiGraphicsExtractor graphics) {
        panelsDone = true;
        if (!hookNoted) {
            hookNoted = true;
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Hud] the panels are drawn before the chat and the player list (Tab), so both cover them");
        }
        renderPanels(graphics);
    }

    /**
     * Die Kaesten dieser Mod, vor dem Chat und der Spielerliste (Taste Tab) gezeichnet statt am Ende des Bildes: So liegen beide ueber ihnen, statt
     * dass sie sich durch die Kaesten hindurch lesen lassen muessen (User 18.11.: "die Tabs von Shoki sollen hinter dem Tab-Fenster stehen",
     * 19.11.: "der Chat soll wie Tab auch ueber den Fenstern von Shoki liegen"). Der Aufruf kommt aus dem HudRenderMixin, genau vor dem Zeichnen des
     * Chats. Banner und Namensschilder bleiben am Ende des Bildes ({@link #render}): Ein Alarm soll auch bei gedruckter Taste zu sehen sein.
     */
    public static void renderPanels(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (client.level == null) return;
        if (!GameState.Server.isSkyblock()) return;

        // Waehrend man Banner oder Kaesten einrichtet, sollen die Kaesten nicht dazwischenliegen.
        // Bei jedem anderen offenen Fenster zeichnet sie der NearbyOverlayMixin darueber -
        // hier noch einmal zu zeichnen kostet dieselbe Arbeit und verschwindet hinter dem Fenster
        if (client.screen != null) return;
        SafariHud.render(graphics);
        // Die Funny Map: der ganze Dungeon, nach den Kaesten gezeichnet, damit sie ueber der Karte von NoammAddons liegt
        com.shokiteufel.shokimod.dungeonkarte.FunnyKarteHud.render(graphics);
    }
}
