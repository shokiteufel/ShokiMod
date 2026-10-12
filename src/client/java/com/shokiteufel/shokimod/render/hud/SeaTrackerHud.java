package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.SeaCreatureTracker;
import com.shokiteufel.shokimod.handler.SeaCreatureTracker.Count;

import java.util.List;
import java.util.Locale;

/**
 * Der Kasten des Sea Creature Trackers, aufgebaut wie bei Feesh: je Bewohner eine Zeile
 * mit Anzahl und Anteil an allen Faengen, daneben die Spalte DH mit den Double Hooks
 * und ihrem Anteil. Zuunterst "Others" und die Summe, darunter die Blood Shots (BS).
 *
 * Gezeigt wird nur, was schon gefangen wurde - ein Bewohner ohne Fang hat keine Zeile.
 * Die Farbe eines Namens ist die Seltenheit des Bewohners.
 */
public final class SeaTrackerHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;
    private static final int HEADER_COLOUR = HudColours.AQUA;

    private SeaTrackerHud() {
    }

    public static HudPanel build() {
        ModConfig.SeaTrackerCategory cfg = ModConfig.INSTANCE.fishing.tracker;
        HudPanel panel = new HudPanel();
        boolean session = SeaCreatureTracker.sessionView();
        panel.title(session ? "Sea Creatures (Session)" : "Sea Creatures", TITLE_COLOUR);

        Count total = SeaCreatureTracker.total();
        if (cfg.showDoubleHooks) panel.table("", "", "DH", HEADER_COLOUR, HEADER_COLOUR);

        for (SeaCreatureTracker.Row row : SeaCreatureTracker.rows()) {
            line(panel, row.name() + ":", row.colour(), row.count(), total, cfg);
        }
        Count others = SeaCreatureTracker.others();
        if (others != null && cfg.showOthers) line(panel, "Others:", LABEL_COLOUR, others, total, cfg);

        // Die Summe steht unten, wo man sie sucht - ohne Anteil, der waere immer hundert
        panel.table("Total:", HudNumbers.amount(total.total),
                cfg.showDoubleHooks ? share(total.dh, events(total)) : "", LABEL_COLOUR, VALUE_COLOUR);
        if (cfg.showBloodShot) {
            panel.table("BS:", HudNumbers.amount(total.cc), "", LABEL_COLOUR, VALUE_COLOUR);
        }
        // Zeit und Rate gibt es nur fuer die Sitzung: der Gesamtstand hat keine Uhr
        if (session) {
            panel.table("Time:", clock(SeaCreatureTracker.sessionMillis()), "", LABEL_COLOUR, HEADER_COLOUR);
            panel.table("SC/h:", HudNumbers.amount(Math.round(SeaCreatureTracker.perHour())), "",
                    LABEL_COLOUR, VALUE_COLOUR);
        }
        return panel;
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

    private static void line(HudPanel panel, String name, int colour, Count count, Count total,
                             ModConfig.SeaTrackerCategory cfg) {
        String first = HudNumbers.amount(count.total) + " " + percent(count.total, total.total);
        String second = cfg.showDoubleHooks ? share(count.dh, events(count)) : "";
        panel.table(name, first, second, colour, VALUE_COLOUR);
    }

    /** Wie oft die Angel ausgeworfen wurde: ein Double Hook bringt zwei Fange auf einmal */
    private static int events(Count count) {
        return count.total - count.dh;
    }

    /** "4 14,29%" - die Anzahl und ihr Anteil an den Auswuerfen */
    private static String share(int part, int of) {
        return HudNumbers.amount(part) + " " + percent(part, of);
    }

    private static String percent(int part, int of) {
        if (of <= 0) return "0%";
        double value = part * 100.0 / of;
        // Deutsch mit Komma, wie auf dem Vorbild; ein ganzer Wert ohne Nachkommastellen
        return value == Math.rint(value)
                ? String.format(Locale.GERMANY, "%.0f%%", value)
                : String.format(Locale.GERMANY, "%.2f%%", value);
    }
}
