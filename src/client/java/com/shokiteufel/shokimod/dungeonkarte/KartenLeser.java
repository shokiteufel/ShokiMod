package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Liest die Karte, die Hypixel dem Spieler in den Hotbar-Platz 9 legt (128 x 128 Farbnummern): welcher Raum wie weit ist.
 * Hypixel zeigt dort nur, was ein Spieler der Gruppe schon gesehen hat - die Raeume selbst kennt die Funny Map aus dem Welt-Scan,
 * von der Karte kommt der Zustand (entdeckt, gesperrt, geraeumt, fertig, fehlgeschlagen).
 *
 * Aufbau der Karte: 6 x 6 Raeume zu je 16 oder 18 Pixeln, dazwischen Tueren von 4 Pixeln. Wo sie beginnt, haengt vom Stockwerk ab; die
 * Eingangsreihe (Farbe 30, mindestens 16 Pixel am Stueck) legt Grosse und Anfang fest. Die Rechnung folgt NoammAddons (CC0).
 */
public final class KartenLeser {

    public static final int BREITE = 128;
    private static final int EINGANG = 30;

    private KartenLeser() {
    }

    /** Anfang des Rasters in der Karte (Pixel) und Kantenlaenge eines Raums (16 oder 18) */
    public record Kalibrierung(int startX, int startY, int raumGroesse) {
        int halbRaum() {
            return raumGroesse / 2;
        }

        /** Abstand von Mitte zu Mitte zweier benachbarter Rasterplaetze */
        int halbKachel() {
            return halbRaum() + 2;
        }
    }

    /** Was an einem Rasterplatz der Karte steht. {@code art} ist null, wo die Karte nichts zeigt */
    public record Zelle(Kachel.Art art, RaumTyp typ, TuerTyp tuer, Zustand zustand) {
        static final Zelle LEER = new Zelle(null, null, null, Zustand.UNENTDECKT);
    }

    /**
     * Die Lage des Rasters in der Karte.
     *
     * @param stockwerk 0 (Eingang) bis 7, oder null, wenn unbekannt
     * @return null, solange die Eingangsreihe noch nicht zu sehen ist
     */
    public static Kalibrierung kalibriere(byte[] farben, Integer stockwerk) {
        if (farben == null || farben.length < BREITE * BREITE) return null;
        int laenge = 0;
        int start = 0;
        int gefunden = -1;
        int gefundenStart = 0;
        for (int i = 0; i < BREITE * BREITE; i++) {
            if (farben[i] == EINGANG) {
                if (laenge == 0) start = i;
                laenge++;
            } else {
                if (laenge >= 16) {
                    gefunden = laenge;
                    gefundenStart = start;
                    break;
                }
                laenge = 0;
            }
        }
        if (gefunden < 0) {
            gefunden = laenge;
            gefundenStart = start;
        }
        if (gefunden != 16 && gefunden != 18) return null;

        int groesse = gefunden;
        int sx;
        int sy;
        if (stockwerk != null && stockwerk == 0) {
            sx = 22;
            sy = 22;
        } else if (stockwerk != null && stockwerk == 1) {
            sx = 22;
            sy = 11;
        } else if (stockwerk != null && (stockwerk == 2 || stockwerk == 3)) {
            sx = 11;
            sy = 11;
        } else {
            sx = (gefundenStart & 127) % (groesse + 4);
            sy = (gefundenStart >> 7) % (groesse + 4);
        }
        return new Kalibrierung(sx, sy, groesse);
    }

    /** Alle 121 Rasterplaetze (Zeile mal 11 plus Spalte) aus der Karte */
    public static Zelle[] lesen(byte[] farben, Kalibrierung k) {
        Zelle[] aus = new Zelle[121];
        java.util.Arrays.fill(aus, Zelle.LEER);
        int startX = k.startX() + k.halbRaum();
        int startY = k.startY() + k.halbRaum();
        for (int x = 0; x <= 10; x++) {
            for (int y = 0; y <= 10; y++) {
                int mapX = startX + x * k.halbKachel();
                int mapY = startY + y * k.halbKachel();
                if (mapX >= BREITE || mapY >= BREITE) continue;
                int mitte = farben[mapY * BREITE + mapX];
                if (mitte == 0) continue;
                int seite;
                if (x % 2 == 0 && y % 2 == 0) {
                    seite = at(farben, (mapY - k.halbRaum()) * BREITE + (mapX - k.halbRaum()));
                } else if (y % 2 == 1) {
                    seite = at(farben, mapY * BREITE + mapX - 4);
                } else {
                    seite = at(farben, (mapY - 4) * BREITE + mapX);
                }
                aus[y * 11 + x] = zelle(x, y, mitte, seite);
            }
        }
        return aus;
    }

    private static int at(byte[] farben, int index) {
        return index >= 0 && index < farben.length ? farben[index] : 0;
    }

    private static Zelle zelle(int x, int y, int mitte, int seite) {
        if (x % 2 == 0 && y % 2 == 0) {
            RaumTyp typ = RaumTyp.vonKartenfarbe(seite);
            if (typ == null) return Zelle.LEER;
            Zustand z = switch (mitte) {
                case 18 -> typ == RaumTyp.BLUT ? Zustand.ENTDECKT : typ == RaumTyp.PUZZLE ? Zustand.FEHLGESCHLAGEN : Zustand.UNENTDECKT;
                case 30 -> typ == RaumTyp.EINGANG ? Zustand.ENTDECKT : Zustand.GRUEN;
                case 34 -> Zustand.GERAEUMT;
                case 85, 119 -> Zustand.GESCHLOSSEN;
                default -> Zustand.ENTDECKT;
            };
            return new Zelle(Kachel.Art.RAUM, typ, null, z);
        }
        if (seite == 0) {
            TuerTyp tuer = TuerTyp.vonKartenfarbe(mitte);
            if (tuer == null) return Zelle.LEER;
            return new Zelle(Kachel.Art.TUER, null, tuer, mitte == 85 ? Zustand.GESCHLOSSEN : Zustand.ENTDECKT);
        }
        RaumTyp typ = RaumTyp.vonKartenfarbe(seite);
        if (typ == null) return Zelle.LEER;
        return new Zelle(Kachel.Art.TRENNER, typ, null, Zustand.ENTDECKT);
    }
}
