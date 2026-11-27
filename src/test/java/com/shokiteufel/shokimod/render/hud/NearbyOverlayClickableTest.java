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

    // ---- seit 2.6.4: Klicks nimmt nur an, wo die Kaesten auch gezeichnet werden

    @Test
    void theBoxesAreDrawnOverContainerWindowsAndTheChat() {
        assertTrue(NearbyOverlay.panelsDrawnOver(net.minecraft.client.gui.screens.inventory.InventoryScreen.class), "eigenes Inventar");
        assertTrue(NearbyOverlay.panelsDrawnOver(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class), "jeder Behaelter (Besucher, Basar ...)");
        assertTrue(NearbyOverlay.panelsDrawnOver(net.minecraft.client.gui.screens.ChatScreen.class), "Chat");
    }

    @Test
    void noBoxIsDrawnOverTheWindowsOfTheModOrAnyOtherScreen() {
        // Das Einstellungsfenster (/shoki): Die Einkaufsliste der Besucher stand dort unsichtbar und schluckte Klicks auf die Reiter
        assertFalse(NearbyOverlay.panelsDrawnOver(io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent.class), "Einstellungsfenster");
        assertFalse(NearbyOverlay.panelsDrawnOver(com.shokiteufel.shokimod.gui.HudEditorScreen.class), "HUD-Editor zeichnet selbst");
        assertFalse(NearbyOverlay.panelsDrawnOver(com.shokiteufel.shokimod.gui.EffectsScreen.class), "ein weiteres Fenster der Mod");
        assertFalse(NearbyOverlay.panelsDrawnOver(net.minecraft.client.gui.screens.Screen.class), "jedes andere Fenster");
        assertFalse(NearbyOverlay.panelsDrawnOver((Class<?>) null), "kein Fenster: es gibt keinen Klick");
    }
}
