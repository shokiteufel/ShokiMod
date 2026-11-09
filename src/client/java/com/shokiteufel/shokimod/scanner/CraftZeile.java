package com.shokiteufel.shokimod.scanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Chatzeile, mit der Hypixel ein Craften meldet: "You Supercrafted Whale Bait x1,298!". Das Ergebnis landet meist direkt im Sack
 * ("[Sacks] +1,298 items, -10,384 items. (Last 5s.)" fuenf Sekunden spaeter) und sah fuer den Profit-Tracker wie ein Fund aus -
 * selbst gebaute Ware ist aber nie Beute (Schiggy, 09.11., 14:00: 1.298 Whale Bait im Tracker).
 */
final class CraftZeile {

    /** "You Supercrafted Whale Bait x1,298!" - die Menge fehlt bei einem einzelnen Stueck */
    private static final Pattern GECRAFTET = Pattern.compile("^You (?:Super)?[Cc]rafted (.+?)(?: x([\\d,]+))?!?$");

    private CraftZeile() {
    }

    /** Was gecraftet wurde und wie viel */
    record Gecraftet(String name, int menge) {
    }

    /** Die Angaben einer Craft-Zeile (ohne Farbcodes); null, wenn die Zeile keine ist */
    static Gecraftet lesen(String zeile) {
        if (zeile == null) return null;
        Matcher m = GECRAFTET.matcher(zeile.trim());
        if (!m.matches()) return null;
        String name = m.group(1).trim();
        if (name.isEmpty()) return null;
        int menge = 1;
        if (m.group(2) != null) {
            try {
                menge = Integer.parseInt(m.group(2).replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return menge > 0 ? new Gecraftet(name, menge) : null;
    }
}
