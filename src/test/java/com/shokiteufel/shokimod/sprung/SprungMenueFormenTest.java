package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.data.ModConfig.LeapCustomType;
import com.shokiteufel.shokimod.data.ModConfig.LeapOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Formen des Leap Menus: Farben der Kaesten, Viertel des Rasters, Stuecke des Rads, Ring aus Zeilen */
class SprungMenueFormenTest {

    private static SprungSpieler s(String name, SprungKlasse klasse) {
        return new SprungSpieler(name, klasse, false);
    }

    // ------------------------------------------------------------------ Farben

    @Test
    void anOrdinaryBoxIsDarkAndBrighterUnderThePointer() {
        assertEquals(SprungFarben.KASTEN, SprungFarben.kasten(false, true, false, true, false, true, false, false));
        assertEquals(SprungFarben.KASTEN_ZEIGER, SprungFarben.kasten(true, true, false, true, false, true, false, false));
    }

    @Test
    void theLastDoorOpenerComesBeforeEverythingElse() {
        assertEquals(SprungFarben.OEFFNER, SprungFarben.kasten(false, true, true, true, true, true, true, true));
        assertEquals(SprungFarben.OEFFNER_ZEIGER, SprungFarben.kasten(true, true, true, true, true, true, true, true));
        // ohne die Einstellung zaehlt er nicht
        assertEquals(SprungFarben.KASTEN, SprungFarben.kasten(false, false, true, true, false, true, false, false));
    }

    @Test
    void aDeadPlayerIsTintedRedAndInTheCoreOnlyAfterItOpened() {
        int tot = SprungFarben.kasten(false, false, false, true, true, true, false, false);
        assertNotEquals(SprungFarben.KASTEN, tot);
        // 20 % Rot in 0x212121: 0x4D (77), 0x1A (26), 0x1A
        assertEquals(0xFF4D1A1A, tot);
        assertEquals(SprungFarben.heller(tot), SprungFarben.kasten(true, false, false, true, true, true, false, false));
        // im Kern gruen, aber nur mit der Einstellung und nur wenn der Kern offen ist
        assertEquals(SprungFarben.IM_KERN, SprungFarben.kasten(false, false, false, true, false, true, true, true));
        assertEquals(SprungFarben.KASTEN, SprungFarben.kasten(false, false, false, true, false, true, false, true));
        assertEquals(SprungFarben.KASTEN, SprungFarben.kasten(false, false, false, true, false, false, true, true));
        // ein Toter im Kern bleibt rot: die Rangfolge von Noamm
        assertEquals(tot, SprungFarben.kasten(false, false, false, true, true, true, true, true));
    }

    @Test
    void brighterAndDarkerFollowJavaAwtColor() {
        assertEquals(0xFF2F2F2F, SprungFarben.heller(0xFF212121));    // 33 / 0,7 = 47
        assertEquals(0xFF171717, SprungFarben.dunkler(0xFF212121));   // 33 * 0,7 = 23
        assertEquals(0xFF030303, SprungFarben.heller(0xFF000000));    // reines Schwarz wird dunkelgrau
        assertEquals(0xFFFFFFFF, SprungFarben.heller(0xFFFFFFFF));    // hoechstens 255
        assertEquals(0xBEFF8800 & 0xFF000000, SprungFarben.heller(0xBEFF8800) & 0xFF000000, "die Deckkraft bleibt");
        assertEquals(0xBE212121, SprungFarben.mitDeckkraft(0xFF212121, 190));
    }

    // ------------------------------------------------------------------ Raster

    @Test
    void theGridHasTheQuarterOfTheWindowUnderThePointer() {
        assertEquals(0, SprungGitter.viertel(100, 100, 800, 600));
        assertEquals(1, SprungGitter.viertel(700, 100, 800, 600));
        assertEquals(2, SprungGitter.viertel(100, 500, 800, 600));
        assertEquals(3, SprungGitter.viertel(700, 500, 800, 600));
        assertNull(SprungGitter.viertel(400, 100, 800, 600), "auf der Mittellinie nichts");
        assertNull(SprungGitter.viertel(100, 300, 800, 600));
    }

    // ------------------------------------------------------------------ Rad

    @Test
    void theWheelHasFivePiecesAndADeadZoneInTheMiddle() {
        assertNull(SprungRad.platz(0, 0));
        assertNull(SprungRad.platz(3, 3), "innerhalb von 5 Einheiten");
        assertEquals(0, SprungRad.platz(0, -50), "oben");
        assertEquals(1, SprungRad.platz(50, -15), "rechts oben, im Uhrzeigersinn");
        assertEquals(2, SprungRad.platz(30, 50), "rechts unten");
        assertEquals(3, SprungRad.platz(-30, 50), "links unten");
        assertEquals(4, SprungRad.platz(-50, -15), "links oben");
    }

    @Test
    void theWheelPutsEveryClassOnItsOwnPiece() {
        List<SprungSpieler> team = List.of(
                s("Tank1", SprungKlasse.TANK), s("Mage1", SprungKlasse.MAGE), s("Arch1", SprungKlasse.ARCHER), s("Heal1", SprungKlasse.HEALER),
                s("Bers1", SprungKlasse.BERSERK));
        SprungSpieler[] p = SprungRad.plaetze(team, LeapOrder.CLASS, null, new SprungMenue.Sortierdaten(LeapCustomType.NAME, List.of()));
        assertEquals("Arch1", p[0].name);
        assertEquals("Bers1", p[1].name);
        assertEquals("Heal1", p[2].name);
        assertEquals("Mage1", p[3].name);
        assertEquals("Tank1", p[4].name);
    }

    @Test
    void theWheelFillsAFreePieceWithTheSecondOfAClass() {
        List<SprungSpieler> team = List.of(
                s("Mage2", SprungKlasse.MAGE), s("Mage1", SprungKlasse.MAGE), s("Arch1", SprungKlasse.ARCHER), s("Tank1", SprungKlasse.TANK));
        SprungSpieler[] p = SprungRad.plaetze(team, LeapOrder.CLASS, null, new SprungMenue.Sortierdaten(LeapCustomType.NAME, List.of()));
        // nach Name: Arch1, Mage1, Mage2, Tank1 - Mage2 findet sein Stueck belegt und rueckt in das erste freie (Berserk)
        assertEquals("Arch1", p[0].name);
        assertEquals("Mage2", p[1].name);
        assertNull(p[2]);
        assertEquals("Mage1", p[3].name);
        assertEquals("Tank1", p[4].name);
    }

    @Test
    void withAnotherOrderTheWheelTakesTheFirstFiveOfTheMenuOrder() {
        List<SprungSpieler> team = List.of(s("Zed", SprungKlasse.TANK), s("Anna", SprungKlasse.MAGE), s("Bert", SprungKlasse.ARCHER));
        SprungSpieler[] p = SprungRad.plaetze(team, LeapOrder.NAME, null, new SprungMenue.Sortierdaten(LeapCustomType.NAME, List.of()));
        assertEquals("Anna", p[0].name);
        assertEquals("Bert", p[1].name);
        assertEquals("Zed", p[2].name);
        assertNull(p[3]);
    }

    // ------------------------------------------------------------------ Ring aus Zeilen

    @Test
    void fiveRingPiecesTileTheRingWithoutGapsOrOverlap() {
        float mx = 100f;
        float my = 100f;
        float innen = 58.5f;
        float aussen = 130f;
        double stueck = Math.PI * 2.0 / 5;
        // Alle Punkte der doppelten Rasters, die ein Stueck deckt, in einer Menge: kein Punkt darf zweimal vorkommen
        java.util.Set<Long> gedeckt = new java.util.HashSet<>();
        long summe = 0;
        for (int i = 0; i < 5; i++) {
            double winkel = -Math.PI / 2.0 + i * stueck;
            int[] aus = new int[SprungZeichnen.ringPlatz(aussen)];
            int n = SprungZeichnen.ringZeilen(mx, my, innen, aussen, winkel - stueck / 2.0, winkel + stueck / 2.0, aus);
            for (int k = 0; k < n; k += 3) {
                for (int x = aus[k]; x < aus[k + 1]; x++) {
                    assertTrue(gedeckt.add(((long) x << 32) | (aus[k + 2] & 0xFFFFFFFFL)), "Punkt doppelt gedeckt: " + x + "," + aus[k + 2]);
                    summe++;
                }
            }
        }
        // Die Flaeche des Rings in doppelten Koordinaten: pi * (R^2 - r^2) mit R = 260, r = 117
        double soll = Math.PI * (260.0 * 260.0 - 117.0 * 117.0);
        assertEquals(soll, (double) summe, soll * 0.01, "die fuenf Stuecke decken den Ring auf ein Prozent genau");
    }

    @Test
    void aSingleRingPieceStaysInsideItsWedge() {
        float mx = 0f;
        float my = 0f;
        // Das Stueck oben: von -126 bis -54 Grad (Mitte -90, also oberhalb der Mitte)
        double stueck = Math.PI * 2.0 / 5;
        int[] aus = new int[SprungZeichnen.ringPlatz(130f)];
        int n = SprungZeichnen.ringZeilen(mx, my, 58.5f, 130f, -Math.PI / 2.0 - stueck / 2.0, -Math.PI / 2.0 + stueck / 2.0, aus);
        assertTrue(n > 0);
        for (int k = 0; k < n; k += 3) {
            assertTrue(aus[k + 2] < 0, "das obere Stueck liegt ganz ueber der Mitte");
        }
    }
}
