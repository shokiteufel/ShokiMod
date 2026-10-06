package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
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
 *
 * Bei offenem Fenster stehen vor jeder Zeile zwei Knoepfe. Der Kasten zeigt, was
 * gezaehlt wurde, nicht was gefallen ist; geht einmal etwas daneben, zieht man es
 * damit gerade, statt alles zurueckzusetzen. Mit gedrueckter Umschalttaste in
 * Zehnerschritten.
 */
public final class ProfitHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    /**
     * Coins in der Farbe der Ueberschrift.
     *
     * Gold ist im Spiel die Farbe des Geldes - die Muenzzeile im Menue, die Preise im
     * Basar, die Purse. Gruen stand vorher da und sah aus wie ein Haken: gut, richtig,
     * erledigt. Es ist aber ein Betrag.
     */
    private static final int VALUE_COLOUR = HudColours.GOLD;
    private static final int TIME_COLOUR = HudColours.AQUA;

    /** Die beiden Knoepfe vor einer Zeile, in der Reihenfolge, in der sie stehen */
    private static final String MINUS = "[-]";
    private static final String PLUS = "[+]";
    /**
     * Der Knopf zum Loeschen einer Ware.
     *
     * Rot, weil er als Einziger etwas wegnimmt - und mit einer Rueckfrage im Chat, weil
     * ein Klick daneben sonst einen Zaehlstand kostet. Das Ende setzt die Farbe der
     * Zeile wieder ein, sonst stuende auch der Name in Rot.
     */
    private static final String CROSS = "§c[X]§r";
    private static final String GAP = " ";

    /**
     * Welche Ware in welcher Zeile steht - fuer den Klick.
     *
     * Wird bei jedem Bau gefuellt und ist damit so aktuell wie der Kasten selbst. Der
     * Klick fragt denselben gepufferten Kasten ab, den er auch sieht.
     */
    private static List<String> rowIds = new ArrayList<>();

    private ProfitHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.ProfitCategory cfg = ModConfig.INSTANCE.profit;
        List<ProfitTracker.Row> rows = ProfitTracker.rows();
        List<String> ids = new ArrayList<>();
        boolean clickable = NearbyOverlay.interactive();

        // Der Zeitraum steht in der Ueberschrift, weil er alles darunter bestimmt - und
        // in Klammern, weil man ihn anklicken kann. Dieselbe Schreibweise wie beim
        // Reset-Knopf darunter, damit man den Zusammenhang sieht, ohne ihn zu suchen
        panel.title("Profit Tracker [ " + ProfitTracker.view() + " ]"
                + (ProfitTracker.isPaused() ? " (paused)" : ""), TITLE_COLOUR);
        ids.add(null);
        panel.blank();
        ids.add(null);

        if (rows.isEmpty()) {
            panel.line(cfg.selection == ModConfig.ProfitSelection.PICKED
                    ? "Nothing picked yet - open Items" : "Waiting for the first find", LABEL_COLOUR);
            ids.add(null);
        } else {
            int limit = Math.max(1, cfg.maxRows);
            for (int i = 0; i < rows.size() && i < limit; i++) {
                ProfitTracker.Row row = rows.get(i);
                String worth = row.priced() ? ItemValue.format(row.value()) : "?";
                // Ein verbrauchter Koeder steht im Minus - und in Rot, damit man es
                // nicht fuer einen Fund haelt
                panel.pair(prefix(clickable) + row.name() + " x" + HudNumbers.amount(row.count())
                                + mark(row.itemId(), row.mode()),
                        worth, ProfitTracker.colourOf(row.itemId()),
                        row.value() < 0 ? HudColours.RED : VALUE_COLOUR);
                ids.add(row.itemId());
            }
            if (rows.size() > limit) {
                panel.pair("+" + (rows.size() - limit) + " more", "", LABEL_COLOUR, LABEL_COLOUR);
                ids.add(null);
            }
        }

        panel.blank();
        ids.add(null);
        panel.pair("Total:", ItemValue.format(ProfitTracker.total()), LABEL_COLOUR,
                ProfitTracker.total() < 0 ? HudColours.RED : VALUE_COLOUR);
        ids.add(null);
        // Beide Zeilen einzeln: Wer nur wissen will, wie lange er schon dran ist,
        // braucht die Coins je Stunde nicht daneben
        if (cfg.timerEnabled && cfg.showPerHour) {
            panel.pair("Profit/h:", ItemValue.format(ProfitTracker.perHour()), LABEL_COLOUR, VALUE_COLOUR);
            ids.add(null);
        }
        if (cfg.timerEnabled && cfg.showTime) {
            // "Active Time", nicht "Time": Die Uhr zaehlt nur, solange etwas hereinkommt,
            // und die Wartezeit vor einer Pause wird wieder abgezogen
            panel.pair("Active Time:", clock(ProfitTracker.uptimeMillis()), LABEL_COLOUR, TIME_COLOUR);
            ids.add(null);
        }

        // Nur bei offenem Fenster, und nur im laufenden Lauf: Tag und Gesamtstand laufen
        // von selbst weiter, ein Knopf, der sie leert, gehoert nicht in den Kasten. Die
        // Zeile ist immer die letzte, darauf verlaesst sich der Klick im NearbyOverlay
        if (clickable && ProfitTracker.view() == ModConfig.ProfitView.SESSION) {
            panel.blank();
            ids.add(null);
            panel.line("[ Reset ]", TIME_COLOUR);
            ids.add(null);
        }

        rowIds = ids;
        return panel;
    }

    /** Die drei Knoepfe stehen nur da, wo man sie auch druecken kann */
    private static String prefix(boolean clickable) {
        return clickable ? MINUS + GAP + PLUS + GAP + CROSS + GAP : "";
    }

    /** Die Ware in dieser Zeile, oder null wenn dort keine steht */
    public static String itemAt(int row) {
        return row >= 0 && row < rowIds.size() ? rowIds.get(row) : null;
    }

    /**
     * Welcher Knopf sitzt an dieser Stelle? -1 fuer weniger, +1 fuer mehr, 0 fuer keiner.
     *
     * @param localX der Abstand vom linken Rand der Schrift, in den Massen des Kastens
     */
    /**
     * Welcher Knopf sitzt an dieser Stelle?
     *
     * @return -1 fuer [-], 1 fuer [+], 2 fuer [X], 0 fuer keinen
     */
    public static int buttonAt(Font font, double localX) {
        return buttonAt(font.width(MINUS), font.width(PLUS), font.width(CROSS), font.width(GAP), localX);
    }

    /**
     * Dasselbe, nur mit Breiten statt einer Schrift.
     *
     * Getrennt, damit die Rechnung ohne laufendes Spiel nachvollziehbar ist: Wer hier
     * danebengreift, loescht im schlimmsten Fall einen Zaehlstand.
     */
    static int buttonAt(int minusWidth, int plusWidth, int crossWidth, int gapWidth, double localX) {
        if (localX < 0) return 0;
        if (localX < minusWidth) return -1;

        int plusAnfang = minusWidth + gapWidth;
        int plusEnde = plusAnfang + plusWidth;
        if (localX >= plusAnfang && localX < plusEnde) return 1;

        int kreuzAnfang = plusEnde + gapWidth;
        return localX >= kreuzAnfang && localX < kreuzAnfang + crossWidth ? 2 : 0;
    }

    /** Ein Kuerzel hinter der Stueckzahl, wenn dieses Item eine eigene Verkaufsart hat */
    private static String mark(String itemId, SellMode mode) {
        if (!ProfitTracker.hasOwnMode(itemId)) return "";
        return switch (mode) {
            case INSTANT_SELL -> " (insta)";
            case SELL_ORDER -> " (order)";
            case NPC_SELL -> " (npc)";
            // Ohne eingetragene Zahl gilt weiter der Markt, und dann steht hier auch nichts
            case CUSTOM -> ProfitTracker.customPrice(itemId) > 0 ? " (own)" : "";
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
