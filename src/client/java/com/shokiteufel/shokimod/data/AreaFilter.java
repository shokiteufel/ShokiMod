package com.shokiteufel.shokimod.data;

import java.util.List;

/**
 * Wann ein Gebiet in einer Auswahl steht - die Regel hinter "Areas" im HUD-Editor (je Kasten) und hinter "Map"
 * im Effects-Fenster (je Effekt). Eine Regel fuer beide, damit sie sich nicht auseinanderentwickeln.
 */
public final class AreaFilter {

    private AreaFilter() {
    }

    /**
     * Sagt die Auswahl ja zu diesem Gebiet?
     *
     * @return {@code null}, wenn nichts ausgewaehlt ist (leere oder fehlende Liste) - dann gilt die Vorgabe;
     *         sonst ob das Gebiet in der Liste steht. Gross- und Kleinschreibung zaehlt nicht. Ist das
     *         Gebiet noch unbekannt, gilt bei vorhandener Auswahl "nein"
     */
    public static Boolean allows(List<String> chosen, String area) {
        if (chosen == null || chosen.isEmpty()) return null;
        if (area == null || area.isBlank()) return false;
        for (String entry : chosen) {
            if (entry != null && entry.equalsIgnoreCase(area)) return true;
        }
        return false;
    }
}
