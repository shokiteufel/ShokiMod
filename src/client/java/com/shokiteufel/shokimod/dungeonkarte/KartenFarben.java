package com.shokiteufel.shokimod.dungeonkarte;

import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.*;

/**
 * Die Farben der Karte (ARGB). Welche Farbe ein Raum oder eine Tuer hat, bestimmen die {@link KartenEinstellungen} - die von NoammAddons oder
 * die eigenen; ohne Angabe gelten seine Vorgaben. Noch nicht betretene Raeume werden zur Farbe "Undiscovered Room" hin gemischt, damit man
 * sieht, was schon gesehen wurde.
 */
public final class KartenFarben {

    private static final int OPAK = 0xFF000000;
    private static final KartenEinstellungen STANDARD = KartenEinstellungen.standard();

    private KartenFarben() {
    }

    private static int rgb(int r, int g, int b) {
        return OPAK | (r << 16) | (g << 8) | b;
    }

    /** Die Vorgaben von Noamm - fuer Aufrufer ohne Einstellungen */
    public static final int GESCHLOSSEN = rgb(65, 65, 65);
    public static final int WITHER_TUER = rgb(16, 16, 16);

    // ------------------------------------------------------------------ Raeume

    public static int raum(RaumTyp typ) {
        return raum(typ, STANDARD);
    }

    public static int raum(RaumTyp typ, KartenEinstellungen e) {
        return e.farbe(switch (typ) {
            case BLUT -> ROOM_BLOOD;
            case FAIRY -> ROOM_FAIRY;
            case SELTEN -> ROOM_RARE;
            case MINIBOSS -> ROOM_MINIBOSS;
            case PUZZLE -> ROOM_PUZZLE;
            case FALLE -> ROOM_TRAP;
            case NORMAL -> ROOM_NORMAL;
            case EINGANG -> ROOM_ENTRANCE;
        });
    }

    // ------------------------------------------------------------------ Tueren

    /** Die Farbe einer Tuer, ohne Rucksicht auf die Raeume daneben: Blut rot, Wither schwarz, Eingang gruen, sonst wie ein normaler Raum */
    public static int tuer(TuerTyp typ) {
        return tuer(typ, STANDARD);
    }

    public static int tuer(TuerTyp typ, KartenEinstellungen e) {
        return e.farbe(switch (typ) {
            case BLUT -> DOOR_BLOOD;
            case WITHER -> DOOR_WITHER;
            case EINGANG -> DOOR_ENTRANCE;
            case NORMAL -> DOOR_NORMAL;
        });
    }

    /** Mischt zwei Farben: {@code anteil} 0 gibt {@code a}, 1 gibt {@code b} (nur die Farbkanaele, undurchsichtig) */
    public static int mischen(int a, int b, float anteil) {
        float t = Math.max(0f, Math.min(1f, anteil));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return rgb(r, g, bl);
    }

    /**
     * Die Farbe einer Tuer im Raster. Eine normale Tuer traegt die Farbe des besonderen Raums daneben (Puzzle, Miniboss ...; ist es ein
     * Fairy-Raum und daneben noch ein anderer, der andere), sonst die der normalen Tuer. Eine schon geoeffnete Wither-Tuer hat ihre eigene
     * Farbe ("Opened Wither Door").
     *
     * @param index Platz der Tuer im Raster (Zeile mal 11 plus Spalte)
     */
    public static int tuer(Raster raster, int index, Kachel k, int dunkelProzent) {
        return tuer(raster, index, k, STANDARD, dunkelProzent);
    }

    public static int tuer(Raster raster, int index, Kachel k, KartenEinstellungen e) {
        return tuer(raster, index, k, e, Math.round(e.komma(UNDISCOVERED_TINT)));
    }

    private static int tuer(Raster raster, int index, Kachel k, KartenEinstellungen e, int dunkelProzent) {
        int farbe;
        TuerTyp typ = k.tuer == null ? TuerTyp.NORMAL : k.tuer;
        if (typ == TuerTyp.WITHER && k.offen) {
            farbe = e.farbe(DOOR_OPEN_WITHER);
        } else if (typ == TuerTyp.NORMAL) {
            int zeile = index / Raster.KANTE;
            boolean waagerecht = (zeile & 1) == 0;
            int davor = waagerecht ? index - 1 : index - Raster.KANTE;
            int dahinter = waagerecht ? index + 1 : index + Raster.KANTE;
            RaumTyp besonders = null;
            for (int n : new int[] {davor, dahinter}) {
                if (n < 0 || n >= Raster.PLAETZE) continue;
                Kachel nk = raster.get(n);
                if (nk == null || !nk.istRaumTeil() || nk.daten == null) continue;
                RaumTyp t = nk.daten.typ();
                if (t != RaumTyp.NORMAL && (besonders == null || besonders == RaumTyp.FAIRY)) besonders = t;
            }
            farbe = besonders == null ? e.farbe(DOOR_NORMAL) : raum(besonders, e);
        } else {
            farbe = tuer(typ, e);
        }
        if (k.zustand == Zustand.UNENTDECKT) farbe = mischen(farbe, e.farbe(UNDISCOVERED_COLOR), dunkelProzent / 100f);
        return farbe;
    }

    // ------------------------------------------------------------------ Plaetze

    /** Die Farbe eines Platzes: seine Art, bei noch nicht betretenen Raeumen zum Dunklen hin gemischt (Anteil in Prozent) */
    public static int kachel(Kachel k, int dunkelProzent) {
        return kachel(k, STANDARD, dunkelProzent);
    }

    public static int kachel(Kachel k, KartenEinstellungen e) {
        return kachel(k, e, Math.round(e.komma(UNDISCOVERED_TINT)));
    }

    private static int kachel(Kachel k, KartenEinstellungen e, int dunkelProzent) {
        int farbe;
        if (k.art == Kachel.Art.TUER) {
            farbe = tuer(k.tuer == null ? TuerTyp.NORMAL : k.tuer, e);
        } else if (k.zustand == Zustand.GESCHLOSSEN && k.daten != null && k.daten.istUnbekannt()) {
            farbe = e.farbe(ROOM_UNOPENED);
        } else {
            farbe = raum(k.daten == null ? RaumTyp.NORMAL : k.daten.typ(), e);
        }
        if (k.zustand == Zustand.UNENTDECKT) farbe = mischen(farbe, e.farbe(UNDISCOVERED_COLOR), dunkelProzent / 100f);
        return farbe;
    }
}
