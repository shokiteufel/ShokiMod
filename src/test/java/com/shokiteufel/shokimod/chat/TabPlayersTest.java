package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.TabPlayers.Extra;
import com.shokiteufel.shokimod.chat.TabPlayers.Faction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 4) > Player Messages: Ironman und Fraktion aus den Zeilen der Tab-Liste. Die Beispiele sind SkyHannis {@code REGEX-TEST}-Zeilen ({@code AdvancedPlayerList}). */
class TabPlayersTest {

    @Test
    void theSampleLinesOfSkyHanni() {
        Map<String, Extra> m = TabPlayers.read(List.of("[290] Skirtwearer ꀾ♲", "[14] ColombianoGood Ⓑ", "[218] nightdives", "[281] [YOUTUBE] Remittal"), false);
        assertTrue(m.get("Skirtwearer").ironman(), "das Zeichen ♲ hinter dem Namen");
        assertFalse(m.get("ColombianoGood").ironman(), "ein anderes Zeichen (Bingo) ist kein Ironman");
        assertFalse(m.get("nightdives").ironman());
        assertFalse(m.get("Remittal").ironman(), "der Name steht hinter dem Rang in Klammern");
        assertEquals(4, m.size());
    }

    @Test
    void theSbLevelLineIsNotAPlayer() {
        Map<String, Extra> m = TabPlayers.read(List.of("SB Level: [419] 8/100 XP"), false);
        assertTrue(m.isEmpty());
    }

    @Test
    void linesWithoutALevelAreSkipped() {
        assertTrue(TabPlayers.read(List.of("Area: Hub", "Server: mini12A", "Pet: [Lvl 100] Wolf", ""), false).isEmpty());
        assertTrue(TabPlayers.read(List.of(), false).isEmpty());
        assertTrue(TabPlayers.read(null, false).isEmpty());
    }

    @Test
    void aRankInBracketsMovesTheNameOneWordOn() {
        Map<String, Extra> m = TabPlayers.read(List.of("[100] [MVP+] Carl ♲", "[100] [VIP] Dora"), false);
        assertTrue(m.get("Carl").ironman());
        assertFalse(m.get("Dora").ironman());
    }

    @Test
    void theFactionIsOnlyReadOnTheCrimsonIsle() {
        List<String> lines = List.of("[200] Bob ⚒", "[200] Mia ቾ", "[200] Neutral");
        Map<String, Extra> isle = TabPlayers.read(lines, true);
        assertEquals(Faction.BARBARIAN, isle.get("Bob").faction());
        assertEquals(Faction.MAGE, isle.get("Mia").faction());
        assertEquals(Faction.NONE, isle.get("Neutral").faction());
        Map<String, Extra> elsewhere = TabPlayers.read(lines, false);
        assertEquals(Faction.NONE, elsewhere.get("Bob").faction());
        assertEquals(Faction.NONE, elsewhere.get("Mia").faction());
    }

    @Test
    void theNameOfAChatAuthor() {
        assertEquals("lrg89", TabPlayers.nameOf("[MVP+] lrg89"));
        assertEquals("lrg89", TabPlayers.nameOf("lrg89"));
        assertEquals("lrg89", TabPlayers.nameOf("[MVP+] lrg89's"));
        assertEquals("lrg89", TabPlayers.nameOf("  lrg89 "));
    }
}
