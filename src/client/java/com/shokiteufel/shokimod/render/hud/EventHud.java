package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.scanner.TabContest;
import com.shokiteufel.shokimod.scanner.TabEvents;

/**
 * Das laufende Event mit seiner Restzeit und, falls einer laeuft, der Contest dazu.
 *
 * Anders als die beiden Safari-Kaesten gilt das ueberall in SkyBlock - das Event
 * laeuft schliesslich auch dort, wo man gerade nicht ist.
 */
public final class EventHud {

    private static final int LABEL_COLOUR = 0xFFFFC93C;
    private static final int NAME_COLOUR = 0xFFE45FFF;
    private static final int TIME_COLOUR = 0xFF6EF0FF;
    private static final int AMOUNT_COLOUR = 0xFF7CFF7C;
    private static final int NEEDED_COLOUR = 0xFFE6E6E6;

    private EventHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();

        if (TabEvents.isActive()) {
            panel.pair("Event:", TabEvents.name(), LABEL_COLOUR, NAME_COLOUR);
            // Dauer-Events nennen keine Restzeit, dann bleibt die Zeile weg
            if (!TabEvents.remaining().isEmpty()) {
                panel.pair("Ends In:", TabEvents.remaining(), LABEL_COLOUR, TIME_COLOUR);
            }
        }

        appendContest(panel);
        return panel;
    }

    /**
     * Der Contest hat keine eigene Uhr - er endet mit dem Event. Deshalb steht er hier
     * unter der Restzeit und nicht in einem eigenen Kasten.
     */
    private static void appendContest(HudPanel panel) {
        if (!TabContest.isActive()) return;

        if (!panel.isEmpty()) panel.blank();
        panel.title(TabContest.host() + "'s Contest", LABEL_COLOUR);

        // Vor dem ersten Bracket kennt Hypixel nur die Menge
        String label = TabContest.bracket().isEmpty() ? "Collected:" : TabContest.bracket() + ":";
        if (!TabContest.amount().isEmpty()) {
            panel.pair(label, TabContest.amount(), NEEDED_COLOUR, AMOUNT_COLOUR);
        }

        if (!TabContest.next().isEmpty()) {
            panel.pair(TabContest.next() + " in:", "+" + TabContest.needed(),
                    NEEDED_COLOUR, TIME_COLOUR);
        }
    }
}
