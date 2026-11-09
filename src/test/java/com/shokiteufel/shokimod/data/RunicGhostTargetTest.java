package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Runic Ghosts der Mist: Leben wie im Mitschnitt von Schiggy (09.11.) und die Schalter. */
class RunicGhostTargetTest {

    @BeforeEach
    void frisch() {
        ModConfig.INSTANCE = new ModConfig();
    }

    @Test
    void aRunicGhostIsTheOneWithMoreThanAnAverageGhostsHealth() {
        // Mitschnitt: ein gewoehnlicher Ghost 1.000.000, die drei Runic 4.000.000
        assertTrue(RunicGhostTarget.isRunicHealth(4_000_000f));
        assertFalse(RunicGhostTarget.isRunicHealth(1_000_000f));
        assertFalse(RunicGhostTarget.isRunicHealth(250_000f), "ein angeschlagener gewoehnlicher Ghost ist keiner");
        assertFalse(RunicGhostTarget.isRunicHealth(0f));
        assertFalse(RunicGhostTarget.isRunicHealth(1_500_000f), "die Grenze selbst gehoert nicht dazu");
        assertTrue(RunicGhostTarget.isRunicHealth(1_500_001f));
    }

    @Test
    void onlyCreepersCanBeGhosts() {
        assertTrue(RunicGhostTarget.isGhostBody("entity.minecraft.creeper"));
        assertFalse(RunicGhostTarget.isGhostBody("entity.minecraft.zombie"));
        assertFalse(RunicGhostTarget.isGhostBody("entity.minecraft.armor_stand"));
        assertFalse(RunicGhostTarget.isGhostBody(""));
        assertFalse(RunicGhostTarget.isGhostBody(null));
    }

    @Test
    void everythingIsOffFromTheStart() {
        assertFalse(RunicGhostTarget.enabled());
        assertFalse(RunicGhostTarget.scanning());
        assertFalse(RunicGhostTarget.INSTANCE.highlight());
        assertFalse(RunicGhostTarget.INSTANCE.tracer());
        assertFalse(RunicGhostTarget.INSTANCE.box());
    }

    @Test
    void outsideSkyblockNothingIsScannedEvenWhenSwitchedOn() {
        ModConfig.INSTANCE.ikohS.mist.runicGhosts = true;
        assertTrue(RunicGhostTarget.enabled());
        // GameState.Server steht in der Pruefung nicht auf SkyBlock: der Durchgang durch die Mobs bleibt aus
        assertEquals(GameState.Server.isSkyblock(), RunicGhostTarget.scanning());
    }

    @Test
    void theSwitchesAreIndependentOfTheMobVisualsMasterSwitch() {
        ModConfig.INSTANCE.mobVisuals.masterEnabled = false;
        ModConfig.INSTANCE.ikohS.mist.runicGhosts = true;
        assertTrue(RunicGhostTarget.enabled());
        assertTrue(RunicGhostTarget.INSTANCE.highlight());
        assertFalse(RunicGhostTarget.INSTANCE.tracer());
        assertFalse(RunicGhostTarget.INSTANCE.box());
        ModConfig.INSTANCE.ikohS.mist.runicGhostsLine = true;
        ModConfig.INSTANCE.ikohS.mist.runicGhostsBox = true;
        assertTrue(RunicGhostTarget.INSTANCE.tracer());
        assertTrue(RunicGhostTarget.INSTANCE.box());
        assertFalse(RunicGhostTarget.INSTANCE.nameplate(), "ein Ghost hat kein Namensschild");
    }

    @Test
    void ghostsStayInvisibleUntilTheSwitchIsOn() {
        assertFalse(ModConfig.INSTANCE.ikohS.mist.showGhosts, "von Haus aus aus");
        assertFalse(RunicGhostTarget.showGhosts("entity.minecraft.creeper"));
    }

    @Test
    void theShowGhostsSwitchOnlyEverConcernsCreepersAndOnlyInSkyblock() {
        ModConfig.INSTANCE.ikohS.mist.showGhosts = true;
        assertFalse(RunicGhostTarget.showGhosts("entity.minecraft.zombie"), "kein anderes unsichtbares Wesen wird sichtbar");
        assertFalse(RunicGhostTarget.showGhosts("entity.minecraft.armor_stand"), "Ruestungsstaender sind absichtlich unsichtbar");
        assertFalse(RunicGhostTarget.showGhosts(null));
        // In der Pruefung steht GameState.Server nicht auf SkyBlock: draussen bleibt alles, wie der Server es will
        assertEquals(GameState.Server.isSkyblock(), RunicGhostTarget.showGhosts("entity.minecraft.creeper"));
    }

    @Test
    void theLabelAndColourAreSet() {
        assertEquals("Runic ghost", RunicGhostTarget.INSTANCE.label());
        assertEquals(0xAA55FF, RunicGhostTarget.INSTANCE.glowColorRGB());
    }
}
