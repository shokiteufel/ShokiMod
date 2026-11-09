package com.shokiteufel.shokimod.dungeonkarte;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaumTabelleTest {

    @Test
    void theBundledTableHasAllRoomsAndHashes() {
        RaumTabelle t = RaumTabelle.standard();
        assertEquals(140, t.anzahlRaeume());
        assertEquals(240, t.anzahlKerne());
    }

    @Test
    void aKnownHashFindsItsRoom() {
        RaumTabelle t = RaumTabelle.standard();
        RaumDaten admin = t.nachKern(-908785306);
        assertNotNull(admin);
        assertEquals("Admin", admin.name());
        assertEquals(RaumTyp.NORMAL, admin.typ());
        assertEquals(1, admin.kacheln());
        assertEquals(34, admin.crypts());

        RaumDaten criss = t.nachKern(210009410);
        assertEquals("Criss Cross", criss.name());
        assertEquals(1, criss.geheimnisse());
        assertEquals(6, criss.crypts());
    }

    @Test
    void anUnknownHashFindsNothing() {
        assertNull(RaumTabelle.standard().nachKern(123456789));
    }

    @Test
    void aRoomOfSeveralTilesHasOneHashPerTile() {
        RaumTabelle t = RaumTabelle.standard();
        RaumDaten altar = t.nachName("Altar");
        assertNotNull(altar);
        assertEquals(3, altar.kacheln(), "Altar ist ein L aus drei Kacheln");
        assertEquals(3, altar.kerne().length);
        for (int kern : altar.kerne()) assertEquals("Altar", t.nachKern(kern).name());
    }

    @Test
    void theSpecialRoomsHaveTheirTypes() {
        RaumTabelle t = RaumTabelle.standard();
        assertEquals(RaumTyp.BLUT, t.nachName("Blood").typ());
        assertEquals(RaumTyp.EINGANG, t.nachName("Entrance").typ());
        assertEquals(RaumTyp.FAIRY, t.nachName("Fairy").typ());
        assertEquals(RaumTyp.FALLE, t.nachName("New Trap").typ());
        assertEquals(RaumTyp.PUZZLE, t.nachName("Tic Tac Toe").typ());
        assertEquals(RaumTyp.MINIBOSS, t.nachName("Default").typ());
    }

    @Test
    void theShapesCountTheirTiles() {
        assertEquals(1, RaumTabelle.kacheln("1x1"));
        assertEquals(2, RaumTabelle.kacheln("1x2"));
        assertEquals(3, RaumTabelle.kacheln("1x3"));
        assertEquals(3, RaumTabelle.kacheln("L"));
        assertEquals(4, RaumTabelle.kacheln("1x4"));
        assertEquals(4, RaumTabelle.kacheln("2x2"));
        assertEquals(0, RaumTabelle.kacheln("Unknown"));
        assertEquals(0, RaumTabelle.kacheln(null));
    }

    @Test
    void aTableFromTextWorksAndSurvivesMissingFields() {
        RaumTabelle t = RaumTabelle.laden(new StringReader(
                "[{\"name\":\"A\",\"type\":\"RARE\",\"shape\":\"1x2\",\"cores\":[1,2],\"secrets\":3},{\"name\":\"B\",\"cores\":[7]}]"));
        assertEquals(2, t.anzahlRaeume());
        assertEquals(3, t.nachKern(2).geheimnisse());
        assertEquals(RaumTyp.SELTEN, t.nachKern(1).typ());
        assertEquals(RaumTyp.NORMAL, t.nachKern(7).typ(), "ohne Typ gilt normal");
        assertEquals(0, t.nachKern(7).kacheln());
        assertFalse(t.nachKern(7).istUnbekannt(), "ein Eintrag mit Namen ist nicht 'Unknown'");
    }

    @Test
    void anUnknownRoomOnlyKnowsItsType() {
        RaumDaten u = RaumDaten.unbekannt(RaumTyp.PUZZLE);
        assertTrue(u.istUnbekannt());
        assertEquals(RaumTyp.PUZZLE, u.typ());
    }
}
