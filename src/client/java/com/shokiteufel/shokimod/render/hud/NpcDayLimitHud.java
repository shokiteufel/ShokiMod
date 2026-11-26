package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.NpcDayLimit;

/**
 * Der Kasten zum NPC Daily Limit Tracker: wie viel vom Tageslimit der NPCs schon verbraucht ist, etwa 400k/500m.
 *
 * Herkunft der Idee: SkyHanni ("NPC Daily Limit Tracker"). Er steht in SkyBlock, solange der Tracker an ist; im HUD-Editor
 * ({@code /shoki hud}) immer, damit er sich setzen laesst.
 */
public final class NpcDayLimitHud {

    private NpcDayLimitHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.misc.npcLimit.enabled) return false;
        return GameState.Server.isSkyblock() || HudEditorScreen.isOpen();
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        // "§6400k§f/§6500m": bei HUD > Bold numbers beides fett; NpcDayLimit.hudLine() selbst bleibt unveraendert (Tests pruefen den Text)
        String line = NpcDayLimit.hudLine();
        panel.line(HudNumbers.bold() ? HudNumbers.bolden(line) : line, HudColours.WHITE);
        return panel;
    }
}
