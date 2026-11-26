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
 * folgen so viele Zeilen mit je einem Namen; Hypixel schreibt sie mit einem Leerzeichen davor. Die Umsetzung ist eigene Arbeit: Java, gelesen wird der Text ohne
 * Farbcodes, den die Mod aus der Tab-Liste schon hat; die Zeile "Next Visitor: ..." und jede andere mit einem Doppelpunkt ist kein Name und beendet die Liste.
 */
public final class VisitorTab {

    private static final Pattern HEADER = Pattern.compile("^Visitors: \\((\\d+)\\)$");

    /**
     * Was die Tab-Liste ueber die Besucher sagt.
     *
     * @param found ob das Widget in der Liste steht (sonst weiss man nichts und darf das Buch nicht leeren)
     * @param count die Zahl in der Kopfzeile "Visitors: (n)"
     * @param names die Namen, in der Reihenfolge der Liste
     */
    public record Result(boolean found, int count, List<String> names) {

        /** Stehen so viele Namen da, wie die Kopfzeile sagt? Nur dann darf ein fehlender Name als gegangen gelten (die Liste kann mitten im Aufbau stehen) */
        public boolean complete() {
            return found && names.size() == count;
        }
    }

    private VisitorTab() {
    }

    /** Die Zeilen der Tab-Liste (ohne Farbcodes, getrimmt) nach dem Widget der Besucher absuchen */
    public static Result read(List<String> lines) {
        List<String> names = new ArrayList<>();
        if (lines == null) return new Result(false, 0, names);
        for (int i = 0; i < lines.size(); i++) {
            String header = TabText.tidy(lines.get(i));
            Matcher m = HEADER.matcher(header);
            if (!m.matches()) continue;
            int remaining = Integer.parseInt(m.group(1));
            for (int j = i + 1; j < lines.size() && remaining > 0; j++) {
                String line = TabText.tidy(lines.get(j));
                // Eine Leerzeile gehoert nicht zur Liste, zaehlt aber nicht als Besucher
                if (line.isEmpty()) continue;
                // Ein Name hat keinen Doppelpunkt; dort beginnt das naechste Widget oder "Next Visitor:"
                if (line.indexOf(':') >= 0) break;
                names.add(line);
                remaining--;
            }
            return new Result(true, Integer.parseInt(m.group(1)), names);
        }
        return new Result(false, 0, names);
    }
}
