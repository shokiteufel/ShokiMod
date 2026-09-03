package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.CollectionTrackerCategory;
import com.shokiteufel.shokimod.handler.CollectionTracker;
import com.shokiteufel.shokimod.handler.CollectionTracker.Row;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Kasten des Collection-Trackers: je Collection der Zuwachs, der Stand und das Tempo.
 *
 * Gerechnet wird nichts hier; der Tracker liefert die Zeilen. Der Kasten bleibt stehen,
 * auch wenn noch nichts gezaehlt wurde - sonst weiss man nicht, ob er ueberhaupt laeuft.
 */
public final class CollectionHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;
    private static final int TIME_COLOUR = HudColours.AQUA;
    private static final int MUTED = HudColours.GRAY;

    private CollectionHud() {
    }

    public static HudPanel build() {
        CollectionTrackerCategory cfg = ModConfig.INSTANCE.collections.tracker;
        HudPanel panel = new HudPanel();
        panel.title("Collections" + (CollectionTracker.isPaused() ? " (paused)" : ""), TITLE_COLOUR);

        List<Row> rows = CollectionTracker.rows();
        if (rows.isEmpty()) {
            panel.line("Nothing selected", MUTED);
            panel.line("Collections > Tracker > Choose", MUTED);
        }

        if (cfg.timerEnabled) {
            panel.pair("Time:", clock(CollectionTracker.uptimeMillis()), LABEL_COLOUR, TIME_COLOUR);
        }
        if (cfg.showValue) {
            double value = CollectionTracker.totalValue();
            panel.pair("Value:", ItemValue.format(value), LABEL_COLOUR, VALUE_COLOUR);
            if (cfg.timerEnabled) {
                panel.pair("Coins/h:", ItemValue.format(CollectionTracker.perHour(value)), LABEL_COLOUR, VALUE_COLOUR);
            }
        }

        if (!rows.isEmpty()) panel.blank();
        int limit = Math.max(1, cfg.maxRows);
        for (int i = 0; i < rows.size() && i < limit; i++) {
            Row row = rows.get(i);
            panel.line(row.name(), LABEL_COLOUR);
            if (row.total() > 0) {
                panel.pair("  Total:", amount(row.total()), MUTED, LABEL_COLOUR);
            }
            panel.pair("  Gained:", "+" + amount(row.gained()), MUTED, VALUE_COLOUR);
            if (cfg.timerEnabled) {
                panel.pair("  Per hour:", amount((long) CollectionTracker.perHour(row.gained())), MUTED, VALUE_COLOUR);
            }
        }
        if (rows.size() > limit) {
            panel.line("+" + (rows.size() - limit) + " more", MUTED);
        }

        // Nur bei offenem Fenster: dort kann man klicken. Immer die letzte Zeile
        if (Minecraft.getInstance().screen != null) {
            panel.blank();
            panel.line("[ Reset ]", TIME_COLOUR);
        }
        return panel;
    }

    /** Grosse Zahlen mit Trennzeichen, sehr grosse gekuerzt */
    private static String amount(long value) {
        if (value >= 1_000_000) return String.format("%.2fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format("%.1fk", value / 1_000.0);
        return String.format("%,d", value);
    }

    private static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest) : String.format("%d:%02d", minutes, rest);
    }
}
