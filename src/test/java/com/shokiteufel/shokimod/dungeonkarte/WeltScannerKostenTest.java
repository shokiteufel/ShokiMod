package com.shokiteufel.shokimod.dungeonkarte;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was der Welt-Scan der Funny Map die Welt kostet, gezaehlt an der Floor-V-Welt aus Schiggys Lauf. Der Scan laeuft viermal je Sekunde; eine
 * Hoehenabfrage ({@code hoechsterBlock}) sind im Spiel bis zu 257 Blockabfragen. Urspruenglich fragte jeder Durchgang auch nach dem Abschluss
 * noch 94 leere Plaetze neu (gemessen); dann ruhten sie (7 je Durchgang); jetzt endet der Scan wie bei NoammAddons, sobald alle Chunks da waren
 * und nichts Neues kam.
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
        final Set<Integer> fehlendeChunks = new HashSet<>();
        int hoehenFragen;

        Zaehlwelt() {
            for (int[] r : FLOOR_V) kernAnPlatz.put(r[1] * 2 * 11 + r[0] * 2, tabelle.kernVon("Default"));
        }

        private static int platz(int wx, int wz) {
            return ((wz - Raster.START) / 16) * 11 + (wx - Raster.START) / 16;
        }

        @Override
        public boolean chunkGeladen(int x, int z) {
            return !fehlendeChunks.contains(platz(x, z));
        }

        @Override
        public int hoechsterBlock(int x, int z) {
            hoehenFragen++;
            return kernAnPlatz.containsKey(platz(x, z)) ? 90 : 0;
        }

        @Override
        public int kernHash(int x, int z) {
            return kernAnPlatz.getOrDefault(platz(x, z), 0);
        }

        @Override
        public String blockName(int x, int y, int z) {
            return "minecraft:air";
        }
    }

    @Test
    void theScanIsFinishedAfterTwoQuietPassesWithAllChunksLoaded() {
        WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), new Raster());
        Zaehlwelt welt = new Zaehlwelt();

        scanner.durchgang(welt);
        assertFalse(scanner.fertig(), "der erste Durchgang findet alles, ist aber noch nicht ruhig");
        scanner.durchgang(welt);
        assertFalse(scanner.fertig(), "ein ruhiger Durchgang genuegt nicht");
        scanner.durchgang(welt);
        assertTrue(scanner.fertig(), "zwei ruhige Durchgaenge mit allen Chunks: fertig");
    }

    @Test
    void aFinishedScanDoesNotAskTheWorldAnymore() {
        WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), new Raster());
        Zaehlwelt welt = new Zaehlwelt();
        for (int i = 0; i < 4; i++) scanner.durchgang(welt);
        welt.hoehenFragen = 0;

        for (int i = 0; i < 40; i++) scanner.durchgang(welt);   // zehn Sekunden bei vier Durchgaengen je Sekunde

        System.out.println("Kosten Funny-Map-Scan (Floor V), zehn Sekunden nach dem Abschluss: " + welt.hoehenFragen
                + " Hoehenabfragen (urspruenglich 94 je Durchgang = 3760, dann 7 je Durchgang = 282)");
        assertTrue(welt.hoehenFragen == 0, "gemessen: " + welt.hoehenFragen);
    }

    @Test
    void whileAChunkIsMissingTheScanIsNotFinishedButTheEmptyPlacesRest() {
        WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), new Raster());
        Zaehlwelt welt = new Zaehlwelt();
        welt.fehlendeChunks.add(0 * 11 + 10);   // Raumeinheit (5, 0): der Chunk kommt nicht
        for (int i = 0; i < 4; i++) scanner.durchgang(welt);
        welt.hoehenFragen = 0;

        for (int i = 0; i < 40; i++) scanner.durchgang(welt);

        assertFalse(scanner.fertig(), "ein Chunk fehlt: der Scan bleibt offen");
        double jeDurchgang = welt.hoehenFragen / 40.0;
        System.out.println("Kosten Funny-Map-Scan (Floor V) mit fehlendem Chunk: im Mittel " + jeDurchgang + " Hoehenabfragen je Durchgang (vorher 94)");
        assertTrue(jeDurchgang <= 10, "je Durchgang gemessen: " + jeDurchgang);
    }

    @Test
    void aRoomThatAppearsLaterIsFoundWhileTheScanIsNotFinished() {
        Raster raster = new Raster();
        WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), raster);
        Zaehlwelt welt = new Zaehlwelt();
        welt.fehlendeChunks.add(10 * 11 + 10);   // ein anderer Chunk fehlt, der Scan ist also nicht fertig
        for (int i = 0; i < 4; i++) scanner.durchgang(welt);

        int platz = 0 * 11 + 10;   // Raumeinheit (5, 0): in Floor V leer
        assertTrue(raster.get(platz) == null);
        welt.kernAnPlatz.put(platz, welt.tabelle.kernVon("Default"));
        for (int i = 0; i < WeltScanner.RUHE + 2 && raster.get(platz) == null; i++) scanner.durchgang(welt);

        assertNotNull(raster.get(platz), "nach der Ruhezeit wird der Platz wieder gefragt");
    }
}
