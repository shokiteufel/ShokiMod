package com.shokiteufel.shokimod.dungeonkarte;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Geheimnisse des Raums, in dem man steht: Hypixel zeigt sie in der Aktionsleiste im Dungeon als "0/1 Secrets" ("§70/1 Secrets" in grau,
 * Mitschnitt 09.11., 17:05). Erste Zahl: schon gefunden, zweite: im Raum. Nur dort steht, wie viele man schon hat - die Karte der Raeume
 * kennt nur die Gesamtzahl.
 */
public final class SecretsLeiste {
    private SecretsLeiste() {
    }

    /** Gefunden und im Raum insgesamt */
    public record Stand(int gefunden, int gesamt) {
    }

    /** Nicht der Schwanz einer laengeren Zahl: davor steht "127/127", die Gesundheit-Zahlen derselben Leiste */
    private static final Pattern SECRETS = Pattern.compile("(?<![\\d,])(\\d+)\\s*/\\s*(\\d+)\\s+Secrets?\\b");

    /** Den Stand aus dem Text der Aktionsleiste (ohne Farbcodes) lesen; null, wenn die Leiste keinen nennt */
    public static Stand lesen(String leiste) {
        if (leiste == null) {
            return null;
        }
        Matcher m = SECRETS.matcher(leiste);
        if (!m.find()) {
            return null;
        }
        try {
            int gefunden = Integer.parseInt(m.group(1));
            int gesamt = Integer.parseInt(m.group(2));
            return gesamt > 0 && gefunden >= 0 && gefunden <= gesamt ? new Stand(gefunden, gesamt) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Auf welchem Platz des Rasters (Zeile * 11 + Spalte, immer eine Raummitte: gerade Zeile, gerade Spalte) liegt eine Weltstelle? -1 ausserhalb
     * des Rasters (Boss-Raum, Vorraum). Der erste Raum ist um {@link Raster#START} zentriert, ein Raum ist {@link Raster#RAUM} Bloecke gross.
     */
    public static int platz(double weltX, double weltZ) {
        int spalte = raum(weltX);
        int zeile = raum(weltZ);
        if (spalte < 0 || zeile < 0) {
            return -1;
        }
        return (2 * zeile) * Raster.KANTE + 2 * spalte;
    }

    /** Der Raum (0 bis 5) einer Weltkoordinate in einer Richtung, -1 ausserhalb */
    static int raum(double welt) {
        int r = (int) Math.floor((welt - Raster.START + Raster.RAUM / 2.0) / Raster.RAUM);
        return r >= 0 && r <= (Raster.KANTE - 1) / 2 ? r : -1;
    }
}
