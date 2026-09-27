package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.CollectionTrackerCategory;
import com.shokiteufel.shokimod.handler.CollectionTracker;
import com.shokiteufel.shokimod.handler.CollectionTracker.Row;
import com.shokiteufel.shokimod.util.CollectionData;
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
        String order = cfg.lineOrder == null ? "TGH" : cfg.lineOrder.code;
        for (int i = 0; i < rows.size() && i < limit; i++) {
            Row row = rows.get(i);
            panel.line(row.name(), LABEL_COLOUR);
            // Die drei Zeilen in der eingestellten Reihenfolge
            for (char line : order.toCharArray()) {
                switch (line) {
                    case 'T' -> {
                        if (cfg.showTotal) {
                            // Ohne einmal geoeffnetes Collections-Menue kennt die Mod den Stand nicht
                            panel.pair("  Total:", row.total() > 0 ? amount(row.total()) : "?", MUTED,
                                    row.total() > 0 ? LABEL_COLOUR : MUTED);
                        }
                    }
                    case 'G' -> {
                        if (cfg.showGained) panel.pair("  Gained:", "+" + amount(row.gained()), MUTED, VALUE_COLOUR);
                    }
                    case 'H' -> {
                        if (cfg.showPerHour && cfg.timerEnabled) {
                            panel.pair("  Per hour:", amount((long) CollectionTracker.perHour(row.gained())), MUTED, VALUE_COLOUR);
                        }
                    }
                    default -> {
                    }
                }
            }
            if (cfg.showNextTier) nextTier(panel, cfg, row);
        }
        if (rows.size() > limit) {
            panel.line("+" + (rows.size() - limit) + " more", MUTED);
        }

        // Nur bei offenem Fenster: dort kann man klicken. Immer die letzte Zeile
        if (Minecraft.getInstance().gui.screen() != null) {
            panel.blank();
            panel.line("[ Reset ]", TIME_COLOUR);
        }
        return panel;
    }

    /**
     * Die naechste Stufe und, wenn ein Tempo da ist, wann sie erreicht ist.
     *
     * Beides braucht den Gesamtstand aus dem Collections-Menue; ohne ihn steht hier
     * nichts, statt eine Stufe zu raten. Ist die Collection durch, sagt die Zeile das.
     */
    private static void nextTier(HudPanel panel, CollectionTrackerCategory cfg, Row row) {
        if (row.total() <= 0) return;

        CollectionData.Tier next = CollectionData.nextTier(row.collectionId(), row.total());
        if (next == null) {
            panel.pair("  Next tier:", "maxed", MUTED, LABEL_COLOUR);
            return;
        }
        panel.pair("  Next tier:", next.tier() + " in " + amount(next.missing()), MUTED, LABEL_COLOUR);

        // Die Zeit kommt aus demselben Tempo, das schon in der Zeile darueber steht
        double perHour = CollectionTracker.perHour(row.gained());
        if (!cfg.timerEnabled || perHour <= 0) return;
        panel.pair("  Reached in:", clock((long) (next.missing() * 3_600_000.0 / perHour)), MUTED, TIME_COLOUR);
    }

    /** Grosse Zahlen gekuerzt - dieselbe Schreibweise wie in den anderen Kaesten */
    private static String amount(long value) {
        return HudNumbers.amount(value);
    }

    private static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest) : String.format("%d:%02d", minutes, rest);
    }
}
