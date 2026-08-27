package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.scanner.TabEvents;

/**
 * Das laufende Event mit seiner Restzeit, gelesen aus der Tab-Liste.
 *
 * Anders als die beiden Safari-Kaesten gilt das ueberall in SkyBlock - das Event
 * laeuft schliesslich auch dort, wo man gerade nicht ist.
 */
public final class EventHud {

    private static final int LABEL_COLOUR = 0xFFFFC93C;
    private static final int NAME_COLOUR = 0xFFE45FFF;
    private static final int TIME_COLOUR = 0xFF6EF0FF;

    private EventHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        if (!TabEvents.isActive()) return panel;

        panel.pair("Event:", TabEvents.name(), LABEL_COLOUR, NAME_COLOUR);
        // Dauer-Events nennen keine Restzeit, dann bleibt die Zeile weg
        if (!TabEvents.remaining().isEmpty()) {
            panel.pair("Ends In:", TabEvents.remaining(), LABEL_COLOUR, TIME_COLOUR);
        }
        return panel;
    }
}
