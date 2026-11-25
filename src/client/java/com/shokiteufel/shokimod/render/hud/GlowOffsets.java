package com.shokiteufel.shokimod.render.hud;

/**
 * Wohin der dunkle Umriss der leuchtenden HUD-Schrift gezeichnet wird, gemessen in Bildpunkten von der Schrift aus.
 *
 * Bis 2.4.8 waren es acht Versaetze (alle Nachbarn). Jeder Versatz ist ein voller Durchgang durch das Schriftsystem - mit acht Versaetzen und der
 * Fuellung kostete jede Zeile neun Durchgaenge, und ein grosses Panel (der Profit Tracker) frass in einem Mitschnitt die Haelfte der Spielzeit
 * (126 statt etwa 230 FPS). Seit 2.4.9 sind es vier: oben, unten, links, rechts. Die Ecken des Umrisses fehlen dann, bei der kleinen Schrift sieht
 * man das kaum, und die Kosten halbieren sich.
 */
final class GlowOffsets {

    /** {dx, dy} je Durchgang */
    static final int[][] OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private GlowOffsets() {
    }
}
