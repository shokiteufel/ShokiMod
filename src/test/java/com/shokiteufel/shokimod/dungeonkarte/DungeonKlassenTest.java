package com.shokiteufel.shokimod.dungeonkarte;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Klasse eines Spielers aus der Tab-Liste des Dungeons (Format nach NoammAddons, Vorgabefarben seiner ClassColors). */
class DungeonKlassenTest {

    @Test
    void theClassComesFromTheBracketAtTheEndOfTheTabLine() {
        Map<String, Integer> m = DungeonKlassen.lesen(List.of(
                "[420] [MVP+] TestPlayer (Mage XXV)",
                "[390] Nils_rs (Healer XX)",
                "[12] [VIP] Archy (Archer L)",
                "[88] Bers (Berserk XLIX)",
                "[1] Tanky (Tank XV)"));
        assertEquals(0x00AAAA, m.get("TestPlayer"));
        assertEquals(0xAA00AA, m.get("Nils_rs"));
        assertEquals(0xAA0000, m.get("Archy"));
        assertEquals(0xFFAA00, m.get("Bers"));
        assertEquals(0x00AA00, m.get("Tanky"));
    }

    @Test
    void linesWithoutAClassAreLeftOut() {
        Map<String, Integer> m = DungeonKlassen.lesen(List.of("[420] [MVP+] Someone (EMPTY)", "Dungeon: Catacombs", "", "Party (4)"));
        assertTrue(m.isEmpty(), () -> "gesehen: " + m);
        assertNull(DungeonKlassen.farbeDerKlasse("Wizard"));
        assertNull(DungeonKlassen.farbeDerKlasse(null));
    }

    @Test
    void noTabListIsNoClass() {
        assertTrue(DungeonKlassen.lesen(null).isEmpty());
    }
}
