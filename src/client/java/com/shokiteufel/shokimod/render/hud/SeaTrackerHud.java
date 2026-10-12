package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.SeaCreatureTracker;
import com.shokiteufel.shokimod.handler.SeaCreatureTracker.Count;

import java.util.Locale;

/**
 * Der Kasten des Sea Creature Trackers, aufgebaut wie bei Feesh: je Bewohner eine Zeile
 * mit Anzahl und Anteil an allen Faengen, daneben die Spalte DH mit den Double Hooks
 * und ihrem Anteil. Zuunterst "Others" und die Summe, darunter die Blood Shots (BS).
 * In der Session-Ansicht folgen Zeit, SC/h und Hooks/h.
 *
 * Die Zahl steht weiss, ihr Anteil grau; die Farbe eines Namens ist die Seltenheit des
 * Bewohners. Gezeigt wird nur, was schon gefangen wurde.
 *
 * Die Ueberschrift ist unterstrichen, weil man sie anklicken kann: sie wechselt
 * zwischen Total und Session (im eigenen Inventar, wie bei den anderen Kaesten).
 */
public final class SeaTrackerHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int HEADER_COLOUR = HudColours.AQUA;

    private SeaTrackerHud() {
    }

    public static HudPanel build() {
        ModConfig.SeaTrackerCategory cfg = ModConfig.INSTANCE.fishing.tracker;
        HudPanel panel = new HudPanel();
        boolean session = SeaCreatureTracker.sessionView();
        // Zeile 0: ein Klick darauf wechselt die Ansicht (NearbyOverlay)
        panel.title("Sea Creatures §n" + (session ? "Session" : "Total"), TITLE_COLOUR);

        Count total = SeaCreatureTracker.total();
        if (cfg.showDoubleHooks) panel.table("", "", "DH", HEADER_COLOUR, HEADER_COLOUR);

        for (SeaCreatureTracker.Row row : SeaCreatureTracker.rows()) {
            line(panel, row.name() + ":", row.colour(), row.count(), total, cfg);
        }
        Count others = SeaCreatureTracker.others();
        if (others != null && cfg.showOthers) line(panel, "Others:", LABEL_COLOUR, others, total, cfg);

        // Die Summe steht unten, wo man sie sucht - ohne Anteil, der waere immer hundert
        panel.table("Total:", white(total.total),
                cfg.showDoubleHooks ? share(total.dh, events(total)) : "", LABEL_COLOUR, LABEL_COLOUR);
        if (cfg.showBloodShot) {
            // Anteil an allem, was gefangen wurde: die Blood Shots kommen zu den Faengen hinzu
            panel.table("BS:", share(total.cc, total.total + total.cc), "", LABEL_COLOUR, LABEL_COLOUR);
        }
        // Zeit und Raten gibt es nur fuer die Sitzung: der Gesamtstand hat keine Uhr
        if (session) {
            panel.table("Time:", clock(SeaCreatureTracker.sessionMillis()), "", LABEL_COLOUR, LABEL_COLOUR);
            panel.table("SC/h:", white(Math.round(SeaCreatureTracker.perHour())), "",
                    LABEL_COLOUR, LABEL_COLOUR);
            panel.table("Hooks/h:", white(Math.round(SeaCreatureTracker.hooksPerHour())), "",
                    LABEL_COLOUR, LABEL_COLOUR);
        }
        return panel;
    }

    private static void line(HudPanel panel, String name, int colour, Count count, Count total,
                             ModConfig.SeaTrackerCategory cfg) {
        String first = share(count.total, total.total);
        String second = cfg.showDoubleHooks ? share(count.dh, events(count)) : "";
        panel.table(name, first, second, colour, LABEL_COLOUR);
    }

    /** Wie oft die Angel ausgeworfen wurde: ein Double Hook bringt zwei Fange auf einmal */
    private static int events(Count count) {
        return count.total - count.dh;
    }

    private static String white(long value) {
        return "§f" + HudNumbers.amount(value);
    }

    /** "4 14,29%" - die Zahl weiss, ihr Anteil grau */
    private static String share(int part, int of) {
        return white(part) + " §7" + percent(part, of);
    }

    private static String percent(int part, int of) {
        if (of <= 0) return "0%";
        double value = part * 100.0 / of;
        // Deutsch mit Komma, wie auf dem Vorbild; ein ganzer Wert ohne Nachkommastellen
        return value == Math.rint(value)
                ? String.format(Locale.GERMANY, "%.0f%%", value)
                : String.format(Locale.GERMANY, "%.2f%%", value);
    }

    /** Millisekunden als h:mm:ss, ohne fuehrende Stunde solange noch keine voll ist */
    private static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
