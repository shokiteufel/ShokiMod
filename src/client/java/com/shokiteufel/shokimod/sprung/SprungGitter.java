package com.shokiteufel.shokimod.sprung;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Das gewoehnliche 2x2-Menue ("Default" bei NoammAddons, {@code GridLeapRenderer}, CC0): vier Kaesten mit Kopf, Name und Klasse. Unter dem Zeiger
 * liegt der Kasten, in dessen Viertel des Fensters er steht - nicht erst der Kasten selbst.
 */
final class SprungGitter extends SprungDarstellung {

    static final SprungGitter INSTANZ = new SprungGitter();

    private static final float BREITE = 128f * 1.3f;
    private static final float HOEHE = 80f * 0.8f;
    private static final float ABSTAND = 40f;
    private static final int KOPF = 50;
    private static final int TOT = 0xFFFF5555;

    private SprungGitter() {
    }

    @Override
    void zeichnen(GuiGraphicsExtractor g, Font font, int guiBreite, int guiHoehe, double mausX, double mausY) {
        float s = SprungZeichnen.faktor(guiBreite, guiHoehe);
        float nutzer = SprungMenue.cfg().scale / 100f * 2.0f;
        float breite = guiBreite / s / nutzer;
        float hoehe = guiHoehe / s / nutzer;

        g.pose().pushMatrix();
        g.pose().scale(s, s);
        g.pose().scale(nutzer, nutzer);

        float gitterB = BREITE * 2 + ABSTAND;
        float gitterH = HOEHE * 2 + ABSTAND;
        float sx = (breite - gitterB) / 2f;
        float sy = (hoehe - gitterH) / 2f;
        float[][] orte = {
                {sx, sy},
                {sx + BREITE + ABSTAND, sy},
                {sx, sy + HOEHE + ABSTAND},
                {sx + BREITE + ABSTAND, sy + HOEHE + ABSTAND}};

        Integer zeiger = index(guiBreite, guiHoehe, mausX, mausY);
        for (int i = 0; i < 4; i++) {
            SprungMenue.Eintrag e = SprungMenue.platz(i);
            if (e == null) continue;
            kasten(g, font, orte[i][0], orte[i][1], e.spieler(), zeiger != null && zeiger == i);
        }
        g.pose().popMatrix();
    }

    private static void kasten(GuiGraphicsExtractor g, Font font, float x, float y, SprungSpieler spieler, boolean zeiger) {
        int farbe = SprungMenue.kastenFarbe(spieler, zeiger);
        SprungZeichnen.schwebend(g, x, y, BREITE, HOEHE, SprungFarben.mitDeckkraft(farbe, 190));

        int kx = (int) (x + 10);
        int ky = (int) (y + (HOEHE / 2) - KOPF / 2);
        int klasse = 0xFF000000 | spieler.klasse.farbe;
        SprungZeichnen.kopf(g, spieler.haut, kx, ky, KOPF);
        SprungZeichnen.rahmen(g, kx, ky, KOPF, KOPF, klasse, 1);

        int tx = (int) (x + 10 + KOPF + 5);
        int ty = (int) (y + HOEHE / 2 - font.lineHeight);
        SprungZeichnen.text(g, font, spieler.name, tx, ty + 2, klasse, 1f);
        SprungZeichnen.text(g, font, spieler.tot ? "DEAD" : spieler.klasse.anzeige, tx, ty + 12, spieler.tot ? TOT : klasse, 1f);
    }

    @Override
    Integer index(int guiBreite, int guiHoehe, double mausX, double mausY) {
        return viertel(mausX, mausY, guiBreite, guiHoehe);
    }

    /** Das Viertel des Fensters, in dem der Zeiger steht: 0 oben links, 1 oben rechts, 2 unten links, 3 unten rechts; auf der Mittellinie null */
    static Integer viertel(double mausX, double mausY, double breite, double hoehe) {
        double cx = breite / 2.0;
        double cy = hoehe / 2.0;
        if (mausX < cx && mausY < cy) return 0;
        if (mausX > cx && mausY < cy) return 1;
        if (mausX < cx && mausY > cy) return 2;
        if (mausX > cx && mausY > cy) return 3;
        return null;
    }
}
