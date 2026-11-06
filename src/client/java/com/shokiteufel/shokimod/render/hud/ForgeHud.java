package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.ForgeTracker;
import com.shokiteufel.shokimod.handler.ForgeTracker.Row;

import java.util.List;

/**
 * Der Forge-Kasten: je Platz der Gegenstand und wie lange er noch braucht - auf jeder Map, denn die Zeit liegt gemerkt
 * in {@link ForgeTracker}. Fertig steht gruen da, die letzten fuenf Minuten gelb.
 *
 * Im HUD-Editor stehen Beispielzeilen da, solange die Forge noch nie gelesen wurde, sonst liesse sich der Kasten nie setzen.
 */
public final class ForgeHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final long SOON_MILLIS = 5 * 60_000L;

    private ForgeHud() {
    }

    /** Gibt es etwas zu zeigen? Sonst bleibt der Kasten weg */
    public static boolean hasRows() {
        return ForgeTracker.hasRows(System.currentTimeMillis());
    }

    static int colourFor(Row row) {
        if (row.empty()) return HudColours.GRAY;
        if (row.ready()) return HudColours.GREEN;
        return row.remainingMs() <= SOON_MILLIS ? HudColours.YELLOW : HudColours.WHITE;
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        panel.title("Forge", TITLE_COLOUR);

        List<Row> rows = ForgeTracker.rows(System.currentTimeMillis());
        // HudEditorScreen.isOpen() statt Minecraft.screen: Der Zugriff darauf heisst auf 26.2 und 26.3 anders, das Flag nicht
        if (rows.isEmpty() && HudEditorScreen.isOpen()) {
            panel.pair("1. Refined Diamond", EffectsHud.format(2 * 3_600_000L + 15 * 60_000L), HudColours.GOLD, HudColours.WHITE);
            panel.pair("2. Mithril Plate", "Ready!", HudColours.GOLD, HudColours.GREEN);
            panel.pair("3. Empty", "-", HudColours.GRAY, HudColours.GRAY);
            return panel;
        }
        for (Row row : rows) {
            if (row.empty()) {
                panel.pair(row.slot() + ". Empty", "-", HudColours.GRAY, HudColours.GRAY);
            } else {
                panel.pair(row.slot() + ". " + row.item(), row.ready() ? "Ready!" : EffectsHud.format(row.remainingMs()),
                        HudColours.GOLD, colourFor(row));
            }
        }
        return panel;
    }
}
