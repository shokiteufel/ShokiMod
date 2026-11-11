package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.dungeonkarte.FunnyKarteHud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Das Menue als Karte ("Map" bei NoammAddons, {@code MapLeapRenderer}, CC0): Man klickt den Kopf eines Mitspielers auf der Karte an. Bei Noamm
 * zeichnet seine eigene Karte, und er bietet den Modus nur an, wenn sie an ist; hier zeichnet die Funny Map ({@link FunnyKarteHud}), und das
 * Menue haengt nicht an der Karte von NoammAddons.
 */
final class SprungKarte extends SprungDarstellung {

    static final SprungKarte INSTANZ = new SprungKarte();

    private static final float LEINWAND = 128f;
    /** Wo das Raster auf der Leinwand beginnt - von {@link FunnyKarteHud#zeichneFuerSprung} gelesen, fuer die Treffer des Zeigers */
    private static int[] verschiebung;

    private SprungKarte() {
    }

    @Override
    void zeichnen(GuiGraphicsExtractor g, Font font, int guiBreite, int guiHoehe, double mausX, double mausY) {
        float s = SprungZeichnen.faktor(guiBreite, guiHoehe);
        float breite = guiBreite / s;
        float hoehe = guiHoehe / s;
        float massstab = SprungMenue.cfg().mapScale;
        float flaeche = LEINWAND * massstab;
        float ox = (breite - flaeche) / 2f;
        float oy = (hoehe - flaeche - 8 * massstab) / 2f;

        g.pose().pushMatrix();
        g.pose().scale(s, s);
        SprungZeichnen.rechteck(g, 0, 0, breite, hoehe, 0xAA000000);

        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(massstab, massstab);
        verschiebung = FunnyKarteHud.zeichneFuerSprung(g);
        g.pose().popMatrix();

        Integer zeiger = index(guiBreite, guiHoehe, mausX, mausY);
        if (zeiger != null) {
            SprungMenue.Eintrag e = SprungMenue.platz(zeiger);
            float[] p = e == null ? null : punkt(e.spieler(), ox, oy, massstab);
            if (p != null) {
                float r = 7f * massstab;
                SprungZeichnen.rahmen(g, p[0] - r, p[1] - r, r * 2f, r * 2f, 0xFFFFFFFF, 2f);
            }
        }
        g.pose().popMatrix();
    }

    /** Wo der Kopf des Spielers steht, in Einheiten der Bezugsaufloesung; null, wenn die Karte ihn nicht kennt */
    private static float[] punkt(SprungSpieler spieler, float ox, float oy, float massstab) {
        float[] karte = FunnyKarteHud.punktDesSpielers(spieler.name, verschiebung);
        if (karte == null) return null;
        return new float[] {ox + karte[0] * massstab, oy + karte[1] * massstab};
    }

    @Override
    Integer index(int guiBreite, int guiHoehe, double mausX, double mausY) {
        if (verschiebung == null) return null;
        float s = SprungZeichnen.faktor(guiBreite, guiHoehe);
        float massstab = SprungMenue.cfg().mapScale;
        float flaeche = LEINWAND * massstab;
        float ox = (guiBreite / s - flaeche) / 2f;
        float oy = (guiHoehe / s - flaeche - 8 * massstab) / 2f;
        double mx = mausX / s;
        double my = mausY / s;
        float r = 7f * massstab;
        Integer treffer = null;
        // Wie bei Noamm gilt der letzte Treffer; ein Toter hat keinen Kopf zum Anklicken
        for (int i = 0; i < 4; i++) {
            SprungMenue.Eintrag e = SprungMenue.platz(i);
            if (e == null || e.spieler().tot) continue;
            float[] p = punkt(e.spieler(), ox, oy, massstab);
            if (p == null) continue;
            if (mx >= p[0] - r && mx <= p[0] + r && my >= p[1] - r && my <= p[1] + r) treffer = i;
        }
        return treffer;
    }
}
