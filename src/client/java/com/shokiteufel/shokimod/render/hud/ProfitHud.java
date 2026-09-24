package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Kasten des Profit-Trackers: die Funde, darunter die Summe.
 *
 * Die Reihenfolge ist Absicht. Oben steht, was gerade faellt - das aendert sich
 * staendig und wird am haeufigsten angesehen. Die Summe darunter ist der Abschluss
 * und steht dort, wo man sie am Ende einer Liste erwartet; so wie es auch andere
 * Tracker halten.
 *
 * Jeder Name traegt die Farbe seiner Seltenheit, wie sie im Spiel aussieht: gruen
 * ungewoehnlich, blau selten, lila episch. Hinter der Stueckzahl steht die
 * Verkaufsart - aber nur, wenn sie von der Voreinstellung abweicht. Wer fuer alles
 * Sofortverkauf gewaehlt hat und nur beim Deep Sea Orb abweicht, soll genau das eine
 * Mal etwas dastehen sehen.
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
        panel.blank();

        if (rows.isEmpty()) {
            panel.line(cfg.selection == ModConfig.ProfitSelection.PICKED
                    ? "Nothing picked yet - open Items" : "Waiting for the first find", LABEL_COLOUR);
        } else {
            int limit = Math.max(1, cfg.maxRows);
            for (int i = 0; i < rows.size() && i < limit; i++) {
                ProfitTracker.Row row = rows.get(i);
                String worth = row.priced() ? ItemValue.format(row.value()) : "?";
                panel.pair(row.name() + " x" + row.count() + mark(row.itemId(), row.mode()),
                        worth, ProfitTracker.colourOf(row.itemId()), VALUE_COLOUR);
            }
            if (rows.size() > limit) {
                panel.pair("+" + (rows.size() - limit) + " more", "", LABEL_COLOUR, LABEL_COLOUR);
            }
        }

        panel.blank();
        panel.pair("Total:", ItemValue.format(ProfitTracker.total()), LABEL_COLOUR, VALUE_COLOUR);
        if (cfg.timerEnabled) {
            panel.pair("Profit/h:", ItemValue.format(ProfitTracker.perHour()), LABEL_COLOUR, VALUE_COLOUR);
            panel.pair("Time:", clock(ProfitTracker.uptimeMillis()), LABEL_COLOUR, TIME_COLOUR);
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
