package com.shokiteufel.shokimod.handler;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wo steht man? Die Zone steht in der Seitenleiste, vor ihr eines von vier Zeichen (wie bei Feesh). */
class SoundMuteZoneTest {

    @Test
    void theZoneWithThePrivateUseSymbolOfTheMarshIsFound() {
        // Aufnahme Schiggy 09.11.: Praefix " §7 §2Murkwater", Suffix "§2 Loch" - die Seitenleiste fuegt beides zusammen
        assertEquals("Murkwater Loch", SoundMute.zoneOf(List.of("§7Moonglade", " §7 §2Murkwater§2 Loch")));
    }

    @Test
    void bothZonesOfTheCaveAreRecognisedWithAnyOfTheFourSymbols() {
        for (String symbol : List.of("⏣", "ф", "", "")) {
            assertEquals(SoundMute.DRAGON_LAIR, SoundMute.zoneOf(List.of("§7" + symbol + " §bDragon's Lair")), "Zeichen " + Integer.toHexString(symbol.charAt(0)));
            assertEquals(SoundMute.MURKWATER_DEPTHS, SoundMute.zoneOf(List.of(symbol + " §2Murkwater Depths")));
        }
    }

    @Test
    void picturesBetweenTheLettersAreCutOut() {
        assertEquals("Abandoned Quarry", SoundMute.zoneOf(List.of("§7⏣ §cAbandoned🐍 Quarry")));
    }

    @Test
    void linesWithoutASymbolAreNoZone() {
        assertNull(SoundMute.zoneOf(List.of("Purse: 1,234", "Bits: 5", "")));
        assertFalse(SoundMute.hasZoneSymbol("Dragon's Lair"));
        assertTrue(SoundMute.hasZoneSymbol(" Dragon's Lair"));
    }
}
