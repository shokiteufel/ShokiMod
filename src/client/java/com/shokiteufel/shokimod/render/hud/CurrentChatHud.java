package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.chat.CurrentChatDisplay;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;

/**
 * Der Kasten zu Chat > Current Chat Display: "Chat: Party (3 members)".
 *
 * Herkunft der Idee: SkyHanni ("Current Chat Display"). Er steht in SkyBlock bei offenem Chat und zwei Sekunden danach, sobald die Mod gelesen hat, in welchem
 * Chat man ist; im HUD-Editor ({@code /shoki hud}) immer, mit einem Beispiel, damit er sich setzen laesst.
 */
public final class CurrentChatHud {

    static final String SAMPLE = "§aChat: §eAll";

    private CurrentChatHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.chatTab.currentChatDisplay) return false;
        return HudEditorScreen.isOpen() || CurrentChatDisplay.visibleNow();
    }

    public static HudPanel build() {
        String line = CurrentChatDisplay.line();
        if (line.isEmpty() && HudEditorScreen.isOpen()) line = SAMPLE;
        HudPanel panel = new HudPanel();
        if (!line.isEmpty()) panel.line(line, HudColours.WHITE);
        return panel;
    }
}
