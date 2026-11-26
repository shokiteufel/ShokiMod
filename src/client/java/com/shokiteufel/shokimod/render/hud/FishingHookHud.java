package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.FishingHookDisplay;

/**
 * Der Kasten zur Zeit ueber dem Angelhaken: nur die Zahl (oder der Alarm), gross, ohne Untergrund.
 *
 * Herkunft der Idee: SkyHanni ("Fishing Hook Display"). Er steht nur da, solange der eigene Haken eine Zahl oder ein "!!!" zeigt; im HUD-Editor
 * ({@code /shoki hud}) immer, mit einem Beispiel, damit er sich setzen laesst. Lage, Groesse und Deckkraft sind einstellbar; die Deckkraft steht
 * von Haus aus auf null, es wird also nur die Schrift gezeichnet.
 */
public final class FishingHookHud {

    /** Das Beispiel im Editor: eine Zahl, wie Hypixel sie schreibt (gelb, fett) */
    static final String SAMPLE = "§e§l2.1";

    private FishingHookHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.fishing.hookDisplay.enabled) return false;
        return FishingHookDisplay.shown() != null || HudEditorScreen.isOpen();
    }

    public static HudPanel build() {
        String text = FishingHookDisplay.shown();
        if (text == null) text = SAMPLE;
        HudPanel panel = new HudPanel();
        panel.line(text, HudColours.WHITE);
        return panel;
    }
}
