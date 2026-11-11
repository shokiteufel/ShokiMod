package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Welche Effekte in der Liste unter "God Potion" stehen: Name und Stufe wie im Wiki, mit zwei Ausnahmen */
class EffectsTrackerGodPotionTest {

    @Test
    void theWikiEffectsWithTheirLevelAreGodPotionEffects() {
        assertTrue(EffectsTracker.isGodPotion("Critical IV"));
        assertTrue(EffectsTracker.isGodPotion("Regeneration IX"));
        assertTrue(EffectsTracker.isGodPotion("Alchemy XP Boost III"));
        assertTrue(EffectsTracker.isGodPotion("  speed   VIII "), "Schreibweise und Leerzeichen zaehlen nicht");
    }

    @Test
    void theSameNameWithAnotherLevelIsAnOrdinaryPotion() {
        assertFalse(EffectsTracker.isGodPotion("Speed VI"));
        assertFalse(EffectsTracker.isGodPotion("Strength VII"));
    }

    @Test
    void jerryCandyCountsWithAnyLevelBecauseTheWikiWritesItWithoutOne() {
        assertTrue(EffectsTracker.isGodPotion("Jerry Candy I"), "so steht es im Spiel");
        assertTrue(EffectsTracker.isGodPotion("Jerry Candy"));
        assertTrue(EffectsTracker.isGodPotion("jerry candy ii"));
    }

    @Test
    void healingNineIsAGodPotionEffectLikeRegenerationNine() {
        assertTrue(EffectsTracker.isGodPotion("Healing IX"));
        assertFalse(EffectsTracker.isGodPotion("Healing V"), "nur mit der Stufe der God Potion");
    }

    @Test
    void aNameThatOnlyStartsLikeJerryCandyIsNotOne() {
        assertFalse(EffectsTracker.isGodPotion("Jerry Candy Jar I"));
        assertFalse(EffectsTracker.isGodPotion("Candy I"));
        assertFalse(EffectsTracker.isGodPotion(null));
    }
}
