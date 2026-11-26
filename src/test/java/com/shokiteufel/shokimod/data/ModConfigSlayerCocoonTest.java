package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.ModConfig.SlayerCategory.SpawnParticleMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slayer > Cocoon und Spawn particles (2.4.11): von Haus aus aus, speichern und laden, beschaedigte Dateien. */
class ModConfigSlayerCocoonTest {

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        ModConfig.INSTANCE = c;
        assertFalse(c.slayer.cocoonTitle);
        assertFalse(c.slayer.cocoonDing);
        assertEquals(SpawnParticleMode.AS_IS, c.slayer.spawnWhite);
        assertEquals(SpawnParticleMode.AS_IS, c.slayer.spawnPurple);
        assertEquals(SpawnParticleMode.AS_IS, c.slayer.spawnSlayer);
        assertFalse(FeatureGate.slayerCocoon());
        assertFalse(FeatureGate.slayerSpawnParticles());
    }

    @Test
    void theGatesFollowTheSwitches() {
        ModConfig c = new ModConfig();
        ModConfig.INSTANCE = c;
        c.slayer.cocoonDing = true;
        assertTrue(FeatureGate.slayerCocoon());
        c.slayer.cocoonDing = false;
        c.slayer.spawnPurple = SpawnParticleMode.HIDE;
        assertTrue(FeatureGate.slayerSpawnParticles());
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.slayer.cocoonTitle = true;
        c.slayer.spawnWhite = SpawnParticleMode.HIDE;
        c.slayer.spawnSlayer = SpawnParticleMode.SHOW_ALWAYS;
        c.slayer.cocoonSoundFile = "ding.wav";
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.slayer.cocoonTitle);
        assertEquals(SpawnParticleMode.HIDE, again.slayer.spawnWhite);
        assertEquals(SpawnParticleMode.AS_IS, again.slayer.spawnPurple);
        assertEquals(SpawnParticleMode.SHOW_ALWAYS, again.slayer.spawnSlayer);
        assertEquals("ding.wav", again.slayer.cocoonSoundFile);
    }

    @Test
    void damagedValuesAreStraightened() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"slayer\":{\"cocoonBannerDesign\":null,\"cocoonSoundFile\":null,"
                + "\"spawnWhite\":null,\"spawnPurple\":\"NOT_A_MODE\",\"spawnSlayer\":\"HIDE\"}}", ModConfig.class);
        ModConfig.repairSlayerCocoon(loaded.slayer);
        assertEquals(BannerDesign.ALERT_BAND, loaded.slayer.cocoonBannerDesign);
        assertEquals("", loaded.slayer.cocoonSoundFile);
        assertEquals(SpawnParticleMode.AS_IS, loaded.slayer.spawnWhite);
        assertEquals(SpawnParticleMode.AS_IS, loaded.slayer.spawnPurple, "ein unbekannter Name wird zu 'as is'");
        assertEquals(SpawnParticleMode.HIDE, loaded.slayer.spawnSlayer);
    }
}
