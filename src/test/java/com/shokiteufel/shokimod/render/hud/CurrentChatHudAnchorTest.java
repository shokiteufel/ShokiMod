package com.shokiteufel.shokimod.render.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Kasten "Current Chat Display" sitzt (Vorgabe) kurz ueber der Chat-Eingabe: links am Rand des Chats, mit zwei Punkten Luft ueber dem Eingabefeld des Spiels, das
 * 14 Punkte ueber dem unteren Rand beginnt. Der Ort haengt an der Bildschirmhoehe in Spielpunkten, nicht an einem festen Bruchteil.
 */
class CurrentChatHudAnchorTest {

    /** Wie das Zeichnen rechnet (SafariHud.originX / originY): Bruchteil mal Groesse, gerundet */
    private static int origin(float fraction, int size) {
        return Math.round(fraction * size);
    }

    @Test
    void theBoxStartsAtTheLeftEdgeOfTheChatOnEveryScreenWidth() {
        for (int width : new int[]{240, 320, 427, 480, 640, 854, 960, 1280, 1920}) {
            assertEquals(CurrentChatHud.LEFT_EDGE, origin(CurrentChatHud.anchoredX(width), width), "Breite " + width);
        }
        assertEquals(4, CurrentChatHud.LEFT_EDGE, "der Text der Chatzeilen beginnt bei vier Punkten");
    }

    @Test
    void theBoxSitsOnTheChatInputWithTwoPointsOfAirOnEveryScreenHeight() {
        int box = CurrentChatHud.boxHeight(1.0f);
        assertEquals(18, box, "eine Zeile (10) und je vier Punkte Rand");
        for (int height = 135; height <= 1080; height += 7) {
            int top = origin(CurrentChatHud.anchoredY(height, box), height);
            // Die Unterkante des Kastens liegt zwei Punkte ueber dem Eingabefeld (height - 14)
            assertEquals(height - CurrentChatHud.INPUT_HEIGHT - CurrentChatHud.GAP, top + box, "Hoehe " + height);
            assertTrue(top + box < height - CurrentChatHud.INPUT_HEIGHT, "nie auf dem Eingabefeld");
        }
    }

    @Test
    void aBiggerBoxGrowsUpwardsAndKeepsItsBottomEdge() {
        int height = 270;
        for (float scale : new float[]{0.5f, 1.0f, 1.5f, 2.0f, 3.0f}) {
            int box = CurrentChatHud.boxHeight(scale);
            int top = origin(CurrentChatHud.anchoredY(height, box), height);
            assertEquals(height - 16, top + box, "Skalierung " + scale);
        }
        assertEquals(36, CurrentChatHud.boxHeight(2.0f));
    }

    @Test
    void theBoxStaysBelowTheLastChatLineAtTheNormalScale() {
        // Die letzte Chatzeile des Spiels endet etwa 40 Punkte ueber dem unteren Rand: Bei Skalierung 1 passt der Kasten dazwischen
        int height = 270;
        int top = origin(CurrentChatHud.anchoredY(height, CurrentChatHud.boxHeight(1.0f)), height);
        assertTrue(top >= height - 40, "unter den Chatzeilen: " + top);
    }

    @Test
    void theFractionsAreInsideTheScreen() {
        for (int size : new int[]{135, 270, 540, 1080}) {
            float y = CurrentChatHud.anchoredY(size, CurrentChatHud.boxHeight(1.0f));
            assertTrue(y > 0f && y < 1f);
            float x = CurrentChatHud.anchoredX(size);
            assertTrue(x > 0f && x < 1f);
        }
    }
}
