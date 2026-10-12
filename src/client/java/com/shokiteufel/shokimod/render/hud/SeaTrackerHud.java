package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.SeaCreatureTracker;
import com.shokiteufel.shokimod.handler.SeaCreatureTracker.Count;

import java.util.List;

/**
 * Der Kasten des Sea Creature Trackers: oben die Summe mit Double Hooks (DH) und
 * Cocoons (CC), darunter eine Zeile je gewaehltem Bewohner, ganz unten "Others".
 *
 * Gezeigt wird nur, was schon gefangen wurde - ein Bewohner ohne Fang hat keine Zeile.
 */
public final class SeaTrackerHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;

    private SeaTrackerHud() {
    }

    public static HudPanel build() {
        ModConfig.SeaTrackerCategory cfg = ModConfig.INSTANCE.fishing.tracker;
        HudPanel panel = new HudPanel();
        panel.title("Sea Creatures", TITLE_COLOUR);

        panel.pair("Total:", value(SeaCreatureTracker.total(), cfg), LABEL_COLOUR, VALUE_COLOUR);

        List<SeaCreatureTracker.Row> rows = SeaCreatureTracker.rows();
        if (!rows.isEmpty()) panel.blank();
        for (SeaCreatureTracker.Row row : rows) {
            panel.pair(row.name() + ":", value(row.count(), cfg), row.colour(), VALUE_COLOUR);
        }

        Count others = SeaCreatureTracker.others();
        if (others != null && cfg.showOthers) {
            if (rows.isEmpty()) panel.blank();
            panel.pair("Others:", value(others, cfg), LABEL_COLOUR, VALUE_COLOUR);
        }
        return panel;
    }

    /** "120 (DH 8, CC 2)" - die Klammer nur mit dem, was eingeschaltet ist */
    private static String value(Count count, ModConfig.SeaTrackerCategory cfg) {
        StringBuilder text = new StringBuilder(HudNumbers.amount(count.total));
        StringBuilder extra = new StringBuilder();
        if (cfg.showDoubleHooks) extra.append("DH ").append(HudNumbers.amount(count.dh));
        if (cfg.showCocoons) {
            if (extra.length() > 0) extra.append(", ");
            extra.append("CC ").append(HudNumbers.amount(count.cc));
        }
        if (extra.length() > 0) text.append(" (").append(extra).append(')');
        return text.toString();
    }
}
