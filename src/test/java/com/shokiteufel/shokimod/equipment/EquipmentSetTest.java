package com.shokiteufel.shokimod.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Welcher Satz Equipment gilt: gewoehnlich, Rift oder Safari - und wie er in der Datei heisst. */
class EquipmentSetTest {

    @Test
    void theRiftHasItsOwnSetInBothSpellings() {
        assertEquals(EquipmentSet.RIFT, EquipmentSet.forMap("The Rift"));
        assertEquals(EquipmentSet.RIFT, EquipmentSet.forMap("Rift Dimension"));
        assertEquals(EquipmentSet.RIFT, EquipmentSet.forMap("  the rift "));
    }

    @Test
    void theSafariHasItsOwnSet() {
        assertEquals(EquipmentSet.SAFARI, EquipmentSet.forMap("Safari"));
        assertEquals(EquipmentSet.SAFARI, EquipmentSet.forMap("safari"));
    }

    @Test
    void everyOtherAreaIsTheNormalSet() {
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap("Hub"));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap("Private Island"));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap("Dungeons"));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap("Garden"));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap("Unknown"), "ein noch nicht gelesenes Gebiet");
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap(""));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap(null));
    }

    @Test
    void theAnswerFollowsTheAreaAlsoWhenItChangesBackAndForth() {
        // Die Antwort wird fuer denselben Text zwischengespeichert: Ein Wechsel muss sie trotzdem aendern
        String hub = new String("Hub");
        String rift = new String("The Rift");
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap(hub));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap(hub));
        assertEquals(EquipmentSet.RIFT, EquipmentSet.forMap(rift));
        assertEquals(EquipmentSet.RIFT, EquipmentSet.forMap(rift));
        assertEquals(EquipmentSet.NORMAL, EquipmentSet.forMap(hub));
        assertEquals(EquipmentSet.RIFT, EquipmentSet.forMap(new String("The Rift")), "ein anderes Objekt mit demselben Text");
    }

    @Test
    void theNamesInTheFileAreStableAndComeBack() {
        assertEquals("normal", EquipmentSet.NORMAL.key());
        assertEquals("rift", EquipmentSet.RIFT.key());
        assertEquals("safari", EquipmentSet.SAFARI.key());
        for (EquipmentSet set : EquipmentSet.values()) assertSame(set, EquipmentSet.byKey(set.key()));
        assertNull(EquipmentSet.byKey("weird"));
        assertNull(EquipmentSet.byKey(null));
        assertNull(EquipmentSet.byKey("NORMAL"), "die Datei schreibt klein; wer gross schreibt, hat von Hand veraendert");
    }
}
