package com.shokiteufel.shokimod.sprung;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Zeichenhilfen fuer das Leap Menu, nach {@code Render2D} und {@code Resolution} von NoammAddons (CC0): Alles wird in einer Bezugsaufloesung von
 * 960 x 540 gezeichnet und auf das Fenster gezogen, damit das Menue bei jeder GUI-Groesse gleich gross wirkt.
 *
 * <p>Das Menue zeichnet nur, solange das Fenster des Leaps offen ist (Sekunden, nicht das ganze Spiel); ein Bild darin sind eine Handvoll Flaechen
 * und Texte, nichts, was gerechnet und gemerkt werden muesste.
 */
final class SprungZeichnen {

    private static final float BEZUG_BREITE = 960f;
    private static final float BEZUG_HOEHE = 540f;

    private SprungZeichnen() {
    }

    /** Wie viele GUI-Punkte eine Einheit der Bezugsaufloesung ist ({@code Resolution.scale}) */
    static float faktor(int guiBreite, int guiHoehe) {
        return Math.min(guiBreite / BEZUG_BREITE, guiHoehe / BEZUG_HOEHE);
    }

    /** Eine Flaeche mit Kommazahlen: ein Punkt, gezogen und verschoben ({@code drawRect}) */
    static void rechteck(GuiGraphicsExtractor g, float x, float y, float breite, float hoehe, int argb) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(breite, hoehe);
        g.fill(0, 0, 1, 1, argb);
        g.pose().popMatrix();
    }

    /** Ein Rahmen innerhalb der Flaeche, {@code dicke} dick ({@code drawBorder}) */
    static void rahmen(GuiGraphicsExtractor g, float x, float y, float breite, float hoehe, int argb, float dicke) {
        rechteck(g, x, y, breite, dicke, argb);
        rechteck(g, x, y + hoehe - dicke, breite, dicke, argb);
        rechteck(g, x, y + dicke, dicke, hoehe - dicke * 2, argb);
        rechteck(g, x + breite - dicke, y + dicke, dicke, hoehe - dicke * 2, argb);
    }

    /** Ein Kasten mit hellem Rand oben links und dunklem unten rechts ({@code drawFloatingRect}); Kommazahlen werden abgeschnitten */
    static void schwebend(GuiGraphicsExtractor g, float x, float y, float breite, float hoehe, int argb) {
        int licht = SprungFarben.heller(argb);
        int dunkel = SprungFarben.dunkler(argb);
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) breite;
        int ih = (int) hoehe;
        g.fill(ix, iy, ix + 1, iy + ih, licht);
        g.fill(ix + 1, iy, ix + iw, iy + 1, licht);
        g.fill(ix + iw - 1, iy + 1, ix + iw, iy + ih, dunkel);
        g.fill(ix + 1, iy + ih - 1, ix + iw - 1, iy + ih, dunkel);
        g.fill(ix + 1, iy + 1, ix + iw - 1, iy + ih - 1, argb);
    }

    /** Eine Linie von (x1, y1) nach (x2, y2) ({@code drawLine}) */
    static void linie(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int argb, float dicke) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float laenge = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.pose().translate(0f, -dicke / 2f);
        g.pose().scale(laenge, dicke);
        g.fill(0, 0, 1, 1, argb);
        g.pose().popMatrix();
    }

    /** Text mit Schatten, die linke obere Ecke bei (x, y), im Massstab ({@code drawString}) */
    static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, int argb, float massstab) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (massstab != 1f) g.pose().scale(massstab, massstab);
        g.text(font, s, 0, 0, argb, true);
        g.pose().popMatrix();
    }

    /** Text, dessen Mitte bei {@code mitteX} liegt, die Oberkante bei {@code y} ({@code drawCenteredString}) */
    static void mitte(GuiGraphicsExtractor g, Font font, String s, float mitteX, float y, int argb, float massstab) {
        g.pose().pushMatrix();
        g.pose().translate(mitteX, y);
        if (massstab != 1f) g.pose().scale(massstab, massstab);
        g.centeredText(font, s, 0, 0, argb);
        g.pose().popMatrix();
    }

    /** Ein Kopf mit Hut-Schicht; ohne Bild ein grauer Platzhalter ({@code drawPlayerHead}) */
    static void kopf(GuiGraphicsExtractor g, PlayerSkin haut, int x, int y, int groesse) {
        if (haut == null) {
            g.fill(x, y, x + groesse, y + groesse, 0xFF555555);
            return;
        }
        PlayerFaceExtractor.extractRenderState(g, haut, x, y, groesse);
    }

    /** Die Zeilen des Ringstuecks, das gerade gezeichnet wird: je Stueck drei Zahlen (xs, xe, zeile) - wiederverwendet, nicht je Bild neu */
    private static int[] puffer = new int[6 * 600];

    /**
     * Ein Stueck eines Rings, von {@code a0} bis {@code a1} (Bogenmass, weniger als ein halber Kreis), Zeile fuer Zeile in halben Einheiten
     * gefuellt ({@code drawAnnularSegment}): Noamm baut es aus Dreiecken; hier gibt es keine freie Flaeche, und Zeilen ueberlappen nie - ein
     * Stueck mit Deckkraft hat darum keine dunkleren Nahtstellen.
     */
    static void ringstueck(GuiGraphicsExtractor g, float mx, float my, float innen, float aussen, double a0, double a1, int argb) {
        int noetig = ringPlatz(aussen);
        if (puffer.length < noetig) puffer = new int[noetig];
        int n = ringZeilen(mx, my, innen, aussen, a0, a1, puffer);
        g.pose().pushMatrix();
        g.pose().scale(0.5f, 0.5f);
        for (int i = 0; i < n; i += 3) {
            g.fill(puffer[i], puffer[i + 2], puffer[i + 1], puffer[i + 2] + 1, argb);
        }
        g.pose().popMatrix();
    }

    /** Wie viele Zahlen {@link #ringZeilen} hoechstens schreibt: zwei Stuecke je Zeile, je drei Zahlen */
    static int ringPlatz(float aussen) {
        return 6 * ((int) Math.ceil(aussen * 4.0) + 4);
    }

    /**
     * Die Zeilen eines Ringstuecks in halben Einheiten, also in einem Raster mit doppelten Koordinaten (Mitte (2*mx, 2*my), Radien mal zwei): je
     * Zeile ein Stueck, oder zwei, wo die Zeile durch das Loch des Rings geht. Reine Rechnung.
     *
     * @param aus Platz fuer mindestens {@link #ringPlatz} Zahlen; je Stueck steht dort xs, xe (nicht eingeschlossen) und die Zeile
     * @return wie viele Zahlen geschrieben wurden
     */
    static int ringZeilen(float mx, float my, float innen, float aussen, double a0, double a1, int[] aus) {
        double cx = mx * 2.0;
        double cy = my * 2.0;
        double ri = innen * 2.0;
        double ra = aussen * 2.0;
        // Die Grenzen des Keils: Punkte p mit  A*px + B*py >= 0  fuer beide Kanten
        double a1x = -Math.sin(a0);
        double b1x = Math.cos(a0);
        double a2x = Math.sin(a1);
        double b2x = -Math.cos(a1);

        int n = 0;
        int von = (int) Math.floor(cy - ra);
        int bis = (int) Math.ceil(cy + ra);
        for (int zeile = von; zeile < bis; zeile++) {
            double dy = zeile + 0.5 - cy;
            double aussenQuadrat = ra * ra - dy * dy;
            if (aussenQuadrat <= 0) continue;
            double xa = Math.sqrt(aussenQuadrat);
            double innenQuadrat = ri * ri - dy * dy;
            double xi = innenQuadrat > 0 ? Math.sqrt(innenQuadrat) : -1.0;

            // Der Keil begrenzt die Zeile auf ein Stueck [lo, hi]
            double lo = -Double.MAX_VALUE;
            double hi = Double.MAX_VALUE;
            double c1 = b1x * dy;
            if (a1x == 0.0) {
                if (c1 < 0) continue;
            } else if (a1x > 0) {
                lo = Math.max(lo, -c1 / a1x);
            } else {
                hi = Math.min(hi, -c1 / a1x);
            }
            double c2 = b2x * dy;
            if (a2x == 0.0) {
                if (c2 < 0) continue;
            } else if (a2x > 0) {
                lo = Math.max(lo, -c2 / a2x);
            } else {
                hi = Math.min(hi, -c2 / a2x);
            }
            if (lo > hi) continue;

            if (xi < 0) {
                n = stueck(aus, n, cx, zeile, Math.max(lo, -xa), Math.min(hi, xa));
            } else {
                n = stueck(aus, n, cx, zeile, Math.max(lo, -xa), Math.min(hi, -xi));
                n = stueck(aus, n, cx, zeile, Math.max(lo, xi), Math.min(hi, xa));
            }
        }
        return n;
    }

    private static int stueck(int[] aus, int n, double cx, int zeile, double lo, double hi) {
        if (hi <= lo) return n;
        int xs = (int) Math.round(cx + lo);
        int xe = (int) Math.round(cx + hi);
        if (xe <= xs) return n;
        aus[n] = xs;
        aus[n + 1] = xe;
        aus[n + 2] = zeile;
        return n + 3;
    }
}
