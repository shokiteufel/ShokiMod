package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Kasten des Profit-Trackers: Gesamtwert, Profit je Stunde, Zeit und die
 * wertvollsten Funde.
 *
 * Hinter jeder Zeile steht, wie dieses Item zu Geld gemacht wird - aber nur, wenn
 * es nicht die Voreinstellung ist. Wer fuer alles Sofortverkauf gewaehlt hat und
 * nur beim Deep Sea Orb abweicht, soll genau das eine Mal etwas dastehen sehen.
 */
public final class ProfitHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;
    private static final int TIME_COLOUR = HudColours.AQUA;

    private ProfitHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.ProfitCategory cfg = ModConfig.INSTANCE.profit;
        List<ProfitTracker.Row> rows = ProfitTracker.rows();

        panel.title("Profit Tracker" + (ProfitTracker.isPaused() ? " (paused)" : ""), TITLE_COLOUR);
        panel.pair("Total:", ItemValue.format(ProfitTracker.total()), LABEL_COLOUR, VALUE_COLOUR);
        if (cfg.timerEnabled) {
            panel.pair("Profit/h:", ItemValue.format(ProfitTracker.perHour()), LABEL_COLOUR, VALUE_COLOUR);
            panel.pair("Time:", clock(ProfitTracker.uptimeMillis()), LABEL_COLOUR, TIME_COLOUR);
        }

        if (rows.isEmpty()) {
            panel.blank();
            panel.line(cfg.selection == ModConfig.ProfitSelection.PICKED
                    ? "Nothing picked yet - open Items" : "Waiting for the first find", LABEL_COLOUR);
        } else {
            panel.blank();
            int limit = Math.max(1, cfg.maxRows);
            for (int i = 0; i < rows.size() && i < limit; i++) {
                ProfitTracker.Row row = rows.get(i);
                String worth = row.priced() ? ItemValue.format(row.value()) : "?";
                panel.pair(row.name() + " x" + row.count() + mark(row.itemId(), row.mode()),
                        worth, LABEL_COLOUR, VALUE_COLOUR);
            }
            if (rows.size() > limit) {
                panel.pair("+" + (rows.size() - limit) + " more", "", LABEL_COLOUR, LABEL_COLOUR);
            }
        }

        // Nur bei offenem Fenster: dort kann man klicken. Die Zeile ist immer die letzte,
        // darauf verlaesst sich der Klick im NearbyOverlay
        if (Minecraft.getInstance().gui.screen() != null) {
            panel.blank();
            panel.line("[ Reset ]", TIME_COLOUR);
        }
        return panel;
    }

    /** Ein Kuerzel hinter der Stueckzahl, wenn dieses Item eine eigene Verkaufsart hat */
    private static String mark(String itemId, SellMode mode) {
        if (!ProfitTracker.hasOwnMode(itemId)) return "";
        return switch (mode) {
            case INSTANT_SELL -> " (insta)";
            case SELL_ORDER -> " (order)";
            case NPC_SELL -> " (npc)";
        };
    }

    /** Millisekunden als h:mm:ss, ohne fuehrende Stunde wenn noch keine voll ist */
    private static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, rest)
                : String.format("%d:%02d", minutes, rest);
    }
}
