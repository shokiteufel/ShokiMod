package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.HuntingTracker;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.PriceMode;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Kasten des Hunting Trackers: beide Gesamtwerte, Profit je Stunde, Jagdzeit
 * und die wertvollsten Shards.
 *
 * Nichts wird hier gerechnet; der Tracker liefert die Zeilen fertig. Der Kasten
 * ordnet sie nur an - und bleibt leer, solange noch kein Shard gefangen wurde,
 * damit kein leerer Rahmen auf dem Bildschirm steht.
 */
public final class HuntingHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;
    private static final int TIME_COLOUR = HudColours.AQUA;

    private HuntingHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        PriceMode mode = HuntingTracker.mode();
        List<HuntingTracker.Row> rows = HuntingTracker.rows(mode);
        if (rows.isEmpty() && HuntingTracker.uptimeMillis() <= 0L && Minecraft.getInstance().screen == null) return panel;

        ModConfig.HuntingTrackerCategory cfg = ModConfig.INSTANCE.hunting.tracker;
        panel.title("Hunting Tracker" + (HuntingTracker.isPaused() ? " (paused)" : ""), TITLE_COLOUR);

        // Beide Sichtweisen nebeneinander; die gewaehlte bestimmt Profit/h und die Zeilen
        panel.pair("Total (instant):", ItemValue.format(HuntingTracker.total(PriceMode.INSTANT_SELL)),
                LABEL_COLOUR, VALUE_COLOUR);
        panel.pair("Total (sell order):", ItemValue.format(HuntingTracker.total(PriceMode.SELL_ORDER)),
                LABEL_COLOUR, VALUE_COLOUR);
        if (cfg.timerEnabled) {
            panel.pair("Profit/h (" + (mode == PriceMode.SELL_ORDER ? "order" : "instant") + "):",
                    ItemValue.format(HuntingTracker.perHour()), LABEL_COLOUR, VALUE_COLOUR);
            panel.pair("Time:", clock(HuntingTracker.uptimeMillis()), LABEL_COLOUR, TIME_COLOUR);
        }

        if (!rows.isEmpty()) {
            panel.blank();
            int limit = Math.max(1, cfg.maxRows);
            for (int i = 0; i < rows.size() && i < limit; i++) {
                HuntingTracker.Row row = rows.get(i);
                String worth = row.priced() ? ItemValue.format(row.value()) : "?";
                panel.pair(row.name() + " x" + row.count(), worth, LABEL_COLOUR, VALUE_COLOUR);
            }
            if (rows.size() > limit) {
                panel.pair("+" + (rows.size() - limit) + " more", "", LABEL_COLOUR, LABEL_COLOUR);
            }
        }

        // Nur bei offenem Fenster: dort kann man klicken. Die Zeile ist immer die letzte,
        // darauf verlaesst sich der Klick im NearbyOverlay
        if (Minecraft.getInstance().screen != null) {
            panel.blank();
            panel.line("[ Reset ]", TIME_COLOUR);
        }
        return panel;
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
