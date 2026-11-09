package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.ModConstants;
import java.util.List;

/**
 * Gebiet, Server-ID und Spielart aus den Zeilen, die Hypixel liefert. Rein (keine Minecraft-Klassen), damit ein Mitschnitt es pruefen kann.
 *
 * <p>Hypixel legt Gebiet und Server als Anzeigenamen unsichtbarer Eintraege in die Tab-Liste ("Area: Garden", "Server: mini123A") und
 * wiederholt sie etwa jede Sekunde; die Spielart steht im Titel der Seitenleiste ("SKYBLOCK"). Der {@link LocationScanner} bekommt sie aus
 * den Paketen (Anzeigename geaendert, Seitenleiste geaendert) und tastet nur noch ab, solange keine Pakete kommen.
 */
final class Standort {

    /** Gebiet und Server-ID aus einer Liste von Zeilen; je Feld null, wenn keine passende Zeile da ist. */
    record Treffer(String gebiet, String serverId) {
    }

    private Standort() {
    }

    /** Das Gebiet, wenn die Zeile eine Area-Zeile mit Inhalt ist, sonst null. */
    static String gebiet(String zeile) {
        return hinter(zeile, ModConstants.TAB_AREA_PREFIX);
    }

    /** Die Server-ID, wenn die Zeile eine Server-Zeile mit Inhalt ist, sonst null. */
    static String serverId(String zeile) {
        return hinter(zeile, ModConstants.TAB_SERVER_PREFIX);
    }

    /** Sagt die Zeile etwas ueber den Standort? Billige Vorpruefung, ehe jemand etwas daraus macht. */
    static boolean istStandortZeile(String zeile) {
        return gebiet(zeile) != null || serverId(zeile) != null;
    }

    /** Die erste Area- und die erste Server-Zeile der Liste (wie die Abtastung bisher: die obere gewinnt). */
    static Treffer ausZeilen(List<String> zeilen) {
        String gebiet = null;
        String server = null;
        for (String zeile : zeilen) {
            if (gebiet == null) {
                gebiet = gebiet(zeile);
            }
            if (server == null) {
                server = serverId(zeile);
            }
            if (gebiet != null && server != null) {
                break;
            }
        }
        return new Treffer(gebiet, server);
    }

    /**
     * Die Spielart aus dem Titel der Seitenleiste: {@code SKYBLOCK}, sonst {@code Unknown}. Ein leerer Titel (kurz nach einem Weltwechsel
     * gibt es noch keine Seitenleiste) sagt nichts und liefert null: dann bleibt der letzte Wert, sonst flackerte die Anzeige.
     */
    static String spielart(String titel) {
        String sauber = titel == null ? "" : titel.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        if (sauber.isEmpty()) {
            return null;
        }
        return ModConstants.containsIgnoreCase(sauber, ModConstants.SIDEBAR_SKYBLOCK_TITLE) ? ModConstants.GAME_TYPE_SKYBLOCK : "Unknown";
    }

    private static String hinter(String zeile, String vorsilbe) {
        if (zeile == null) {
            return null;
        }
        String t = zeile.trim();
        if (!ModConstants.startsWithIgnoreCase(t, vorsilbe)) {
            return null;
        }
        String rest = t.substring(vorsilbe.length()).trim();
        return rest.isEmpty() ? null : rest;
    }
}
