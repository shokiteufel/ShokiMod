package com.shokiteufel.shokimod.dungeonkarte;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Raster des Dungeons: 11 x 11 Plaetze (Zeile mal 11 plus Spalte), gerade Zeilen und Spalten sind Raeume, die ungeraden Tueren,
 * Verbindungen und Mitten. Gefuellt wird es vom {@link WeltScanner} (Raeume und Tueren aus der Welt) und vom {@link KartenLeser}
 * (Zustand aus der Hypixel-Karte).
 */
public final class Raster {

    public static final int KANTE = 11;
    public static final int PLAETZE = KANTE * KANTE;
    /** Weltkoordinate der Mitte des ersten Raums (x und z) */
    public static final int START = -185;
    /** Abstand zweier Raummitten in Bloecken */
    public static final int RAUM = 32;

    private final Kachel[] kacheln = new Kachel[PLAETZE];
    /** Zaehlt jede Aenderung des Rasters; die Raumliste gilt nur fuer den Stand, zu dem sie gerechnet wurde */
    private int stand;
    private List<Raum> raeumeMerk;
    private int raeumeStand = -1;

    public Kachel get(int index) {
        return kacheln[index];
    }

    public void set(int index, Kachel kachel) {
        kacheln[index] = kachel;
        stand++;
    }

    public void leeren() {
        java.util.Arrays.fill(kacheln, null);
        stand++;
    }

    public static int weltX(int spalte) {
        return START + spalte * (RAUM / 2);
    }

    public static int weltZ(int zeile) {
        return START + zeile * (RAUM / 2);
    }

    /** Gibt es irgendetwas zu zeigen? */
    public boolean leer() {
        for (Kachel k : kacheln) if (k != null) return false;
        return true;
    }

    /** Eine Gruppe zusammenhaengender Kacheln desselben Raums */
    public record Raum(RaumDaten daten, List<Integer> plaetze, Zustand zustand) {
        /** Die Mitte der Gruppe in Rasterkoordinaten (Spalte, Zeile) - dort steht der Name oder das Haekchen */
        public float mitteSpalte() {
            float s = 0;
            for (int p : plaetze) s += p % KANTE;
            return s / plaetze.size();
        }

        public float mitteZeile() {
            float s = 0;
            for (int p : plaetze) s += p / KANTE;
            return s / plaetze.size();
        }
    }

    /**
     * Die Raeume, die das Raster enthaelt: Kacheln mit demselben Namen, die aneinander stossen, sind ein Raum (zwei getrennte Raeume
     * mit demselben Namen bleiben zwei). Unbekannte Raeume (nur die Karte kennt sie) stehen einzeln.
     */
    public List<Raum> raeume() {
        // Die Karte wird in jedem Bild gezeichnet, das Raster aendert sich nur ein paar Mal je Sekunde: die Raeume nur neu rechnen, wenn es sich aenderte
        if (raeumeMerk == null || raeumeStand != stand) {
            raeumeMerk = java.util.Collections.unmodifiableList(raeumeRechnen());
            raeumeStand = stand;
        }
        return raeumeMerk;
    }

    private List<Raum> raeumeRechnen() {
        boolean[] besucht = new boolean[PLAETZE];
        List<Raum> aus = new ArrayList<>();
        for (int i = 0; i < PLAETZE; i++) {
            Kachel k = kacheln[i];
            if (k == null || !k.istRaumTeil() || besucht[i]) continue;
            List<Integer> gruppe = new ArrayList<>();
            ArrayDeque<Integer> offen = new ArrayDeque<>();
            offen.add(i);
            besucht[i] = true;
            while (!offen.isEmpty()) {
                int p = offen.poll();
                gruppe.add(p);
                int zeile = p / KANTE;
                int spalte = p % KANTE;
                int[][] nachbarn = {{zeile - 1, spalte}, {zeile + 1, spalte}, {zeile, spalte - 1}, {zeile, spalte + 1}};
                for (int[] n : nachbarn) {
                    if (n[0] < 0 || n[0] >= KANTE || n[1] < 0 || n[1] >= KANTE) continue;
                    int q = n[0] * KANTE + n[1];
                    Kachel nk = kacheln[q];
                    if (besucht[q] || nk == null || !nk.istRaumTeil()) continue;
                    if (!gleicherRaum(k, nk)) continue;
                    besucht[q] = true;
                    offen.add(q);
                }
            }
            java.util.Collections.sort(gruppe);
            Kachel haupt = null;
            for (int p : gruppe) {
                if (kacheln[p].art == Kachel.Art.RAUM) {
                    haupt = kacheln[p];
                    break;
                }
            }
            if (haupt == null) haupt = kacheln[gruppe.get(0)];
            aus.add(new Raum(haupt.daten, gruppe, haupt.zustand));
        }
        return aus;
    }

    private static boolean gleicherRaum(Kachel a, Kachel b) {
        if (a.daten == null || b.daten == null) return false;
        if (a.daten.istUnbekannt() || b.daten.istUnbekannt()) return false;
        return a.daten.name().equals(b.daten.name());
    }

    /** Alle Geheimnisse der erkannten Raeume zusammen */
    public int geheimnisse() {
        int summe = 0;
        for (Raum r : raeume()) summe += r.daten().geheimnisse();
        return summe;
    }

    /** Wie viele Raeume erkannt sind und wie viele nur als "Unknown" auf der Karte stehen: {erkannt, unbekannt} */
    public int[] zaehleRaeume() {
        int erkannt = 0;
        int unbekannt = 0;
        for (Raum r : raeume()) {
            if (r.daten().istUnbekannt()) unbekannt++;
            else erkannt++;
        }
        return new int[] {erkannt, unbekannt};
    }

    /**
     * Der Zustand der Hypixel-Karte kommt ins Raster. Plaetze, die der Scan noch nicht kennt, werden aus der Karte angelegt; bei bekannten
     * gilt der weiter vorn stehende Zustand (siehe {@link Zustand}) - bei Puzzles immer der der Karte, denn dort ist "fehlgeschlagen"
     * ein Rueckschritt, der gelten soll.
     */
    public void vonKarte(KartenLeser.Zelle[] karte) {
        stand++;
        for (int i = 0; i < PLAETZE; i++) {
            KartenLeser.Zelle m = karte[i];
            if (m.art() == null) continue;
            int zeile = i / KANTE;
            int spalte = i % KANTE;
            Kachel k = kacheln[i];
            if (k == null) {
                Kachel neu = switch (m.art()) {
                    case RAUM -> Kachel.raum(weltX(spalte), weltZ(zeile), RaumDaten.unbekannt(m.typ()));
                    case TRENNER -> Kachel.trenner(weltX(spalte), weltZ(zeile), RaumDaten.unbekannt(m.typ()));
                    case TUER -> Kachel.tuer(weltX(spalte), weltZ(zeile), m.tuer());
                };
                neu.zustand = m.zustand();
                kacheln[i] = neu;
                continue;
            }
            boolean puzzle = k.art == Kachel.Art.RAUM && k.daten != null && k.daten.typ() == RaumTyp.PUZZLE && m.art() == Kachel.Art.RAUM;
            if (m.zustand().ordinal() < k.zustand.ordinal() || puzzle) k.zustand = m.zustand();
            if (k.art == Kachel.Art.TUER && m.art() == Kachel.Art.TUER && m.tuer() == TuerTyp.WITHER && k.tuer != TuerTyp.WITHER) {
                k.tuer = TuerTyp.WITHER;
            }
        }
    }
}
