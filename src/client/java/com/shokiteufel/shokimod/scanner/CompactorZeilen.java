package com.shokiteufel.shokimod.scanner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Compactor, den Hypixel mit 0.27.3 in die Saecke gebracht hat: Er verdichtet die Rohware dort von allein zu ihrer Enchanted-Form
 * (160 Coal zu einem Enchanted Coal) und meldet jedes Mal "COMPACT! You found an Enchanted Hard Stone!".
 *
 * Die Sack-Meldung danach nennt das Ergebnis im Plus ("+4 Enchanted Coal") und die Rohware im Minus ("-640 Coal"). Fuer den Profit Tracker sah
 * das Ergebnis wie ein Fund aus - die Rohware zaehlte aber schon, als sie anfiel, und stand zugleich noch einmal in der Form, in die sie
 * verdichtet wurde (User 19.11., Coal x835k und daneben Enchanted Coal x4.441: dieselbe Kohle zweimal). Verdichtetes ist wie Gecraftetes
 * nie Beute ({@link CraftZeile}).
 *
 * Die Zeile sagt nicht, wie viele Stuecke es waren: Eine Verdichtung kann eines bringen, ein grosser Schwung auf einmal mehrere Tausend
 * (2.242 Enchanted Coal in einer Zeile, -358.720 Coal in der Sack-Meldung). Die Menge steht allein in der Sack-Meldung; die Zeile belegt nur,
 * dass das Enchanted-Stueck dieser Sorte aus dem Compactor kam.
 */
final class CompactorZeilen {

    /** "COMPACT! You found an Enchanted Hard Stone!" - auch mit dem Zaehler "(2)", den ein Chat-Mod hinter gleiche Zeilen setzt */
    private static final Pattern ZEILE = Pattern.compile("^COMPACT!\\s+You found (?:an?\\s+)?([A-Za-z0-9].*?)!?(?:\\s+\\(\\d+\\))?$");

    private CompactorZeilen() {
    }

    /** Wann eine Zeile kam und zu welchen Kennungen ihr Name gehoert */
    record Eintrag(List<String> ids, long nanos) {
    }

    /** Der Name aus einer Compactor-Zeile (ohne Farbcodes), oder null, wenn die Zeile keine ist */
    static String lesen(String zeile) {
        if (zeile == null) return null;
        Matcher m = ZEILE.matcher(zeile.trim());
        if (!m.matches()) return null;
        String name = m.group(1).trim();
        return name.isEmpty() ? null : name;
    }

    /** Eine Ware aus dem Minus einer Sack-Meldung: ihr Name und wie viel davon aus dem Sack ging */
    record Entnommen(String name, int menge) {
    }

    /**
     * Mindestens so viel Rohware gehoert zu einem Enchanted-Stueck. Die Rezepte brauchen 80 bis 576 (Coal, Redstone, Iron: 160; Hard Stone:
     * rund 500), die Rezeptdaten des Trackers liegen aber nicht immer vor - darum eine Schwelle weit darunter statt der genauen Zahl.
     */
    static final int MIN_TEILER = 20;

    /**
     * Wie viel von "+menge Enchanted X" aus der Verdichtung kam: so viel, wie die Rohware im Minus derselben Meldung hergibt.
     *
     * Der Compactor meldet nicht jede Verdichtung im Chat - ein grosser Schwung auf einmal (1.825 Enchanted Redstone mit 292.000 Redstone im
     * Minus) hatte keine "COMPACT!"-Zeile, bei Coal fehlte sie bei jedem siebten Stueck. Das Minus der Sack-Meldung aber nennt die verbrauchte
     * Rohware immer. Steht neben "+4.441 Enchanted Coal" ein "-710.560 Coal", ist das Verdichten, kein Fund.
     *
     * @return die Menge, die kein Fund ist (0 bis menge)
     */
    static int aus(String name, int menge, List<Entnommen> entfernt) {
        if (menge <= 0 || name == null || entfernt == null || entfernt.isEmpty()) return 0;
        String n = name.trim();
        if (!n.regionMatches(true, 0, "Enchanted ", 0, "Enchanted ".length())) return 0;
        String stamm = n.substring("Enchanted ".length()).trim();
        if (stamm.isEmpty()) return 0;
        long moeglich = 0;
        for (Entnommen e : entfernt) {
            if (e == null || e.menge() <= 0 || !gehoert(stamm, e.name())) continue;
            moeglich += e.menge() / MIN_TEILER;
        }
        return (int) Math.min(menge, moeglich);
    }

    /**
     * Ist das die Rohware zu diesem Stamm? "Coal" und "Coal", "Iron" und "Iron Ingot", "Redstone" und "Redstone Dust": gleich, oder der eine
     * Name beginnt mit dem anderen als ganzes Wort. Die Namen sind die, die Hypixel in der Sack-Meldung schreibt.
     */
    static boolean gehoert(String stamm, String entnommen) {
        if (stamm == null || entnommen == null) return false;
        String s = stamm.trim().toLowerCase(java.util.Locale.ROOT);
        String r = entnommen.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty() || r.isEmpty()) return false;
        return r.equals(s) || r.startsWith(s + " ") || s.startsWith(r + " ");
    }

    /** Hat der Compactor in diesem Zeitraum etwas dieser Sorte gemeldet? Die Grenzen zaehlen mit. */
    static boolean imFenster(List<Eintrag> eintraege, List<String> ids, long vonNanos, long bisNanos) {
        if (eintraege == null || ids == null || ids.isEmpty()) return false;
        for (Eintrag e : eintraege) {
            if (e.nanos() < vonNanos || e.nanos() > bisNanos) continue;
            for (String id : ids) {
                if (id != null && e.ids().contains(id)) return true;
            }
        }
        return false;
    }
}
