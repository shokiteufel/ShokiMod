package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.PerformanceState;

import java.util.Locale;

/**
 * Bilder je Sekunde, Server-Takt und Antwortzeit - je nach Wunsch einzeln.
 *
 * Die Farbe sagt, ob die Zahl gut ist: Wer hinsieht, will meist wissen, ob es am
 * eigenen Rechner liegt oder am Server, und das steht schneller in der Farbe als in
 * der Zahl.
 */
public final class PerformanceHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;

    private PerformanceHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.PerformanceHudCategory c = ModConfig.INSTANCE.hud.performance;

        if (c.showFps) {
            int fps = PerformanceState.fps();
            panel.pair("FPS:", String.valueOf(fps), LABEL_COLOUR, fpsColour(fps));
        }
        if (c.showTps) {
            double tps = PerformanceState.tps();
            panel.pair("TPS:", tps < 0 ? "-" : String.format(Locale.US, "%.1f", tps),
                    LABEL_COLOUR, tps < 0 ? HudColours.GRAY : tpsColour(tps));
        }
        if (c.showPing) {
            int ping = PerformanceState.ping();
            panel.pair("Ping:", ping < 0 ? "-" : ping + "ms",
                    LABEL_COLOUR, ping < 0 ? HudColours.GRAY : pingColour(ping));
        }
        return panel;
    }

    /** Ueber sechzig ist fluessig, unter dreissig merkt man es */
    private static int fpsColour(int fps) {
        if (fps >= 60) return HudColours.GREEN;
        if (fps >= 30) return HudColours.YELLOW;
        return HudColours.RED;
    }

    /** Zwanzig ist voller Takt; darunter laeuft die Welt langsamer als sie soll */
    private static int tpsColour(double tps) {
        if (tps >= 19.5) return HudColours.GREEN;
        if (tps >= 15.0) return HudColours.YELLOW;
        return HudColours.RED;
    }

    /** Bis hundert Millisekunden merkt man nichts, ab dreihundert alles */
    private static int pingColour(int ping) {
        if (ping <= 100) return HudColours.GREEN;
        if (ping <= 300) return HudColours.YELLOW;
        return HudColours.RED;
    }
}
