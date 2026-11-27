package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.garden.VisitorDecision;
import com.shokiteufel.shokimod.garden.VisitorOffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Was im Fenster eines Besuchers gezeichnet wird, und der Name der Umgehungstaste. Seit 2.5.16 liegt das graue Quadrat des gesperrten Knopfes nicht mehr ueber der Beschreibung
 * (es wird beim Platz gezeichnet, nach dem Stueck und vor der Beschreibung), und die Umgehungstaste heisst "Left Control" statt "key.keyboard.341".
 */
class VisitorWindowPaintTest {

    private static final int ACCEPT = VisitorOffer.ACCEPT_SLOT;
    private static final int REFUSE = VisitorOffer.REFUSE_SLOT;

    // ---- was gezeichnet wird ----

    @Test
    void whenRefusingIsBlockedRefuseIsDimmedAndAcceptGetsTheOutline() {
        VisitorDecision.BlockReason reason = VisitorDecision.BlockReason.RARE_REWARD;
        assertEquals(VisitorWindow.Paint.DIM, VisitorWindow.paintFor(reason, REFUSE, false, true));
        assertEquals(VisitorWindow.Paint.OUTLINE, VisitorWindow.paintFor(reason, ACCEPT, false, true));
    }

    @Test
    void whenAcceptingIsBlockedAcceptIsDimmedAndRefuseGetsTheOutline() {
        VisitorDecision.BlockReason reason = VisitorDecision.BlockReason.EXPENSIVE_COPPER;
        assertEquals(VisitorWindow.Paint.DIM, VisitorWindow.paintFor(reason, ACCEPT, false, true));
        assertEquals(VisitorWindow.Paint.OUTLINE, VisitorWindow.paintFor(reason, REFUSE, false, true));
    }

    @Test
    void theBypassKeyTakesTheDimmingAwayButNotTheOutline() {
        VisitorDecision.BlockReason reason = VisitorDecision.BlockReason.RARE_REWARD;
        assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(reason, REFUSE, true, true), "mit gehaltener Umgehungstaste sieht der Knopf gewoehnlich aus");
        assertEquals(VisitorWindow.Paint.OUTLINE, VisitorWindow.paintFor(reason, ACCEPT, true, true));
    }

    @Test
    void theOutlineOptionSwitchesTheOutlineOff() {
        VisitorDecision.BlockReason reason = VisitorDecision.BlockReason.RARE_REWARD;
        assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(reason, ACCEPT, false, false));
        assertEquals(VisitorWindow.Paint.DIM, VisitorWindow.paintFor(reason, REFUSE, false, false), "das Abdunkeln bleibt");
    }

    @Test
    void noReasonAndOtherSlotsPaintNothing() {
        assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(null, REFUSE, false, true));
        assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(null, ACCEPT, false, true));
        for (VisitorDecision.BlockReason reason : VisitorDecision.BlockReason.values()) {
            assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(reason, 0, false, true), "ein anderer Platz: " + reason);
            assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(reason, VisitorOffer.INFO_SLOT, false, true), "das Schild: " + reason);
            assertEquals(VisitorWindow.Paint.NONE, VisitorWindow.paintFor(reason, -1, false, true));
        }
    }

    // ---- der Name der Taste ----

    @Test
    void theRawKeyOfTheScreenshotBecomesLeftControl() {
        assertEquals("Left Control", ShortcutHandler.readableKeyName(341, "key.keyboard.341"));
        assertEquals("Left Control", ShortcutHandler.readableKeyName(341, null));
        assertEquals("Left Control", ShortcutHandler.readableKeyName(341, ""));
        assertEquals("Right Control", ShortcutHandler.readableKeyName(345, "key.keyboard.right.control"));
    }

    @Test
    void aNameTheGameKnowsIsKept() {
        assertEquals("Left Control", ShortcutHandler.readableKeyName(341, "Left Control"));
        assertEquals("Strg links", ShortcutHandler.readableKeyName(341, "Strg links"), "die Sprache des Spiels gilt, wenn sie den Namen kennt");
        assertEquals("G", ShortcutHandler.readableKeyName(71, "G"));
    }

    @Test
    void ownNamesForTheCommonKeys() {
        assertEquals("G", ShortcutHandler.fallbackKeyName(71));
        assertEquals("A", ShortcutHandler.fallbackKeyName(65));
        assertEquals("Z", ShortcutHandler.fallbackKeyName(90));
        assertEquals("0", ShortcutHandler.fallbackKeyName(48));
        assertEquals("9", ShortcutHandler.fallbackKeyName(57));
        assertEquals("F1", ShortcutHandler.fallbackKeyName(290));
        assertEquals("F12", ShortcutHandler.fallbackKeyName(301));
        assertEquals("Keypad 0", ShortcutHandler.fallbackKeyName(320));
        assertEquals("Keypad 9", ShortcutHandler.fallbackKeyName(329));
        assertEquals("Space", ShortcutHandler.fallbackKeyName(32));
        assertEquals("Left Shift", ShortcutHandler.fallbackKeyName(340));
        assertEquals("Left Alt", ShortcutHandler.fallbackKeyName(342));
        assertEquals("Right Shift", ShortcutHandler.fallbackKeyName(344));
        assertEquals("Tab", ShortcutHandler.fallbackKeyName(258));
        assertEquals("Up Arrow", ShortcutHandler.fallbackKeyName(265));
    }

    @Test
    void anUnknownKeyGetsItsNumberAndNeverAnEmptyName() {
        assertEquals("Key 999", ShortcutHandler.fallbackKeyName(999));
        assertEquals("Key 999", ShortcutHandler.readableKeyName(999, "key.keyboard.999"));
        assertEquals("Key 1", ShortcutHandler.fallbackKeyName(1));
    }

    @Test
    void noOwnNameStartsWithKeyDotSoItIsNeverTakenForARawKey() {
        for (int key = 1; key < 400; key++) {
            String name = ShortcutHandler.fallbackKeyName(key);
            assertEquals(false, name.isBlank(), "Taste " + key);
            assertEquals(false, name.startsWith("key."), "Taste " + key + ": " + name);
        }
    }
}
