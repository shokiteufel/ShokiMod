package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Copy Chat: was kopiert wird und welche Zeile unter dem Zeiger liegt. */
class CopyChatTest {

    @Test
    void ctrlCopiesTheLineAndNothingTheWholeMessage() {
        assertEquals("the whole message that wraps", CopyChat.chooseText("the whole message that wraps", "the whole message", false));
        assertEquals("the whole message", CopyChat.chooseText("the whole message that wraps", "the whole message", true));
    }

    @Test
    void aLineOfTheChatIsReadWithoutStylesOrColourCodes() {
        FormattedCharSequence styled = FormattedCharSequence.forward("Hello World", Style.EMPTY.withBold(true));
        assertEquals("Hello World", CopyChat.lineText(styled));
        assertEquals("", CopyChat.lineText(FormattedCharSequence.EMPTY));
    }

    @Test
    void thePointInsideTheRectangleOfATextHitsIt() {
        Matrix3x2f identity = new Matrix3x2f();
        assertTrue(CopyChat.hit(identity, 0, 0, 50, 9, 10, 4));
        assertTrue(CopyChat.hit(identity, 0, 0, 50, 9, 0, 0), "die obere linke Ecke gehoert dazu");
        assertFalse(CopyChat.hit(identity, 0, 0, 50, 9, 60, 4), "rechts daneben");
        assertFalse(CopyChat.hit(identity, 0, 0, 50, 9, 50, 4), "der rechte Rand gehoert nicht mehr dazu");
        assertFalse(CopyChat.hit(identity, 0, 0, 50, 9, 10, 9), "die Zeile darunter");
        assertFalse(CopyChat.hit(identity, 0, 0, 50, 9, 10, -1), "die Zeile darueber");
    }

    @Test
    void theMatrixOfTheChatIsUndoneBeforeTheCheck() {
        // Chatgroesse 50 Prozent: ein Text, der in den Koordinaten des Chats 0..50 breit ist, ist auf dem Bildschirm 25 breit
        Matrix3x2f half = new Matrix3x2f().scale(0.5f, 0.5f);
        assertTrue(CopyChat.hit(half, 0, 0, 50, 9, 20, 2));
        assertFalse(CopyChat.hit(half, 0, 0, 50, 9, 40, 2));
        assertFalse(CopyChat.hit(half, 0, 0, 50, 9, 10, 6), "auf dem Bildschirm ist die Zeile nur 4,5 hoch");

        // Mit Verschiebung: der Chat beginnt bei (4|10)
        Matrix3x2f moved = new Matrix3x2f().translate(4f, 10f);
        assertTrue(CopyChat.hit(moved, 0, 0, 50, 9, 5, 12));
        assertFalse(CopyChat.hit(moved, 0, 0, 50, 9, 2, 12), "links vom Chat");
        assertFalse(CopyChat.hit(moved, 0, 0, 50, 9, 5, 5), "ueber dem Chat");

        // Eine Zeile weiter unten im Chat: top = 18
        assertTrue(CopyChat.hit(moved, 0, 18, 50, 9, 5, 30));
        assertFalse(CopyChat.hit(moved, 0, 18, 50, 9, 5, 12));
    }

    @Test
    void aTextWithAnOffsetFromTheAlignmentIsHitWhereItStands() {
        Matrix3x2f identity = new Matrix3x2f();
        assertTrue(CopyChat.hit(identity, 100, 20, 30, 9, 110, 25));
        assertFalse(CopyChat.hit(identity, 100, 20, 30, 9, 90, 25));
    }
}
