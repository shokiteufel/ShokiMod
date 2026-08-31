package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.scanner.ContestState;
import com.shokiteufel.shokimod.scanner.SkyblockClock;
import com.shokiteufel.shokimod.scanner.TabContest;

/**
 * Der laufende Contest mit Punktestand und Restzeit.
 *
 * Beides steht ueberall zur Verfuegung: die Uhr rechnet sich aus dem Tageszyklus, der
 * Punktestand kommt aus dem gemerkten Stand. Die Tab-Liste fuehrt ihn nur am Ort des
 * Contests - waere sie die einzige Quelle, wuerde die Anzeige beim Weggehen verschwinden.
 */
public final class ContestHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int TIME_COLOUR = HudColours.AQUA;
    private static final int AMOUNT_COLOUR = HudColours.GREEN;

    private ContestHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        if (!SkyblockClock.known() && ContestState.secondsRemaining() < 0) return panel;

        // Fest, nicht aus der Zeile gelesen: das Panel fuehrt genau diesen einen
        // Contest. Wo Hypixel einen fremden nennt - im Garden Jacobs - soll hier
        // weder der Name noch die Zahl wechseln
        panel.title(TabContest.HOST + "'s Contest", TITLE_COLOUR);

        // Waehrend der halben Minute dazwischen zaehlt die Uhr zum naechsten Start
        panel.pair(ContestState.running() ? "Ends In:" : "Starts In:",
                ContestState.remaining(), LABEL_COLOUR, TIME_COLOUR);

        // Ohne Bracket ist man noch unter der ersten Schwelle; die Menge steht trotzdem da
        String bracket = ContestState.bracket();
        panel.pair(bracket.isEmpty() ? "Collected:" : bracket + ":",
                String.valueOf(ContestState.amount()), LABEL_COLOUR, AMOUNT_COLOUR);

        if (!ContestState.next().isEmpty()) {
            panel.pair(ContestState.next() + " in:", "+" + ContestState.needed(),
                    LABEL_COLOUR, TIME_COLOUR);
        }
        return panel;
    }
}
