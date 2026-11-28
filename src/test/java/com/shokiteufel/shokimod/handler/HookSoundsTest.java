package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fishing > Hook display > Quiet other sounds (28.11.2026): Solange der eigene Haken draussen ist, werden alle Toene leiser - nie der Biss-Ton, nie ein Ton der Mod
 * selbst ("die Sounds, die von ShokiMod kommen, sollen weiterhin abgespielt werden"), nie einer, den die Stummschaltung ohnehin verschluckt.
 */
class HookSoundsTest {

    @BeforeEach
    void freshConfig() {
        ModConfig.INSTANCE = new ModConfig();
    }

    @Test
    void anOrdinarySoundWhileFishingIsQuieted() {
        assertTrue(HookSounds.shouldQuiet(true, false, true, false, false));
    }

    @Test
    void theModsOwnSoundsAreNeverQuieted() {
        assertFalse(HookSounds.shouldQuiet(true, true, true, false, false), "ein eigenes Pling, ein Alarm, ein Klick der Mod");
    }

    @Test
    void theBiteSoundIsNeverQuieted() {
        assertFalse(HookSounds.shouldQuiet(true, false, true, true, false));
    }

    @Test
    void nothingIsQuietedWithoutAHookOrWithTheSwitchOff() {
        assertFalse(HookSounds.shouldQuiet(true, false, false, false, false), "kein eigener Haken draussen");
        assertFalse(HookSounds.shouldQuiet(false, false, true, false, false), "Einstellung aus");
    }

    @Test
    void aSoundTheMuteSwallowsAnywayIsLeftAlone() {
        // Die Stummschaltung erkennt das Reindrake-Geschenk an der unveraenderten Lautstaerke 0,5
        assertFalse(HookSounds.shouldQuiet(true, false, true, false, true));
    }

    @Test
    void quieterTakesThePercentOff() {
        assertEquals(0.5f, HookSounds.quieter(1.0f, 50f), 1e-6f);
        assertEquals(0.25f, HookSounds.quieter(0.5f, 50f), 1e-6f);
        assertEquals(0.3f, HookSounds.quieter(1.0f, 70f), 1e-6f);
        assertEquals(0.0f, HookSounds.quieter(0.8f, 100f), 1e-6f, "100 macht stumm");
        assertEquals(1.0f, HookSounds.quieter(1.0f, 0f), 1e-6f, "0 aendert nichts");
    }

    @Test
    void quieterTakesNonsenseInStride() {
        assertEquals(0.0f, HookSounds.quieter(1.0f, 150f), 1e-6f, "ueber 100: stumm");
        assertEquals(1.0f, HookSounds.quieter(1.0f, -20f), 1e-6f, "unter 0: unveraendert");
        assertEquals(0.7f, HookSounds.quieter(0.7f, Float.NaN), 1e-6f, "Unsinn: unveraendert");
    }

    @Test
    void itIsOffFromTheStartAndTheDefaultIsHalf() {
        ModConfig.HookDisplayCategory c = ModConfig.INSTANCE.fishing.hookDisplay;
        assertFalse(c.quietOthers);
        assertEquals(50f, c.quietOthersBy, 1e-6f);
        assertFalse(FeatureGate.hookSounds(), "ohne die Einstellung fragt kein Ton nach");
    }

    @Test
    void theMuteCanBeAskedWithoutCounting() {
        ModConfig.INSTANCE.fishing.mute.jadeDragon = true;
        SoundMute.setInDragonCaveForTest(true);
        try {
            Identifier dragon = Identifier.withDefaultNamespace("entity.ender_dragon.growl");
            int before = SoundMute.mutedCount();
            assertTrue(SoundMute.wouldCancel(dragon, 1.0f));
            assertEquals(before, SoundMute.mutedCount(), "nur gefragt, nicht verschluckt");
            assertTrue(SoundMute.shouldCancel(dragon, 1.0f));
            assertEquals(before + 1, SoundMute.mutedCount(), "verschluckt zaehlt");
            assertFalse(SoundMute.wouldCancel(Identifier.withDefaultNamespace("block.note_block.pling"), 1.0f));
        } finally {
            SoundMute.setInDragonCaveForTest(false);
        }
    }
}
