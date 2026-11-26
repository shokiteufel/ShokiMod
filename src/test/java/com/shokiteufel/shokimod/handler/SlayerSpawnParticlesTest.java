package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SlayerCategory.SpawnParticleMode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): Die Spawn-Partikel des Slayers sind einstellbar und lassen sich trotz Sodium Extra immer zeigen. Die Regel von SkyHanni
 * ("Spawn Particles"): drei Arten (enchant, witch, entity_effect), nur bei laufender Quest und nur nahe einem Mob, der eben gestorben ist.
 */
class SlayerSpawnParticlesTest {

    private static final long T0 = 1_000_000L;

    @BeforeEach
    void frisch() {
        SlayerSpawnParticles.resetForTest();
    }

    @Test
    void theThreeKindsAreSkyHannisWhitePurpleAndSlayerSpecific() {
        assertEquals(SlayerSpawnParticles.Kind.WHITE, SlayerSpawnParticles.kindOf("enchant"));
        assertEquals(SlayerSpawnParticles.Kind.PURPLE, SlayerSpawnParticles.kindOf("witch"));
        assertEquals(SlayerSpawnParticles.Kind.SLAYER, SlayerSpawnParticles.kindOf("entity_effect"));
        assertNull(SlayerSpawnParticles.kindOf("flame"));
        assertNull(SlayerSpawnParticles.kindOf("dust"));
        assertNull(SlayerSpawnParticles.kindOf(null));
    }

    @Test
    void theModeDecidesWhatHappens() {
        assertEquals(ParticleHider.Action.NONE, SlayerSpawnParticles.actionFor(SpawnParticleMode.AS_IS));
        assertEquals(ParticleHider.Action.HIDE, SlayerSpawnParticles.actionFor(SpawnParticleMode.HIDE));
        assertEquals(ParticleHider.Action.FORCE, SlayerSpawnParticles.actionFor(SpawnParticleMode.SHOW_ALWAYS),
                "Show always: auch gegen Sodium Extra");
    }

    @Test
    void eachKindHasItsOwnSetting() {
        ModConfig.SlayerCategory c = new ModConfig().slayer;
        c.spawnWhite = SpawnParticleMode.HIDE;
        c.spawnPurple = SpawnParticleMode.SHOW_ALWAYS;
        assertEquals(SpawnParticleMode.HIDE, SlayerSpawnParticles.modeOf(SlayerSpawnParticles.Kind.WHITE, c));
        assertEquals(SpawnParticleMode.SHOW_ALWAYS, SlayerSpawnParticles.modeOf(SlayerSpawnParticles.Kind.PURPLE, c));
        assertEquals(SpawnParticleMode.AS_IS, SlayerSpawnParticles.modeOf(SlayerSpawnParticles.Kind.SLAYER, c));
        assertTrue(c.anySpawnParticle());
    }

    @Test
    void aParticleCloseToARecentDeathBelongsToIt() {
        SlayerSpawnParticles.recordDeath(10, 64, 10, T0);
        assertTrue(SlayerSpawnParticles.nearRecentDeath(10.5, 64.5, 10.5, T0 + 100));
        assertTrue(SlayerSpawnParticles.nearRecentDeath(11.5, 64, 10, T0 + 100), "gut anderthalb Bloecke");
    }

    @Test
    void aParticleFartherAwayDoesNot() {
        SlayerSpawnParticles.recordDeath(10, 64, 10, T0);
        assertFalse(SlayerSpawnParticles.nearRecentDeath(13, 64, 10, T0 + 100), "drei Bloecke sind zu weit (SkyHanni: unter fuenf im Quadrat)");
        assertFalse(SlayerSpawnParticles.nearRecentDeath(10, 70, 10, T0 + 100));
    }

    @Test
    void aDeathIsForgottenAfterThreeSeconds() {
        SlayerSpawnParticles.recordDeath(10, 64, 10, T0);
        assertTrue(SlayerSpawnParticles.nearRecentDeath(10, 64, 10, T0 + 3_000));
        assertFalse(SlayerSpawnParticles.nearRecentDeath(10, 64, 10, T0 + 3_001));
    }

    @Test
    void noDeathNoMatch() {
        assertFalse(SlayerSpawnParticles.nearRecentDeath(0, 0, 0, T0));
    }

    @Test
    void theRingKeepsTheNewestThirtyTwo() {
        for (int i = 0; i < 40; i++) SlayerSpawnParticles.recordDeath(1000.0 * i, 0, 0, T0);
        assertFalse(SlayerSpawnParticles.nearRecentDeath(0, 0, 0, T0 + 10), "der aelteste wurde ueberschrieben");
        assertTrue(SlayerSpawnParticles.nearRecentDeath(39_000, 0, 0, T0 + 10));
    }

    @Test
    void theQuestIsTheSidebarLineSlayerQuest() {
        assertTrue(SlayerSpawnParticles.questIn(List.of("SKYBLOCK", "Slayer Quest", "Tarantula Broodfather IV", "Slay the boss!")));
        assertTrue(SlayerSpawnParticles.questIn(List.of("§cSlayer Quest")), "mit Farbcode");
        assertFalse(SlayerSpawnParticles.questIn(List.of("SKYBLOCK", "Purse: 1,000", "Objective")));
        assertFalse(SlayerSpawnParticles.questIn(List.of()));
    }

    @Test
    void theModesAreNamedForTheMenu() {
        assertEquals("As is", SpawnParticleMode.AS_IS.toString());
        assertEquals("Hide", SpawnParticleMode.HIDE.toString());
        assertEquals("Show always", SpawnParticleMode.SHOW_ALWAYS.toString());
    }
}
