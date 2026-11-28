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
    void theHookAloneQuietsWithItsOwnAmount() {
        assertEquals(50f, HookSounds.percentFor(true, 50f, false, 100f), 1e-6f, "Haken draussen, Aura gerade nicht aktiv: nur der Anteil der Hook display");
        assertEquals(70f, HookSounds.percentFor(true, 70f, false, 100f), 1e-6f);
    }

    @Test
    void theAuraAloneQuietsWithItsOwnAmountAndWithoutAHook() {
        // Wunsch des Users (28.11.): in der Frozen-Blaze-Kategorie, unabhaengig von der anderen - zwischen den Wuerfen gibt es keinen Haken
        assertEquals(100f, HookSounds.percentFor(false, 50f, true, 100f), 1e-6f, "kein Haken, die Aura wirkt: nur der Anteil von Frozen Blaze");
        assertEquals(60f, HookSounds.percentFor(false, 50f, true, 60f), 1e-6f);
    }

    @Test
    void whenBothApplyTheStrongerAmountCounts() {
        assertEquals(100f, HookSounds.percentFor(true, 50f, true, 100f), 1e-6f);
        assertEquals(80f, HookSounds.percentFor(true, 80f, true, 60f), 1e-6f);
    }

    @Test
    void withNeitherTheSoundStaysAsItIs() {
        assertTrue(HookSounds.percentFor(false, 50f, false, 100f) < 0f, "weder Haken noch Aura");
        assertFalse(HookSounds.shouldQuiet(true, false, HookSounds.percentFor(false, 50f, false, 100f) >= 0f, false, false));
    }

    @Test
    void anAmountThatIsNonsenseIsLeftOutOrTrimmed() {
        assertTrue(HookSounds.percentFor(true, Float.NaN, false, 100f) < 0f, "Unsinn: nichts");
        assertEquals(60f, HookSounds.percentFor(true, Float.NaN, true, 60f), 1e-6f, "der andere gilt trotzdem");
        assertEquals(100f, HookSounds.percentFor(true, 250f, false, 0f), 1e-6f, "ueber 100: auf 100");
        assertEquals(0f, HookSounds.percentFor(true, -5f, false, 0f), 1e-6f, "unter 0: auf 0 (aendert nichts)");
    }

    @Test
    void theAuraActsOnlyWithTheFullSetAndTimeOnTheClock() {
        assertTrue(FrozenBlaze.auraActive(true, 12_000L));
        assertTrue(FrozenBlaze.auraActive(true, 1L), "bis zur letzten Millisekunde");
        assertFalse(FrozenBlaze.auraActive(true, 0L), "Zeit abgelaufen");
        assertFalse(FrozenBlaze.auraActive(false, 12_000L), "ohne das ganze Set gibt es keine Aura");
    }

    @Test
    void theFrozenBlazeOptionIsOffFromTheStartAndFullAtOneHundred() {
        ModConfig.FrozenBlazeCategory c = ModConfig.INSTANCE.fishing.frozenBlaze;
        assertFalse(c.quietSounds);
        assertEquals(100f, c.quietSoundsBy, 1e-6f, "Vorgabe: ganz aus ('dass sie aus sind')");
        assertFalse(FeatureGate.auraSounds());
        assertFalse(FeatureGate.frozenBlaze(), "ohne Einstellung wird das Set nicht gesucht");
    }

    @Test
    void theFrozenBlazeOptionIsIndependentOfTheHookDisplayOne() {
        ModConfig.INSTANCE.fishing.frozenBlaze.quietSounds = true;
        // Die Hook display bleibt aus: die Aura-Einstellung wirkt trotzdem, und sie schaltet die andere nicht ein
        assertTrue(FeatureGate.auraSounds());
        assertFalse(FeatureGate.hookSounds(), "Quiet other sounds bei der Hook display bleibt aus");
        assertTrue(FeatureGate.frozenBlaze(), "die Set-Erkennung und die Aura-Uhr laufen jetzt");
        assertFalse(ModConfig.INSTANCE.fishing.hookDisplay.quietOthers);
    }

    @Test
    void theHookDisplayOptionNeedsNothingFromFrozenBlaze() {
        ModConfig.INSTANCE.fishing.hookDisplay.quietOthers = true;
        assertFalse(FeatureGate.auraSounds());
        assertFalse(ModConfig.INSTANCE.fishing.frozenBlaze.quietSounds);
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
