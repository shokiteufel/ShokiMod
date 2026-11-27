package com.shokiteufel.shokimod.render.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wann nimmt ein Fenster Klicks auf die Kaesten an? Im eigenen Inventar jeder Kasten, in jedem anderen Fenster nur der Jagd-Knopf und - seit 2.5.11 - die
 * Einkaufsliste der Besucher (Auftrag des Users: auf den Gegenstand klicken, damit die Basar-Suche gleich aufgeht; sie steht auch vor dem Besucher-Fenster
 * und dem Basar, und dort ging der Klick bisher ins Leere).
 */
class NearbyOverlayClickableTest {

    @Test
    void theVisitorListTakesClicksInAnyWindowItStandsIn() {
        // Nicht das eigene Inventar (Chat, Besucher, Basar), nur die Liste steht
        assertTrue(NearbyOverlay.anyClickable(false, false, false, false, false, false, true, false));
    }

    @Test
    void inAForeignWindowNoOtherBoxTakesClicks() {
        // Wie bisher: Profit, Sea Tracker, Skill XP, Plots und Nearby gehoeren in einem fremden Fenster dem Fenster
        assertFalse(NearbyOverlay.anyClickable(false, false, true, true, true, true, false, true));
        assertFalse(NearbyOverlay.anyClickable(false, false, false, false, false, false, false, false));
    }

    @Test
    void theHuntingButtonStillWorksEverywhere() {
        assertTrue(NearbyOverlay.anyClickable(false, true, false, false, false, false, false, false));
        assertTrue(NearbyOverlay.anyClickable(true, true, false, false, false, false, false, false));
    }

    @Test
    void inTheOwnInventoryEveryBoxThatStandsCounts() {
        assertTrue(NearbyOverlay.anyClickable(true, false, true, false, false, false, false, false));
        assertTrue(NearbyOverlay.anyClickable(true, false, false, true, false, false, false, false));
        assertTrue(NearbyOverlay.anyClickable(true, false, false, false, true, false, false, false));
        assertTrue(NearbyOverlay.anyClickable(true, false, false, false, false, true, false, false));
        assertTrue(NearbyOverlay.anyClickable(true, false, false, false, false, false, true, false));
        assertTrue(NearbyOverlay.anyClickable(true, false, false, false, false, false, false, true));
        assertFalse(NearbyOverlay.anyClickable(true, false, false, false, false, false, false, false), "ohne Kasten nichts");
    }
}
