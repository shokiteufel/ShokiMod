package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): die Zeit ueber dem Angelhaken gross auf dem Bildschirm, wie bei SkyHanni ("Fishing Hook Display").
 *
 * Die Regel von dort: Der Name des Ruestungsstaenders am Haken ist entweder gelb und fett eine Zahl ("3.0") oder ein "!!!" in beliebiger
 * Farbe. Alles andere (Schadenszahlen, Mob-Namen) ist es nicht.
 */
class FishingHookDisplayTest {

    @Test
    void aYellowBoldNumberIsTheCountdown() {
        FishingHookDisplay.Reading r = FishingHookDisplay.read("§e§l3.0");
        assertNotNull(r);
        assertFalse(r.alert());
        assertEquals(3.0, r.seconds(), 1e-9);
        assertEquals(1.2, FishingHookDisplay.read("§e§l1.2").seconds(), 1e-9);
        assertEquals(12.0, FishingHookDisplay.read("§e§l12").seconds(), 1e-9, "auch ganze Zahlen");
    }

    @Test
    void theBangsAreTheAlertInAnyColour() {
        assertTrue(FishingHookDisplay.read("§c§l!!!").alert());
        assertTrue(FishingHookDisplay.read("!!!").alert());
        assertTrue(FishingHookDisplay.read("§a!!!").alert());
    }

    @Test
    void otherNamesNextToTheHookAreNotTheTimer() {
        assertNull(FishingHookDisplay.read("§736"), "eine Schadenszahl in grau");
        assertNull(FishingHookDisplay.read("§772"));
        assertNull(FishingHookDisplay.read("3.0"), "ohne gelb und fett");
        assertNull(FishingHookDisplay.read("§e3.0"), "gelb, aber nicht fett");
        assertNull(FishingHookDisplay.read("§c§lYeti"));
        assertNull(FishingHookDisplay.read("§e§l3.0 s"));
        assertNull(FishingHookDisplay.read(""));
        assertNull(FishingHookDisplay.read(null));
    }

    @Test
    void aResetSignInFrontIsAllowed() {
        assertEquals(2.5, FishingHookDisplay.read("§r§e§l2.5").seconds(), 1e-9);
    }

    @Test
    void theAlertIsReplacedByTheCustomTextWithAmpersandColours() {
        FishingHookDisplay.Reading alert = FishingHookDisplay.read("§c§l!!!");
        assertEquals("§a§lNOW", FishingHookDisplay.textFor(alert, 0f, "&a&lNOW"));
    }

    @Test
    void theNumberStaysHypixelsOwnUntilTheCustomTime() {
        FishingHookDisplay.Reading three = FishingHookDisplay.read("§e§l3.0");
        FishingHookDisplay.Reading half = FishingHookDisplay.read("§e§l0.4");
        FishingHookDisplay.Reading zero = FishingHookDisplay.read("§e§l0.0");
        assertEquals("§e§l3.0", FishingHookDisplay.textFor(three, 0.5f, "&c&l!!!"));
        assertEquals("§e§l0.4", FishingHookDisplay.textFor(half, 0.3f, "&c&l!!!"), "noch zu frueh");
        assertEquals("§c§l!!!", FishingHookDisplay.textFor(half, 0.4f, "&c&l!!!"), "genau die eingestellte Zeit zaehlt schon");
        assertEquals("§c§l!!!", FishingHookDisplay.textFor(half, 1.0f, "&c&l!!!"));
        assertEquals("§c§l!!!", FishingHookDisplay.textFor(zero, 0f, "&c&l!!!"), "bei Null, auch ohne Vorlauf");
    }

    @Test
    void anEmptyCustomAlertFallsBackToHypixelsText() {
        FishingHookDisplay.Reading alert = FishingHookDisplay.read("§c§l!!!");
        assertEquals("§c§l!!!", FishingHookDisplay.textFor(alert, 0f, ""));
        assertEquals("§c§l!!!", FishingHookDisplay.textFor(alert, 0f, null));
    }

    @Test
    void noReadingNoText() {
        assertNull(FishingHookDisplay.textFor(null, 0f, "&c&l!!!"));
    }

    // ------------------------------------------------------------------ Alarm aus dem Biss-Ton (Fishing log vom 28.11.)

    @Test
    void theBiteSoundIsTheNoteBlockPlingAtNormalPitchAndFullVolume() {
        // Vier von vier Bissen im Log: minecraft:block.note_block.pling, pitch 1.00, vol 1.00
        assertTrue(FishingHookDisplay.isBiteSound("minecraft", "block.note_block.pling", 1.00f, 1.00f));
        assertTrue(FishingHookDisplay.isBiteSound("minecraft", "block.note_block.pling", 1.004f, 0.996f), "Rundung");
    }

    @Test
    void otherPlingsAreNotTheBite() {
        // Die Tonfolge nach dem Einholen im Log: dasselbe Pling, aber pitch 0.59 bis 1.17 und vol 0.4 / 0.8
        assertFalse(FishingHookDisplay.isBiteSound("minecraft", "block.note_block.pling", 0.59f, 0.80f));
        assertFalse(FishingHookDisplay.isBiteSound("minecraft", "block.note_block.pling", 1.17f, 0.40f));
        assertFalse(FishingHookDisplay.isBiteSound("minecraft", "block.note_block.pling", 1.00f, 0.80f), "richtige Tonhoehe, falsche Lautstaerke");
        assertFalse(FishingHookDisplay.isBiteSound("minecraft", "block.note_block.bell", 1.00f, 1.00f));
        assertFalse(FishingHookDisplay.isBiteSound("rfu", "block.note_block.pling", 1.00f, 1.00f), "ein anderer Namensraum");
        assertFalse(FishingHookDisplay.isBiteSound("minecraft", "entity.fishing_bobber.splash", 1.00f, 1.00f));
        assertFalse(FishingHookDisplay.isBiteSound(null, null, 1.00f, 1.00f));
    }

    @Test
    void theHeardBiteMakesTheAlertEvenIfTheStandShowsNothingOrStillTheTime() {
        FishingHookDisplay.Reading time = FishingHookDisplay.read("§e§l0.1");
        FishingHookDisplay.Reading alert = FishingHookDisplay.withSoundAlert(null, true);
        assertNotNull(alert);
        assertTrue(alert.alert(), "kein lesbarer Staender: trotzdem der Alarm");
        assertTrue(FishingHookDisplay.withSoundAlert(time, true).alert(), "der Staender zeigt noch die Zeit: der Alarm gewinnt");
        assertEquals("§c§l!!!", FishingHookDisplay.textFor(alert, 0f, ""), "ohne eigenen Text das rote !!!");
        assertEquals("§a§lNOW", FishingHookDisplay.textFor(alert, 0f, "&a&lNOW"), "der eigene Alarmtext der Einstellung");
    }

    @Test
    void withoutTheBiteNothingChanges() {
        FishingHookDisplay.Reading time = FishingHookDisplay.read("§e§l1.4");
        assertEquals(time, FishingHookDisplay.withSoundAlert(time, false));
        assertNull(FishingHookDisplay.withSoundAlert(null, false), "kein Staender, kein Ton: das Overlay bleibt leer");
        FishingHookDisplay.Reading alert = FishingHookDisplay.read("§c§l!!!");
        assertEquals(alert, FishingHookDisplay.withSoundAlert(alert, true), "der Staender zeigt den Alarm schon: unveraendert");
    }

    @Test
    void theSoundAlertIsOffFromTheStart() {
        com.shokiteufel.shokimod.data.ModConfig.INSTANCE = new com.shokiteufel.shokimod.data.ModConfig();
        assertFalse(com.shokiteufel.shokimod.data.ModConfig.INSTANCE.fishing.hookDisplay.soundAlert);
    }
}
