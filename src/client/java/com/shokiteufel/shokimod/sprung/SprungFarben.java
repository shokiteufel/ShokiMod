package com.shokiteufel.shokimod.sprung;

/**
 * Die Farben der Kaesten im Leap Menu, nach {@code LeapMenuRenderer.leapBoxColor} von NoammAddons (CC0). Reine Rechnung auf Ganzzahlen (ARGB).
 */
final class SprungFarben {

    static final int KASTEN = 0xFF212121;
    static final int KASTEN_ZEIGER = 0xFF434343;
    static final int OEFFNER = 0xFF878787;
    static final int OEFFNER_ZEIGER = 0xFFFFFFFF;
    static final int IM_KERN = 0xFF007800;

    private static final float FAKTOR = 0.7f;

    private SprungFarben() {
    }

    /**
     * Die Farbe eines Kastens, in der Reihenfolge von Noamm: der letzte Tuer-Oeffner, ein Toter, einer im Kern, sonst die gewoehnliche (heller, wenn
     * der Zeiger darauf steht).
     */
    static int kasten(boolean zeiger, boolean oeffnerMarkieren, boolean istOeffner, boolean totFaerben, boolean tot, boolean kernMarkieren,
                      boolean kernOffen, boolean imKern) {
        if (oeffnerMarkieren && istOeffner) return zeiger ? OEFFNER_ZEIGER : OEFFNER;
        if (totFaerben && tot) {
            int c = mischen(KASTEN, 0xFFFF0000, 0.2f);
            return zeiger ? heller(c) : c;
        }
        if (kernMarkieren && kernOffen && imKern) return IM_KERN;
        return zeiger ? KASTEN_ZEIGER : KASTEN;
    }

    /** Von {@code a} nach {@code b} um den Anteil {@code t}, je Kanal; die Deckkraft bleibt die von {@code a} */
    static int mischen(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1f - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1f - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1f - t) + (b & 0xFF) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    /** Wie {@code java.awt.Color.brighter()}: jeder Kanal durch 0,7, hoechstens 255; reines Schwarz wird dunkelgrau */
    static int heller(int c) {
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        int i = (int) (1.0 / (1.0 - FAKTOR));
        if (r == 0 && g == 0 && b == 0) return (c & 0xFF000000) | (i << 16) | (i << 8) | i;
        if (r > 0 && r < i) r = i;
        if (g > 0 && g < i) g = i;
        if (b > 0 && b < i) b = i;
        return (c & 0xFF000000) | (Math.min((int) (r / FAKTOR), 255) << 16) | (Math.min((int) (g / FAKTOR), 255) << 8)
                | Math.min((int) (b / FAKTOR), 255);
    }

    /** Wie {@code java.awt.Color.darker()}: jeder Kanal mal 0,7 */
    static int dunkler(int c) {
        int r = Math.max((int) (((c >> 16) & 0xFF) * FAKTOR), 0);
        int g = Math.max((int) (((c >> 8) & 0xFF) * FAKTOR), 0);
        int b = Math.max((int) ((c & 0xFF) * FAKTOR), 0);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Dieselbe Farbe mit einer anderen Deckkraft (0 bis 255) */
    static int mitDeckkraft(int c, int alpha) {
        return (alpha << 24) | (c & 0xFFFFFF);
    }
}
