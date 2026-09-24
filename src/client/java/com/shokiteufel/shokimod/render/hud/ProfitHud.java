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
        boolean clickable = Minecraft.getInstance().gui.screen() != null;

        panel.title("Profit Tracker" + (ProfitTracker.isPaused() ? " (paused)" : ""), TITLE_COLOUR);
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
                panel.pair(prefix(clickable) + row.name() + " x" + row.count()
                                + mark(row.itemId(), row.mode()),
                        worth, ProfitTracker.colourOf(row.itemId()), VALUE_COLOUR);
                ids.add(row.itemId());
            }
            if (rows.size() > limit) {
                panel.pair("+" + (rows.size() - limit) + " more", "", LABEL_COLOUR, LABEL_COLOUR);
                ids.add(null);
            }
        }

        panel.blank();
        ids.add(null);
        panel.pair("Total:", ItemValue.format(ProfitTracker.total()), LABEL_COLOUR, VALUE_COLOUR);
        ids.add(null);
        if (cfg.timerEnabled) {
            panel.pair("Profit/h:", ItemValue.format(ProfitTracker.perHour()), LABEL_COLOUR, VALUE_COLOUR);
            ids.add(null);
            panel.pair("Time:", clock(ProfitTracker.uptimeMillis()), LABEL_COLOUR, TIME_COLOUR);
            ids.add(null);
        }

        // Nur bei offenem Fenster: dort kann man klicken. Die Zeile ist immer die letzte,
        // darauf verlaesst sich der Klick im NearbyOverlay
        if (clickable) {
            panel.blank();
            ids.add(null);
            panel.line("[ Reset ]", TIME_COLOUR);
            ids.add(null);
        }

        rowIds = ids;
        return panel;
    }

    /** Die beiden Knoepfe stehen nur da, wo man sie auch druecken kann */
    private static String prefix(boolean clickable) {
        return clickable ? MINUS + GAP + PLUS + GAP : "";
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
    public static int buttonAt(Font font, double localX) {
        if (localX < 0) return 0;

        int minus = font.width(MINUS);
        if (localX < minus) return -1;

        int plusStart = minus + font.width(GAP);
        return localX >= plusStart && localX < plusStart + font.width(PLUS) ? 1 : 0;
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
