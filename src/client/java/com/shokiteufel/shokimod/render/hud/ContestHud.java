package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.scanner.ContestTimer;
import com.shokiteufel.shokimod.scanner.TabContest;

/**
 * Der laufende Contest mit Punktestand und Restzeit.
 *
 * Die Uhr kommt aus dem Tageszyklus, nicht aus der Tab-Liste - deshalb steht sie
 * ueberall zur Verfuegung, auch weit weg vom Ort des Contests. Der Punktestand
 * dagegen steht nur in der Tab-Liste und faellt weg, sobald sie ihn nicht mehr fuehrt.
 */
public final class ContestHud {

    private static final int TITLE_COLOUR = 0xFFFFC93C;
    private static final int LABEL_COLOUR = 0xFFE6E6E6;
    private static final int TIME_COLOUR = 0xFF6EF0FF;
    private static final int AMOUNT_COLOUR = 0xFF7CFF7C;

    private ContestHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        if (!ContestTimer.known()) return panel;

        // Ohne Tab-Eintrag ist der Ausrichter unbekannt; die Uhr laeuft trotzdem
        String host = TabContest.isActive() ? TabContest.host() + "'s Contest" : "Contest";
        panel.title(host, TITLE_COLOUR);

        if (ContestTimer.running()) {
            panel.pair("Ends In:", ContestTimer.remaining(), LABEL_COLOUR, TIME_COLOUR);
        } else {
            // Die halbe Minute zwischen zwei Contests
            panel.pair("Starts In:", ContestTimer.remaining(), LABEL_COLOUR, TIME_COLOUR);
        }

        if (!TabContest.isActive()) return panel;

        // Vor dem ersten Bracket kennt Hypixel nur die Menge
        String label = TabContest.bracket().isEmpty() ? "Collected:" : TabContest.bracket() + ":";
        if (!TabContest.amount().isEmpty()) {
            panel.pair(label, TabContest.amount(), LABEL_COLOUR, AMOUNT_COLOUR);
        }
        if (!TabContest.next().isEmpty()) {
            panel.pair(TabContest.next() + " in:", "+" + TabContest.needed(),
                    LABEL_COLOUR, TIME_COLOUR);
        }
        return panel;
    }
}
