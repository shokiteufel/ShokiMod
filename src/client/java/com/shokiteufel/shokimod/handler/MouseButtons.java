package com.shokiteufel.shokimod.handler;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Welche Maustaste ist ein Klick? Auf 26.3 zaehlt das Spiel die Tasten nach SDL: links = 1, Mitte = 2, rechts = 3 (vorher, mit GLFW: links = 0, rechts = 1).
 * Eine feste Zahl im Code - "button() == 1" fuer rechts - trifft dort die LINKE Taste und laesst die rechte aus. Darum fragt jede Stelle der Mod hier nach,
 * und die Zahlen kommen aus den Konstanten des Spiels ({@link InputConstants}), nicht aus dem Kopf.
 */
public final class MouseButtons {

    private MouseButtons() {
    }

    /** Die linke Taste */
    public static boolean isLeft(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT;
    }

    /** Die rechte Taste */
    public static boolean isRight(int button) {
        return button == InputConstants.MOUSE_BUTTON_RIGHT;
    }
}
