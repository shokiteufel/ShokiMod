package com.shokiteufel.shokimod.dungeonkarte;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Das Stockwerk aus der Seitenleiste: "The Catacombs (F5)" ist 5, "(M7)" (Master) 7, "(E)" der Eingang 0 */
public final class Stockwerk {

    private static final Pattern KLAMMER = Pattern.compile("\\((?:[FM](\\d)|(E))\\)");

    private Stockwerk() {
    }

    /** Das Stockwerk einer Zeile ohne Farbcodes; null, wenn keine Angabe darin steht */
    public static Integer aus(String zeile) {
        if (zeile == null) return null;
        Matcher m = KLAMMER.matcher(zeile);
        if (!m.find()) return null;
        return m.group(2) != null ? Integer.valueOf(0) : Integer.valueOf(m.group(1));
    }
}
