package com.shokiteufel.shokimod.dungeonkarte;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was ein Durchgang der Funny Map die Welt kostet, gezaehlt an der Floor-V-Welt aus Schiggys Lauf. Der Scan laeuft viermal je Sekunde
 * (alle fuenf Ticks); eine Hoehenabfrage ({@code hoechsterBlock}) sind im Spiel bis zu 257 Blockabfragen. Vor der Verbesserung fragte jeder
 * Durchgang auch nach dem Abschluss noch 94 leere Plaetze neu (gemessen); jetzt ruhen sie, solange nichts Neues kommt.
 */
class WeltScannerKostenTest {

    /** {Spalte, Zeile} der erkannten Raeume in Raumeinheiten, wie in WeltScannerTest (Floor V) */
    private static final int[][] FLOOR_V = {
            {0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {0, 1}, {4, 1}, {5, 1}, {0, 2}, {3, 2}, {4, 2},
            {0, 3}, {1, 3}, {2, 3}, {3, 3}, {4, 3}, {0, 4}, {1, 4}, {2, 4}, {3, 4}, {4, 4}, {0, 5}, {1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5}};

    /** Eine Welt, die nur zaehlt, was gefragt wird; die Raeume sind echt (Hash aus der Tabelle), der Rest ist leer */
    private static final class Zaehlwelt implements WeltScanner.Welt {
        final RaumTabelle tabelle = RaumTabelle.standard();
        final Map<Integer, Integer> kernAnPlatz = new HashMap<>();
        int hoehenFragen;
        int hashFragen;

        Zaehlwelt() {
            for (int[] r : FLOOR_V) kernAnPlatz.put(r[1] * 2 * 11 + r[0] * 2, tabelle.kernVon("Default"));
        }

        private static int platz(int wx, int wz) {
            return ((wz - Raster.START) / 16) * 11 + (wx - Raster.START) / 16;
        }

        @Override
        public boolean chunkGeladen(int x, int z) {
            return true;
        }

        @Override
        public int hoechsterBlock(int x, int z) {
            hoehenFragen++;
            return kernAnPlatz.containsKey(platz(x, z)) ? 90 : 0;
        }

        @Override
        public int kernHash(int x, int z) {
            hashFragen++;
            return kernAnPlatz.getOrDefault(platz(x, z), 0);
        }

        @Override
        public String blockName(int x, int y, int z) {
            return "minecraft:air";
        }
    }

    @Test
    void aSettledMapDoesNotRescanTheEmptyColumnsEveryPass() {
        Raster raster = new Raster();
        WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), raster);
        Zaehlwelt welt = new Zaehlwelt();

        scanner.durchgang(welt);
        scanner.durchgang(welt);   // der zweite findet nichts Neues: ab hier ruhen die leeren Plaetze
        welt.hoehenFragen = 0;
        welt.hashFragen = 0;
        for (int i = 0; i < 40; i++) scanner.durchgang(welt);   // zehn Sekunden bei vier Durchgaengen je Sekunde

        double jeDurchgang = welt.hoehenFragen / 40.0;
        System.out.println("Kosten Funny-Map-Scan (Floor V): vorher 94 Hoehenabfragen je Durchgang, jetzt im Mittel " + jeDurchgang
                + " (je bis zu 257 Blockabfragen); Hash-Abfragen je Durchgang " + welt.hashFragen / 40.0);
        assertTrue(jeDurchgang <= 10, "je Durchgang gemessen: " + jeDurchgang + " Hoehenabfragen");
    }

    @Test
    void aRoomThatAppearsLaterIsStillFoundAfterTheRestPeriod() {
        Raster raster = new Raster();
        WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), raster);
        Zaehlwelt welt = new Zaehlwelt();
        scanner.durchgang(welt);
        scanner.durchgang(welt);
        scanner.durchgang(welt);

        int platz = 0 * 11 + 10;   // Raumeinheit (5, 0): in Floor V leer
        assertTrue(raster.get(platz) == null);
        welt.kernAnPlatz.put(platz, welt.tabelle.kernVon("Default"));
        for (int i = 0; i < WeltScanner.RUHE + 2 && raster.get(platz) == null; i++) scanner.durchgang(welt);

        assertNotNull(raster.get(platz), "nach der Ruhezeit wird der Platz wieder gefragt");
    }
}
