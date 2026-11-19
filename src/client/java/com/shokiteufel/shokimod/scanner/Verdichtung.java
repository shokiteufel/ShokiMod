package com.shokiteufel.shokimod.scanner;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zwei verschiedene Dinge heissen bei Hypixel "Compact", und nur eines davon ist ein Fund.
 *
 * <p><b>Die Verzauberung Compact</b> (Spitzhacke) gibt mit kleiner Chance sofort die Enchanted-Form des abgebauten Stuecks und meldet
 * "COMPACT! You found an Enchanted Hard Stone!" - eine Zeile, ein Stueck. Das ist ein echter Fund (User, 19.11.: "ein anderes Enchant").
 *
 * <p><b>Der Personal Compactor</b>, seit 0.27.3 in den Saecken, verdichtet die Rohware dort von allein (160 Coal zu einem Enchanted Coal,
 * Hard Stone zu 576) und meldet dazu nichts im Chat. Die Sack-Meldung danach nennt das Ergebnis im Plus und die Rohware im Minus. Fuer den
 * Profit Tracker zaehlte das Ergebnis als Fund UND die Rohware, die schon beim Anfall gezaehlt war (User 19.11.: Coal x835k und daneben
 * Enchanted Coal x4.441, dieselbe Kohle zweimal). Das Enchanted-Stueck liegt jetzt im Sack und steht im Kasten; die Rohware, aus der es wurde,
 * geht ab ({@code ProfitTracker.deduct}). Der Wert bleibt, der Kasten zeigt, was im Sack liegt.
 *
 * <p>Im Plus einer Sack-Meldung stehen beide durcheinander. Auseinanderhalten laesst es sich so: Was die Zeilen der Verzauberung im Fenster der
 * Meldung nennen, ist Fund (ein Stueck je Zeile, ohne Rohware dahinter); der Rest des Enchanted-Postens ist verdichtet, soweit die Rohware
 * dazu im Minus derselben Meldung steht - nur dafuer geht Rohware ab. Am Log vom 19.11. nachgerechnet: Minus = (Enchanted im Plus - Zeilen) mal
 * Rezept ging in 10 von 22 Meldungen auf die Zahl auf, die uebrigen wichen um ganze Rezeptvielfache ab - ein Stueck am Fensterrand.
 */
final class Verdichtung {

    /** "COMPACT! You found an Enchanted Hard Stone!" - auch mit dem Zaehler "(2)", den ein Chat-Mod hinter gleiche Zeilen setzt */
    private static final Pattern ZEILE = Pattern.compile("^COMPACT!\\s+You found (?:an?\\s+)?([A-Za-z0-9].*?)!?(?:\\s+\\(\\d+\\))?$");

    /**
     * Mindestens so viel Rohware gehoert zu einem Enchanted-Stueck. Die Rezepte brauchen 80 bis 576 (Coal, Redstone, Iron: 160; Hard Stone:
     * 576), die Rezeptdaten des Trackers liegen aber nicht immer vor - darum eine Schwelle weit darunter statt der genauen Zahl.
     */
    static final int MIN_TEILER = 20;

    private Verdichtung() {
    }

    /** Eine Zeile der Verzauberung: zu welchen Kennungen ihr Name gehoert und wann sie kam. Je Zeile ein Stueck */
    record Eintrag(List<String> ids, long nanos) {
    }

    /** Eine Ware aus dem Minus einer Sack-Meldung: ihr Name und wie viel davon aus dem Sack ging */
    record Entnommen(String name, int menge) {
    }

    /** Der Name aus einer COMPACT!-Zeile der Verzauberung (ohne Farbcodes), oder null, wenn die Zeile keine ist */
    static String lesen(String zeile) {
        if (zeile == null) return null;
        Matcher m = ZEILE.matcher(zeile.trim());
        if (!m.matches()) return null;
        String name = m.group(1).trim();
        return name.isEmpty() ? null : name;
    }

    /** Wie viele Stuecke dieser Sorte hat die Verzauberung in diesem Zeitraum gemeldet (eine Zeile ist eines)? Die Grenzen zaehlen mit. */
    static int funde(List<Eintrag> eintraege, List<String> ids, long vonNanos, long bisNanos) {
        if (eintraege == null || ids == null || ids.isEmpty()) return 0;
        int n = 0;
        for (Eintrag e : eintraege) {
            if (e.nanos() < vonNanos || e.nanos() > bisNanos) continue;
            for (String id : ids) {
                if (id != null && e.ids().contains(id)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /**
     * Wie viel von "+menge Enchanted X" der Compactor aus Rohware verdichtet hat (dafuer geht die Rohware ab): Das Plus ohne die Stuecke der Verzauberung
     * ({@code echteFunde}), soweit die Rohware dazu im Minus derselben Meldung steht - Minus / {@link #MIN_TEILER} Stueck hoechstens.
     *
     * @param echteFunde so viele Stuecke dieser Sorte hat die Verzauberung im Fenster der Meldung gemeldet
     * @return die verdichtete Menge (0 bis menge)
     */
    static int aus(String name, int menge, int echteFunde, List<Entnommen> entfernt) {
        if (menge <= 0 || name == null || entfernt == null || entfernt.isEmpty()) return 0;
        String n = name.trim();
        if (!n.regionMatches(true, 0, "Enchanted ", 0, "Enchanted ".length())) return 0;
        String stamm = n.substring("Enchanted ".length()).trim();
        if (stamm.isEmpty()) return 0;
        int kandidaten = menge - Math.max(0, echteFunde);
        if (kandidaten <= 0) return 0;
        long moeglich = 0;
        for (Entnommen e : entfernt) {
            if (e == null || e.menge() <= 0 || !gehoert(stamm, e.name())) continue;
            moeglich += e.menge() / MIN_TEILER;
        }
        return (int) Math.min(kandidaten, moeglich);
    }

    /** Das groesste Rezept, das der Compactor kennt (Hard Stone): so viel Rohware geht fuer ein Stueck hoechstens ab */
    static final int MAX_TEILER = 576;

    /**
     * Welche Rohware fuer {@code verdichtet} Stuecke von "Enchanted X" vom Tracker abgeht: die Rohware X aus dem Minus derselben Meldung, hoechstens
     * so viel, wie die Stuecke beim groessten Rezept brauchen. Wer in derselben halben Minute nebenbei Rohware aus dem Sack nimmt, verliert so
     * hoechstens ein paar Stueck zu viel, nicht den ganzen Posten.
     *
     * @param uebrig die noch nicht zugeteilten Mengen, Stelle fuer Stelle zu {@code entfernt}; wird um das Zugeteilte gekuerzt, damit zwei
     *               Posten (Enchanted X und sein Block) dieselbe Entnahme nicht zweimal aufbrauchen
     */
    static List<Entnommen> rohwareFuer(String name, int verdichtet, int[] uebrig, List<Entnommen> entfernt) {
        List<Entnommen> aus = new java.util.ArrayList<>();
        if (verdichtet <= 0 || name == null || entfernt == null || uebrig == null || uebrig.length != entfernt.size()) return aus;
        String n = name.trim();
        if (!n.regionMatches(true, 0, "Enchanted ", 0, "Enchanted ".length())) return aus;
        String stamm = n.substring("Enchanted ".length()).trim();
        if (stamm.isEmpty()) return aus;
        long grenze = (long) verdichtet * MAX_TEILER;
        for (int i = 0; i < entfernt.size() && grenze > 0; i++) {
            Entnommen e = entfernt.get(i);
            if (e == null || uebrig[i] <= 0 || !gehoert(stamm, e.name())) continue;
            int nimm = (int) Math.min(uebrig[i], grenze);
            uebrig[i] -= nimm;
            grenze -= nimm;
            aus.add(new Entnommen(e.name(), nimm));
        }
        return aus;
    }

    /**
     * Ist das die Rohware zu diesem Stamm? "Coal" und "Coal", "Iron" und "Iron Ingot", "Redstone" und "Redstone Dust": gleich, oder der eine
     * Name beginnt mit dem anderen als ganzes Wort. Die Namen sind die, die Hypixel in der Sack-Meldung schreibt.
     */
    static boolean gehoert(String stamm, String entnommen) {
        if (stamm == null || entnommen == null) return false;
        String s = stamm.trim().toLowerCase(Locale.ROOT);
        String r = entnommen.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || r.isEmpty()) return false;
        return r.equals(s) || r.startsWith(s + " ") || s.startsWith(r + " ");
    }
}
