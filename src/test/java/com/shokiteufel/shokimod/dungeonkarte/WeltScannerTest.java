package com.shokiteufel.shokimod.dungeonkarte;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Welt-Scan mit dem Raster von Floor V aus Schiggys Lauf (09.11.): 27 Raumplaetze, die ihr Raum in Noamms Tabelle fanden, an den
 * Stellen, die der Nachrechnung an den echten Chunks entsprechen. Die Welt ist gebaut (Hash je Platz, Dachhoehen), die Plaetze stammen
 * aus dem echten Lauf.
 */
class WeltScannerTest {

    /** {Spalte, Zeile, Name} der erkannten Raeume, in Raumeinheiten (0 bis 5) */
    private static final Object[][] FLOOR_V = {
            {0, 0, "Three Weirdos"}, {1, 0, "Entrance"}, {2, 0, "Default"}, {3, 0, "Cathedral"}, {4, 0, "Cathedral"},
            {0, 1, "Catwalk"}, {4, 1, "Cathedral"}, {5, 1, "Tic Tac Toe"},
            {0, 2, "Staircase"}, {3, 2, "Well"}, {4, 2, "Fairy"},
            {0, 3, "Doors"}, {1, 3, "Doors"}, {2, 3, "Market"}, {3, 3, "Well"}, {4, 3, "Overgrown Chains"},
            {0, 4, "Mirror"}, {1, 4, "New Trap"}, {2, 4, "Market"}, {3, 4, "Market"}, {4, 4, "Cages"},
            {0, 5, "Slime"}, {1, 5, "Slime"}, {2, 5, "Slime"}, {3, 5, "Blood"}, {4, 5, "Dome"}, {5, 5, "Boulder"}};

    private RaumTabelle tabelle;
    private Raster raster;
    private WeltScanner scanner;
    private GebauteWelt welt;

    /** Eine Welt aus dem Layout oben: Raeume, Tueren (andere Namen nebeneinander) und Verbindungen (derselbe Name) */
    private static final class GebauteWelt implements WeltScanner.Welt {
        final Map<Integer, String> raumAnPlatz = new HashMap<>();   // Zeile*11+Spalte (Raster) -> Name
        final RaumTabelle tabelle;
        final Set<Integer> fehlendeChunks = new HashSet<>();
        final Map<Integer, String> tuerBlock = new HashMap<>();
        int unbekannterHashAn = -1;

        GebauteWelt(RaumTabelle tabelle) {
            this.tabelle = tabelle;
            for (Object[] r : FLOOR_V) raumAnPlatz.put((int) r[1] * 2 * 11 + (int) r[0] * 2, (String) r[2]);
        }

        private static int platzZ(int wz) {
            return (wz - Raster.START) / 16;
        }

        private static int platzX(int wx) {
            return (wx - Raster.START) / 16;
        }

        @Override
        public boolean chunkGeladen(int x, int z) {
            return !fehlendeChunks.contains(platzZ(z) * 11 + platzX(x));
        }

        @Override
        public int hoechsterBlock(int x, int z) {
            int zeile = platzZ(z);
            int spalte = platzX(x);
            boolean zg = (zeile & 1) == 0;
            boolean sg = (spalte & 1) == 0;
            if (zg && sg) return raumAnPlatz.containsKey(zeile * 11 + spalte) ? 90 : 0;
            if (!zg && !sg) return 0;
            String a = zg ? raumAnPlatz.get(zeile * 11 + spalte - 1) : raumAnPlatz.get((zeile - 1) * 11 + spalte);
            String b = zg ? raumAnPlatz.get(zeile * 11 + spalte + 1) : raumAnPlatz.get((zeile + 1) * 11 + spalte);
            if (a == null || b == null) return 0;
            return a.equals(b) ? 150 : 73;
        }

        @Override
        public int kernHash(int x, int z) {
            int platz = platzZ(z) * 11 + platzX(x);
            if (platz == unbekannterHashAn) return 1234567;
            return tabelle.kernVon(raumAnPlatz.get(platz));
        }

        @Override
        public String blockName(int x, int y, int z) {
            return tuerBlock.getOrDefault(platzZ(z) * 11 + platzX(x), "minecraft:air");
        }
    }

    @BeforeEach
    void aufbauen() {
        tabelle = RaumTabelle.standard();
        raster = new Raster();
        scanner = new WeltScanner(tabelle, raster);
        welt = new GebauteWelt(tabelle);
    }

    @Test
    void allTwentySevenRoomPlacesAreFoundInOnePass() {
        int neu = scanner.durchgang(welt);
        int raeume = 0;
        for (int zeile = 0; zeile < 11; zeile += 2) {
            for (int spalte = 0; spalte < 11; spalte += 2) {
                Kachel k = raster.get(zeile * 11 + spalte);
                if (k != null) {
                    raeume++;
                    assertEquals(Kachel.Art.RAUM, k.art);
                    assertEquals(welt.raumAnPlatz.get(zeile * 11 + spalte), k.daten.name());
                }
            }
        }
        assertEquals(27, raeume);
        assertTrue(neu > 27, "dazu kommen Tueren und Verbindungen: " + neu);
    }

    @Test
    void roomsOfSeveralTilesBecomeOneRoomWithTheirConnections() {
        scanner.durchgang(welt);
        List<Raster.Raum> raeume = raster.raeume();
        assertEquals(19, raeume.size());

        Raster.Raum kathedrale = raeume.stream().filter(r -> r.daten().name().equals("Cathedral")).findFirst().orElseThrow();
        // drei Kacheln und die zwei Verbindungen dazwischen
        assertEquals(5, kathedrale.plaetze().size());
        Raster.Raum slime = raeume.stream().filter(r -> r.daten().name().equals("Slime")).findFirst().orElseThrow();
        assertEquals(5, slime.plaetze().size());
        Raster.Raum market = raeume.stream().filter(r -> r.daten().name().equals("Market")).findFirst().orElseThrow();
        assertEquals(5, market.plaetze().size(), "ein L: drei Kacheln, zwei Verbindungen");
    }

    @Test
    void doorsAreDoorsAndConnectionsAreNot() {
        scanner.durchgang(welt);
        // zwischen Entrance (1,0) und Default (2,0): andere Raeume nebeneinander, also eine Tuer
        Kachel tuer = raster.get(0 * 11 + 3);
        assertNotNull(tuer);
        assertEquals(Kachel.Art.TUER, tuer.art);
        assertEquals(TuerTyp.NORMAL, tuer.tuer);
        // zwischen den beiden Slime-Kacheln (0,5) und (1,5): derselbe Raum, eine Verbindung
        Kachel verbindung = raster.get(10 * 11 + 1);
        assertNotNull(verbindung);
        assertEquals(Kachel.Art.TRENNER, verbindung.art);
        assertEquals("Slime", verbindung.daten.name());
    }

    @Test
    void theDoorBlockDecidesWitherAndBloodDoors() {
        // Platz zwischen Entrance und Default (Spalte 3, Zeile 0)
        welt.tuerBlock.put(0 * 11 + 3, "minecraft:coal_block");
        // Platz zwischen Blood (3,5) und Dome (4,5) (Spalte 7, Zeile 10)
        welt.tuerBlock.put(10 * 11 + 7, "minecraft:red_terracotta");
        scanner.durchgang(welt);
        assertEquals(TuerTyp.WITHER, raster.get(3).tuer);
        assertEquals(TuerTyp.BLUT, raster.get(10 * 11 + 7).tuer);
    }

    @Test
    void aSecondPassFindsNothingNew() {
        scanner.durchgang(welt);
        assertEquals(0, scanner.durchgang(welt));
    }

    @Test
    void aMissingChunkIsRetriedAndFlagsThatNotEverythingIsLoaded() {
        int platz = 4 * 11 + 6;   // Raumplatz (Spalte 3, Zeile 2): "Well"
        welt.fehlendeChunks.add(platz);
        scanner.durchgang(welt);
        assertNull(raster.get(platz));
        assertFalse(scanner.alleChunksGeladen());

        welt.fehlendeChunks.clear();
        scanner.durchgang(welt);
        assertNotNull(raster.get(platz));
        assertTrue(scanner.alleChunksGeladen());
    }

    @Test
    void aRoomWhoseHashTheTableDoesNotKnowStaysOpenForTheMapToFillIn() {
        int platz = 0;   // Three Weirdos
        welt.unbekannterHashAn = platz;
        scanner.durchgang(welt);
        assertNull(raster.get(platz));
    }

    @Test
    void theMapFillsInStateAndWhatTheScanDoesNotKnow() throws IOException {
        scanner.durchgang(welt);
        byte[] karte;
        try (InputStream in = WeltScannerTest.class.getResourceAsStream("/dungeonkarte/karte_mitte.bin")) {
            karte = in.readAllBytes();
        }
        raster.vonKarte(KartenLeser.lesen(karte, KartenLeser.kalibriere(karte, null)));

        assertEquals(Zustand.ENTDECKT, raster.get(0 * 11 + 2).zustand, "der Eingang ist auf der Karte entdeckt");
        assertEquals(Zustand.UNENTDECKT, raster.get(10 * 11 + 10).zustand, "Boulder steht noch nicht auf der Karte");

        // Die Karte zeigt einen Raum bei (Spalte 1, Zeile 1), den die Tabelle nicht kannte: er kommt als "Unknown" dazu
        Kachel unbekannt = raster.get(2 * 11 + 2);
        assertNotNull(unbekannt);
        assertTrue(unbekannt.daten.istUnbekannt());
        assertEquals(RaumTyp.NORMAL, unbekannt.daten.typ());
        // der entdeckte Eingang bleibt ein erkannter Raum
        assertEquals("Entrance", raster.get(0 * 11 + 2).daten.name());
    }

    @Test
    void theSecretsOfAllRecognisedRoomsAreAdded() {
        scanner.durchgang(welt);
        int erwartet = 0;
        Set<String> gezaehlt = new HashSet<>();
        for (Object[] r : FLOOR_V) {
            String name = (String) r[2];
            if (gezaehlt.add(name)) erwartet += tabelle.nachName(name).geheimnisse();
        }
        assertEquals(erwartet, raster.geheimnisse());
        assertEquals(19, raster.zaehleRaeume()[0]);
        assertEquals(0, raster.zaehleRaeume()[1]);
    }
}
