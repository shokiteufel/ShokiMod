package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.scanner.TabText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Namen der Besucher im Widget "Visitors: (n)" der Tab-Liste.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorApi.visitorsInTabList}, {@code TabWidget.VISITORS}). Von dort stammt die Regel: Auf die Zeile "Visitors: (3)"
 * folgen so viele Zeilen mit je einem Namen. Die Umsetzung ist eigene Arbeit: Java, gelesen wird der Text ohne Farbcodes, den die Mod aus der Tab-Liste schon hat.
 *
 * <p>Wo die Namen enden, sagt nicht die Kopfzeile allein (seit 2.6.14): Steht dort mehr, als darunter Besucher aufgelistet sind, laufen die naechsten Zeilen der Liste
 * weiter - und das sind Spieler ("[592] Name"), die Kopfzeile des naechsten Widgets ("Players (12)") und Hypixels Fuellzeilen. Bis 2.6.13 landeten sie alle als
 * "neue Besucher" in der Einkaufsliste. Jetzt beendet jede Zeile, die kein Besuchername sein kann, das Lesen: ein Doppelpunkt ("Next Visitor: ..."), eine Stufe in
 * Klammern (ein Spieler) und eine Zahl in Klammern am Ende (eine Kopfzeile). Die Fuellzeilen, die nur ihren rohen Profilnamen zeigen, nimmt schon der
 * {@code TabListScanner} heraus ({@link TabText#isBareProfileName}); sie kommen hier als Leerzeile an.
 */
public final class VisitorTab {

    private static final Pattern HEADER = Pattern.compile("^Visitors: \\((\\d+)\\)$");
    /** "[592] SchiggyMobil" - die Stufe in Klammern: ein Spieler, nie ein Besucher */
    private static final Pattern PLAYER = Pattern.compile("\\[[\\d,]+]");
    /** "Players (12)" - die Kopfzeile eines Widgets ohne Doppelpunkt */
    private static final Pattern WIDGET_HEAD = Pattern.compile(".*\\(\\d+\\)$");

    /**
     * Was die Tab-Liste ueber die Besucher sagt.
     *
     * @param found ob das Widget in der Liste steht (sonst weiss man nichts und darf das Buch nicht leeren)
     * @param count die Zahl in der Kopfzeile "Visitors: (n)"
     * @param names die Namen, in der Reihenfolge der Liste
     * @param stop  die Zeile, die das Lesen beendet hat, weil sie kein Besuchername sein kann (fuers Log); null, wenn alle Namen der Kopfzeile gelesen wurden oder
     *              die Liste zu Ende war
     */
    public record Result(boolean found, int count, List<String> names, String stop) {

        /** Stehen so viele Namen da, wie die Kopfzeile sagt? Nur dann darf ein fehlender Name als gegangen gelten (die Liste kann mitten im Aufbau stehen) */
        public boolean complete() {
            return found && names.size() == count;
        }
    }

    private VisitorTab() {
    }

    /** Kann diese Zeile (getrimmt, nicht leer) der Name eines Besuchers sein? */
    static boolean looksLikeName(String line) {
        // Ein Name hat keinen Doppelpunkt; dort beginnt das naechste Widget oder "Next Visitor:"
        if (line.indexOf(':') >= 0) return false;
        // Eine Stufe in Klammern hat nur ein Spieler
        if (PLAYER.matcher(line).find()) return false;
        // Eine Zahl in Klammern am Ende hat die Kopfzeile eines Widgets
        return !WIDGET_HEAD.matcher(line).matches();
    }

    /** Die Zeilen der Tab-Liste (ohne Farbcodes, getrimmt) nach dem Widget der Besucher absuchen */
    public static Result read(List<String> lines) {
        List<String> names = new ArrayList<>();
        if (lines == null) return new Result(false, 0, names, null);
        for (int i = 0; i < lines.size(); i++) {
            String header = TabText.tidy(lines.get(i));
            Matcher m = HEADER.matcher(header);
            if (!m.matches()) continue;
            int count = Integer.parseInt(m.group(1));
            int remaining = count;
            String stop = null;
            for (int j = i + 1; j < lines.size() && remaining > 0; j++) {
                String line = TabText.tidy(lines.get(j));
                // Eine Leerzeile gehoert nicht zur Liste, zaehlt aber nicht als Besucher (so kommen auch Hypixels Fuellzeilen an)
                if (line.isEmpty()) continue;
                if (!looksLikeName(line)) {
                    stop = line;
                    break;
                }
                names.add(line);
                remaining--;
            }
            return new Result(true, count, names, stop);
        }
        return new Result(false, 0, names, null);
    }
}
