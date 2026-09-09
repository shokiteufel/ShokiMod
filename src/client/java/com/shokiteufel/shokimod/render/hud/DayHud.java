package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.scanner.ContestTimer;

/**
 * Der Tageszaehler, wie ihn GanKura hatte: ein kleiner Kasten mit "Day: 1.234".
 *
 * Die Tagesnummer fuehrt schon {@link ContestTimer} - dort haengt der Wechsel des
 * Jacob-Contests daran. Sie hier zweimal auszurechnen waere zwei Stellen, an denen
 * dieselbe Zahl auseinanderlaufen kann.
 *
 * GanKura fing die Zahl per Mixin direkt vom Server ab. Den Mixin gibt es seit dem Umbau
 * auf 26.1 nicht mehr; die Uhr der Dimension liefert dieselbe Zahl, steht aber direkt nach
 * dem Betreten noch nicht bereit - dann zeigt der Kasten einen Strich statt einer Null.
 */
public final class DayHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;
    private static final int VALUE_COLOUR = HudColours.WHITE;

    private DayHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        long day = ContestTimer.day();
        panel.pair("Day:", day < 0 ? "-" : String.format("%,d", day), LABEL_COLOUR, VALUE_COLOUR);
        return panel;
    }
}
