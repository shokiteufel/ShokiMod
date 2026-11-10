package com.shokiteufel.shokimod.dungeonkarte;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Das Kartenbild geht Punkt fuer Punkt per {@code NativeImage.setPixel} in die Textur. Hier wird geprueft, dass {@code setPixel} wirklich ARGB
 * nimmt und wiedergibt (nicht ABGR): sonst waeren Rot und Blau der Karte vertauscht. Ohne Grafik, nur Speicher.
 */
class KartenBildPixelTest {

    @Test
    void setPixelTakesAndGivesBackArgb() {
        try (NativeImage bild = new NativeImage(KartenMaler.BREITE, KartenMaler.HOEHE_MAX, true)) {
            int farbe = 0xFFAA5511;   // deckend, rot 0xAA, gruen 0x55, blau 0x11
            bild.setPixel(3, 4, farbe);
            assertEquals(farbe, bild.getPixel(3, 4));
            // Dieselbe Farbe, wie sie im Speicher steht: ABGR = alpha, blau, gruen, rot
            assertEquals(0xFF1155AA, bild.getPixelsABGR()[4 * KartenMaler.BREITE + 3]);
        }
    }

    @Test
    void aTransparentPointStaysTransparent() {
        try (NativeImage bild = new NativeImage(KartenMaler.BREITE, KartenMaler.HOEHE_MAX, true)) {
            assertEquals(0, bild.getPixel(10, 10), "ein frisches Bild ist durchsichtig");
            bild.setPixel(10, 10, 0x80336699);
            assertEquals(0x80336699, bild.getPixel(10, 10));
        }
    }
}
