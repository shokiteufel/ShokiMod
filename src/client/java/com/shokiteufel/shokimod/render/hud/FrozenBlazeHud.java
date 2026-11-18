package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.FrozenBlaze;

/**
 * Der Zeitkasten zur Frozen Blazing Aura: wie viele Sekunden sie noch wirkt, bis man sich wieder bewegen muss.
 *
 * Herkunft der Idee: Riccio Fishing Utils (GPL-3.0), "Frozen Blaze aura timer". Die Regel dahinter steht in {@link FrozenBlaze}. Hier ist der
 * Kasten eigene Arbeit: ein Kasten des HUD-Editors ({@code /shoki hud}) mit Lage, Groesse und Deckkraft, eigenem Wortlaut und Farbstufen
 * gruen, gelb und rot. Er steht nur da, solange man das ganze Set traegt; im Editor immer, sonst liesse er sich nie setzen.
 */
public final class FrozenBlazeHud {

    private FrozenBlazeHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.fishing.frozenBlaze.auraTimer) return false;
        // HudEditorScreen.isOpen() statt Minecraft.screen: Der Zugriff darauf heisst auf 26.2 und 26.3 anders, das Flag nicht
        return FrozenBlaze.isWearing() || HudEditorScreen.isOpen();
    }

    /** Die Farbe der Zahl: mehr als zehn Sekunden gruen, bis zehn gelb, bis fuenf und aus rot */
    static int colourFor(int seconds) {
        if (seconds <= 5) return HudColours.RED;
        if (seconds <= 10) return HudColours.YELLOW;
        return HudColours.GREEN;
    }

    /** Der Wert: die Sekunden, und wenn die Aura aus ist, ein Hinweis, was zu tun ist */
    static String valueFor(int seconds) {
        return seconds <= 0 ? "OFF - move!" : seconds + "s";
    }

    public static HudPanel build() {
        // Im Editor, ohne das Set am Koerper, steht ein Beispiel da
        int seconds = !FrozenBlaze.isWearing() && HudEditorScreen.isOpen() ? 30 : FrozenBlaze.auraSecondsLeft();
        HudPanel panel = new HudPanel();
        panel.pair("Blazing Aura", valueFor(seconds), HudColours.AQUA, colourFor(seconds));
        return panel;
    }
}
