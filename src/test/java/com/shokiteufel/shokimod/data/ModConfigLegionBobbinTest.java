package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.render.hud.SafariHud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Misc > Legion/Bobbin Overlay: von Haus aus aus, speichern und laden, eine Datei von vorher, der Kasten im HUD-Editor. */
class ModConfigLegionBobbinTest {

    @Test
    void itIsOffFromTheStartAndHidesWithoutTheEnchant() {
        ModConfig c = new ModConfig();
        assertFalse(c.misc.legionBobbin.enabled);
        assertTrue(c.misc.legionBobbin.hideWithoutEnchant, "wie bei SkyHanni");
        assertFalse(FeatureGate.legionBobbin());
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.misc.legionBobbin.enabled = true;
        c.misc.legionBobbin.hideWithoutEnchant = false;
        c.misc.legionBobbin.hudX = 0.4f;
        c.misc.legionBobbin.hudY = 0.5f;
        c.misc.legionBobbin.hudScale = 1.5f;
        c.misc.legionBobbin.hudAlpha = 0.5f;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.misc.legionBobbin.enabled);
        assertFalse(again.misc.legionBobbin.hideWithoutEnchant);
        assertEquals(0.4f, again.misc.legionBobbin.hudX);
        assertEquals(0.5f, again.misc.legionBobbin.hudY);
        assertEquals(1.5f, again.misc.legionBobbin.hudScale);
        assertEquals(0.5f, again.misc.legionBobbin.hudAlpha);
    }

    @Test
    void aFileFromBeforeGetsTheCategory() {
        ModConfig noMisc = ModConfig.GSON.fromJson("{\"misc\":null}", ModConfig.class);
        ModConfig.repairMisc(noMisc);
        assertNotNull(noMisc.misc.legionBobbin);
        ModConfig oldMisc = ModConfig.GSON.fromJson("{\"misc\":{\"npcLimit\":{}}}", ModConfig.class);
        ModConfig.repairMisc(oldMisc);
        assertNotNull(oldMisc.misc.legionBobbin);
        assertFalse(oldMisc.misc.legionBobbin.enabled, "eine alte Datei schaltet nichts ein");
        ModConfig nulled = ModConfig.GSON.fromJson("{\"misc\":{\"legionBobbin\":null}}", ModConfig.class);
        ModConfig.repairMisc(nulled);
        assertNotNull(nulled.misc.legionBobbin);
    }

    @Test
    void theSwitchTurnsOnTheLocationReader() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.misc.legionBobbin.enabled = true;
            assertTrue(FeatureGate.legionBobbin());
            assertTrue(FeatureGate.location(), "ohne den Ort bliebe das Overlay in jedem Server stumm");
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void theBoxIsABoxOfTheHudEditor() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            SafariHud.Panel panel = SafariHud.Panel.LEGION_BOBBIN;
            assertEquals("Legion/Bobbin Overlay", panel.settingsSearch());
            assertEquals(0.02f, panel.x());
            assertEquals(0.30f, panel.y());
            assertEquals(1.0f, panel.scale());
            assertEquals(1.0f, panel.alpha());
            assertFalse(panel.visible(), "ohne den Schalter steht er nie");

            panel.setPosition(0.6f, 0.2f);
            panel.setScale(2.0f);
            panel.setAlpha(0.5f);
            assertEquals(0.6f, ModConfig.INSTANCE.misc.legionBobbin.hudX);
            assertEquals(0.2f, ModConfig.INSTANCE.misc.legionBobbin.hudY);
            assertEquals(2.0f, ModConfig.INSTANCE.misc.legionBobbin.hudScale);
            assertEquals(0.5f, ModConfig.INSTANCE.misc.legionBobbin.hudAlpha);
            panel.setScale(9.0f);
            assertEquals(3.0f, panel.scale(), "die Grenze des Editors");
            panel.setAlpha(7.0f);
            assertEquals(1.0f, panel.alpha());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
