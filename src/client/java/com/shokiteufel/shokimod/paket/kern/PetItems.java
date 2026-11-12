package com.shokiteufel.shokimod.paket.kern;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Welche Waren sind Pet Items (Tier Boost, Textbook, Lucky Clover ...)? Der Kern braucht das fuer den Tausch: Ein Pet Item bekommt das Pet
 * durch Rechtsklick auf das Pet in der Welt und wird dabei verbraucht; das alte kommt zurueck ins Inventar, ohne Chatzeile, ohne Klick und
 * ohne Mob-Tod davor - es sah aus wie ein Fund und stand im Profit-Tracker.
 *
 * <p>Erkannt wird ein Pet Item an der Kennung ({@code PET_ITEM_...}) oder an der letzten Zeile der Beschreibung ("LEGENDARY PET ITEM"). Viele
 * Pet Items tragen die Vorsilbe nicht (BIGGER_TEETH, REINFORCED_SCALES ...), darum lernt der Anschluss die Kennung aus der Beschreibung,
 * sobald er die Ware zum ersten Mal sieht. Keine Minecraft-Typen: der Anschluss reicht nur die Zeile.
 */
public final class PetItems {
    private static final Set<String> GELERNT = ConcurrentHashMap.newKeySet();
    /** Kennungen, deren Beschreibung schon gelesen wurde und kein Pet Item nannte: nicht bei jedem Paket noch einmal lesen */
    private static final Set<String> GEPRUEFT = ConcurrentHashMap.newKeySet();
    private static final int GEPRUEFT_MAX = 4096;

    private PetItems() {
    }

    /** Ist die Ware ein Pet Item? */
    public static boolean ist(String id) {
        return id != null && (id.startsWith("PET_ITEM_") || GELERNT.contains(id));
    }

    /** Muss die Beschreibung dieser Ware noch gelesen werden? Nein, wenn sie schon als Pet Item gilt oder schon geprueft wurde */
    public static boolean lesenNoetig(String id) {
        return id != null && !id.startsWith("PET_ITEM_") && !GELERNT.contains(id) && !GEPRUEFT.contains(id);
    }

    /**
     * Die letzte Zeile der Beschreibung einer Ware: "LEGENDARY PET ITEM" macht sie zum Pet Item. Zeilen mit Farbcodes sind erlaubt.
     * Ein Pet selbst ("LEGENDARY PET") und alles andere zaehlt nicht.
     */
    public static void pruefen(String id, String letzteZeile) {
        if (id == null) {
            return;
        }
        if (beschriftung(letzteZeile)) {
            GELERNT.add(id);
            return;
        }
        if (GEPRUEFT.size() >= GEPRUEFT_MAX) {
            GEPRUEFT.clear();
        }
        GEPRUEFT.add(id);
    }

    /** Heisst die Zeile "<SELTENHEIT> PET ITEM"? */
    static boolean beschriftung(String zeile) {
        if (zeile == null) {
            return false;
        }
        String s = zeile.replaceAll("(?i)§[0-9A-FK-OR]", "").trim().toUpperCase(Locale.ROOT);
        return s.endsWith(" PET ITEM");
    }

    /** Fuer Tests: alles Gelernte vergessen */
    static void vergessen() {
        GELERNT.clear();
        GEPRUEFT.clear();
    }
}
