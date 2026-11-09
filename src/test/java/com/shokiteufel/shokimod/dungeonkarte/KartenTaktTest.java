package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Karte von Hypixel wird nur gelesen, wenn ein Kartenpaket kam (wie bei NoammAddons), zur Sicherheit alle zweieinhalb Sekunden */
class KartenTaktTest {

    @Test
    void theFirstPassReadsAndTheFollowingOnesDoNotWithoutAPacket() {
        KartenTakt takt = new KartenTakt();

        assertTrue(takt.lesen(false), "beim ersten Mal wird gelesen");
        for (int i = 0; i < KartenTakt.SICHERHEIT - 1; i++) {
            assertFalse(takt.lesen(false), "ohne Paket wird nicht gelesen (Durchgang " + (i + 1) + ")");
        }
        assertTrue(takt.lesen(false), "zur Sicherheit wird nach zehn Durchgaengen doch gelesen");
        assertFalse(takt.lesen(false));
    }

    @Test
    void aMapPacketMakesTheNextPassRead() {
        KartenTakt takt = new KartenTakt();
        takt.lesen(false);
        assertFalse(takt.lesen(false));

        takt.paket();

        assertTrue(takt.lesen(false));
        assertFalse(takt.lesen(false), "ein Paket zaehlt einmal");
    }

    @Test
    void withoutThePacketLayerEveryPassReads() {
        KartenTakt takt = new KartenTakt();
        for (int i = 0; i < 5; i++) assertTrue(takt.lesen(true));
    }

    @Test
    void thePollingIsMuchRarerThanBefore() {
        KartenTakt takt = new KartenTakt();
        int gelesen = 0;
        for (int i = 0; i < 400; i++) {   // hundert Sekunden bei vier Durchgaengen je Sekunde, nur ein Paket je 20 Durchgaenge
            if (i % 20 == 0) takt.paket();
            if (takt.lesen(false)) gelesen++;
        }
        // je 20 Durchgaenge: einmal wegen des Pakets, einmal zur Sicherheit
        assertEquals(true, gelesen <= 41, "gelesen: " + gelesen + " von 400 Durchgaengen (vorher alle 400)");
    }
}
