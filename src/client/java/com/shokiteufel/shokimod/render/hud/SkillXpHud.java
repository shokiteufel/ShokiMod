package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.SkillXpTracker;
import com.shokiteufel.shokimod.handler.SkillXpTracker.Row;

import java.util.List;
import java.util.Locale;

/**
 * Der Kasten des Skill-XP-Zaehlers: je Faehigkeit eine Zeile mit ihrer Erfahrung in dieser Sitzung und der Rate je Stunde, ihre aktive
 * Zeit in der Beschriftung. Unten steht die laengste aktive Zeit als "Time", damit man sieht, wie lange gezaehlt wurde.
 *
 * Im HUD-Editor stehen Beispielzeilen da, solange noch nichts gezaehlt wurde - sonst liesse sich der Kasten nie setzen.
 */
public final class SkillXpHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.AQUA;
    private static final int VALUE_COLOUR = HudColours.WHITE;
    private static final int RATE_COLOUR = HudColours.GREEN;

    private SkillXpHud() {
    }

    /** Gibt es etwas zu zeigen? Sonst bleibt der Kasten weg */
    public static boolean hasRows() {
        return SkillXpTracker.hasRows();
    }

    private static final String TITLE = "Skill XP  ";
    private static final String RESET_LABEL = "§c[Reset]";

    /**
     * Liegt ein Klick in der Ueberschrift auf dem Reset-Knopf? Wie bei den anderen Trackern: "Skill XP [Reset]", ein Klick im eigenen Inventar.
     *
     * @param localX Abstand vom linken Rand der Schrift, in den Massen des Kastens
     */
    public static boolean resetAt(net.minecraft.client.gui.Font font, double localX) {
        int start = font.width(TITLE);
        return localX >= start && localX < start + font.width(RESET_LABEL);
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        panel.title(TITLE + RESET_LABEL, TITLE_COLOUR);

        long now = System.currentTimeMillis();
        List<Row> rows = SkillXpTracker.rows(now);
        // HudEditorScreen.isOpen() statt Minecraft.screen: Der Zugriff darauf heisst auf 26.2 und 26.3 anders, das Flag nicht
        if (rows.isEmpty() && HudEditorScreen.isOpen()) {
            panel.pair("Fishing 12:34", "1.25M  5.97M/h", LABEL_COLOUR, VALUE_COLOUR);
            panel.pair("Farming 3:10", "310k  5.9M/h", LABEL_COLOUR, VALUE_COLOUR);
            return panel;
        }
        int shown = Math.max(1, Math.min(12, ModConfig.INSTANCE.skillXp.maxRows));
        for (int i = 0; i < rows.size() && i < shown; i++) {
            Row row = rows.get(i);
            panel.pair(row.skill() + " " + clock(row.activeMs()), value(row), LABEL_COLOUR, VALUE_COLOUR);
        }
        panel.pair("Time", clock(SkillXpTracker.longestActiveMs(now)), HudColours.GRAY, HudColours.GRAY);
        return panel;
    }

    /** "1.25M  5.97M/h" - solange noch zu wenig gezaehlt ist, steht statt der Rate ein Strich */
    static String value(Row row) {
        String rate = row.perHour() > 0.0 ? HudNumbers.amount(Math.round(row.perHour())) + "/h" : "-";
        return HudNumbers.amount(Math.round(row.xp())) + "  " + rate;
    }

    /** Millisekunden als h:mm:ss, ohne fuehrende Stunde solange noch keine voll ist */
    static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
