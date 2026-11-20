package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Meldung des Users (20.11.): "die Rarity von den Items sieht falsch aus - bei Gold Ingot ist es Rot und Divine-Farbe, aber es ist glaube ich
 * grau und gruen". Hypixels Item-Liste fuehrt GOLD_INGOT ohne Seltenheit (gewoehnlich) und ENCHANTED_GOLD (Enchanted Gold Ingot) als UNCOMMON.
 */
class ProfitTrackerFarbeTest {

    private static final int WEISS = 0xFFFFFFFF;
    private static final int GRUEN = 0xFF55FF55;
    private static final int BLAU = 0xFF5555FF;
    private static final int LILA = 0xFFAA00AA;
    private static final int GOLD = 0xFFFFAA00;
    private static final int HELLLILA = 0xFFFF55FF;
    private static final int CYAN = 0xFF55FFFF;
    private static final int ROT = 0xFFFF5555;

    @Test
    void theListBeatsAColourSeenInAMenu() {
        // Enchanted Gold Ingot: UNCOMMON in der Liste, in einem Menue rot gesehen -> gruen
        assertEquals(GRUEN, ProfitTracker.farbeAus(true, "UNCOMMON", ROT, ROT));
        assertEquals(GRUEN, ProfitTracker.farbeAus(true, "UNCOMMON", null, ROT));
        // Gold Ingot: in der Liste ohne Seltenheit, in einem Menue cyan gesehen -> weiss
        assertEquals(WEISS, ProfitTracker.farbeAus(true, null, CYAN, CYAN));
        assertEquals(WEISS, ProfitTracker.farbeAus(true, null, null, CYAN));
    }

    @Test
    void everyRarityGetsItsOwnColour() {
        assertEquals(WEISS, ProfitTracker.farbeAus(true, "COMMON", null, 0));
        assertEquals(GRUEN, ProfitTracker.farbeAus(true, "UNCOMMON", null, 0));
        assertEquals(BLAU, ProfitTracker.farbeAus(true, "RARE", null, 0));
        assertEquals(LILA, ProfitTracker.farbeAus(true, "EPIC", null, 0));
        assertEquals(GOLD, ProfitTracker.farbeAus(true, "LEGENDARY", null, 0));
        assertEquals(HELLLILA, ProfitTracker.farbeAus(true, "MYTHIC", null, 0));
        assertEquals(ROT, ProfitTracker.farbeAus(true, "SPECIAL", null, 0));
    }

    @Test
    void whatTheListDoesNotKnowKeepsItsOwnColour() {
        // Shards, Buecher, Runen: nicht in der Liste. Die gemerkte Farbe geht vor der gesehenen
        assertEquals(BLAU, ProfitTracker.farbeAus(false, null, BLAU, GRUEN));
        assertEquals(GRUEN, ProfitTracker.farbeAus(false, null, null, GRUEN));
        assertEquals(WEISS, ProfitTracker.farbeAus(false, null, null, 0), "niemand weiss etwas: weiss");
        assertEquals(WEISS, ProfitTracker.farbeAus(false, null, 0, 0), "eine gespeicherte Null zaehlt nicht");
    }

    @Test
    void aTierThatIsNoColourWordSaysNothing() {
        // UNOBTAINABLE hat keine Farbe in der Zuordnung: dann entscheidet, was sonst bekannt ist
        assertEquals(BLAU, ProfitTracker.farbeAus(true, "UNOBTAINABLE", BLAU, 0));
        assertEquals(WEISS, ProfitTracker.farbeAus(true, "UNOBTAINABLE", null, 0));
    }

    @Test
    void thetierIsReadWithoutRegardToCase() {
        assertEquals(GRUEN, ProfitTracker.farbeAus(true, "uncommon", null, 0));
    }
}
