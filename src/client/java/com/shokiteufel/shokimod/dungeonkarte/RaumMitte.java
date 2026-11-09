package com.shokiteufel.shokimod.dungeonkarte;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wo in einem Raum das Haekchen oder der Text steht - nach NoammAddons ({@code UniqueRoom.addTile}, {@code getCheckmarkPosition}): in der Mitte
 * des Raums ("Center Checkmark/Text" an) oder in seiner oberen linken Ecke (aus). Gerechnet wird mit den Raummitten im 11 x 11 grossen Raster
 * (gerade Zeile, gerade Spalte); die Verbindungen innerhalb eines grossen Raums zaehlen nicht.
 *
 * Reine Rechnung, ohne Minecraft. Das Ergebnis ist {Spalte, Zeile} im Raster, bei Noamm "checkPos" (Spalte zuerst).
 */
public final class RaumMitte {

    private RaumMitte() {
    }

    /** Nur die Raummitten (gerade Zeile und Spalte) der Plaetze eines Raums, als {Zeile, Spalte}, in der Reihenfolge der Plaetze */
    static List<int[]> zellen(List<Integer> plaetze) {
        List<int[]> aus = new ArrayList<>();
        for (int p : plaetze) {
            int zeile = p / Raster.KANTE;
            int spalte = p % Raster.KANTE;
            if ((zeile & 1) == 0 && (spalte & 1) == 0) {
                aus.add(new int[] {zeile, spalte});
            }
        }
        return aus;
    }

    /** Die obere linke Zelle: die kleinste Spalte, bei gleicher die kleinste Zeile */
    public static int[] linksOben(List<Integer> plaetze) {
        List<int[]> z = zellen(plaetze);
        if (z.isEmpty()) {
            int p = plaetze.get(0);
            return new int[] {p % Raster.KANTE, p / Raster.KANTE};
        }
        int[] beste = z.get(0);
        for (int[] c : z) {
            if (c[1] < beste[1] || (c[1] == beste[1] && c[0] < beste[0])) {
                beste = c;
            }
        }
        return new int[] {beste[1], beste[0]};
    }

    /**
     * Die Mitte, wie Noamm sie rechnet: Zeilen und Spalten werden nach der Zahl ihrer Zellen gruppiert und die groesste Gruppe massgebend.
     * Hat die groesste Zeilengruppe keinen Gleichstand, ist die Zeile die der groessten Gruppe und die Spalte der gerundete Mittelwert aller
     * Spalten; sonst entscheiden die Spalten, und zuletzt der Mittelwert der beiden groessten Gruppen.
     */
    public static int[] mitte(List<Integer> plaetze) {
        List<int[]> z = zellen(plaetze);
        if (z.isEmpty()) {
            return linksOben(plaetze);
        }
        // groupBy bewahrt die Reihenfolge des ersten Auftretens, sortedByDescending ist stabil
        Map<Integer, Integer> spalten = new LinkedHashMap<>();
        Map<Integer, Integer> zeilen = new LinkedHashMap<>();
        for (int[] c : z) {
            spalten.merge(c[1], 1, Integer::sum);
            zeilen.merge(c[0], 1, Integer::sum);
        }
        List<Map.Entry<Integer, Integer>> xRooms = nachGroesse(spalten);
        List<Map.Entry<Integer, Integer>> zRooms = nachGroesse(zeilen);

        if (z.size() == 1) {
            return new int[] {xRooms.get(0).getKey(), zRooms.get(0).getKey()};
        }
        if (zRooms.size() == 1 || !zRooms.get(0).getValue().equals(zRooms.get(1).getValue())) {
            int summe = 0;
            for (Map.Entry<Integer, Integer> e : xRooms) summe += e.getKey();
            return new int[] {summe / xRooms.size(), zRooms.get(0).getKey()};
        }
        if (xRooms.size() == 1 || !xRooms.get(0).getValue().equals(xRooms.get(1).getValue())) {
            int summe = 0;
            for (Map.Entry<Integer, Integer> e : zRooms) summe += e.getKey();
            return new int[] {xRooms.get(0).getKey(), summe / zRooms.size()};
        }
        return new int[] {(xRooms.get(0).getKey() + xRooms.get(1).getKey()) / 2, (zRooms.get(0).getKey() + zRooms.get(1).getKey()) / 2};
    }

    private static List<Map.Entry<Integer, Integer>> nachGroesse(Map<Integer, Integer> gruppen) {
        List<Map.Entry<Integer, Integer>> l = new ArrayList<>(gruppen.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return l;
    }

    /** Wie {@link #mitte} oder {@link #linksOben}, je nach Einstellung */
    public static int[] stelle(List<Integer> plaetze, boolean mittig) {
        return mittig ? mitte(plaetze) : linksOben(plaetze);
    }
}
