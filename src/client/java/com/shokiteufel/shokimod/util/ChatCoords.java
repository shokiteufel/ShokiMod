package com.shokiteufel.shokimod.util;

import net.minecraft.core.BlockPos;

/**
 * Koordinaten in den Meldungen, die die Mod in den Party-Chat schreibt: eine Form fuer alle.
 *
 * "x: -158, y: 10, z: -165" - mit den Buchstaben und den Kommas. Skyblocker erkennt genau diese Form im Chat (ChatPositionShare,
 * GENERIC_COORDS_PATTERN) und haengt dann die klickbare Zeile "Click to display waypoint at x: y: z:" an; ohne die Kommas
 * ("x: -158 y: 10 z: -165") erkennt es nichts. Dieselbe Form schreibt der Hotspot-Vorschlag schon (HotspotShare), und Skyblocker
 * selbst teilt Positionen so.
 */
public final class ChatCoords {

    private ChatCoords() {
    }

    public static String format(int x, int y, int z) {
        return "x: " + x + ", y: " + y + ", z: " + z;
    }

    public static String format(BlockPos pos) {
        return format(pos.getX(), pos.getY(), pos.getZ());
    }
}
