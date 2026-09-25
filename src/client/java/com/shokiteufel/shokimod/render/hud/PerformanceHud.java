package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.ContestTimer;
import com.shokiteufel.shokimod.scanner.PerformanceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bilder je Sekunde, Server-Takt, Antwortzeit und der Tageszaehler - je nach Wunsch
 * einzeln, untereinander oder nebeneinander.
 *
 * Die Farbe sagt, ob die Zahl gut ist: Wer hinsieht, will meist wissen, ob es am
 * eigenen Rechner liegt oder am Server, und das steht schneller in der Farbe als in
 * der Zahl. Der Tag hat keine gute oder schlechte Farbe und bleibt weiss.
 *
 * Nebeneinander steht alles in einer Zeile. Die Farben stecken dann als Codes im
 * Text, weil eine Zeile nur eine Farbe kennt - dasselbe macht Hypixel in seiner
 * Seitenleiste.
 */
public final class PerformanceHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;
    /** Abstand zwischen zwei Angaben in der einzeiligen Form */
    private static final String GAP = "  ";

    private PerformanceHud() {
    }

    /** Eine Angabe: Beschriftung, Wert, und welche Farbe der Wert verdient */
    private record Entry(String label, String value, int colour) {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.PerformanceHudCategory c = ModConfig.INSTANCE.hud.performance;
        List<Entry> entries = new ArrayList<>(4);

        if (c.showFps) {
            int fps = PerformanceState.fps();
            entries.add(new Entry("FPS", String.valueOf(fps), fpsColour(fps)));
        }
        if (c.showTps) {
            double tps = PerformanceState.tps();
            entries.add(new Entry("TPS", tps < 0 ? "-" : String.format(Locale.US, "%.1f", tps),
                    tps < 0 ? HudColours.GRAY : tpsColour(tps)));
        }
        if (c.showPing) {
            int ping = PerformanceState.ping();
            entries.add(new Entry("Ping", ping < 0 ? "-" : ping + "ms",
                    ping < 0 ? HudColours.GRAY : pingColour(ping)));
        }
        if (c.showDay) {
            long day = ContestTimer.day();
            entries.add(new Entry("Day", day < 0 ? "-" : String.format("%,d", day), HudColours.WHITE));
        }

        if (entries.isEmpty()) return panel;

        if (c.arrangement == ModConfig.HudArrangement.INLINE) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (i > 0) line.append(GAP);
                line.append(code(LABEL_COLOUR)).append(entry.label()).append(' ')
                        .append(code(entry.colour())).append(entry.value());
            }
            panel.line(line.toString(), LABEL_COLOUR);
            return panel;
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            panel.pair(entry.label() + ":", entry.value(), LABEL_COLOUR, entry.colour());
        }
        return panel;
    }

    /** Der Farbcode zu einer Farbe der Palette - nur diese kommen hier vor */
    private static String code(int colour) {
        if (colour == HudColours.GREEN) return "§a";
        if (colour == HudColours.YELLOW) return "§e";
        if (colour == HudColours.RED) return "§c";
        if (colour == HudColours.GOLD) return "§6";
        if (colour == HudColours.GRAY) return "§7";
        return "§f";
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
