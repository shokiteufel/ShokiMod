package com.shokiteufel.shokimod.equipment;

/**
 * Wo die vier Equipment-Plaetze im Inventarfenster stehen - nur Ganzzahlen, ohne Spieltypen: Es wird in jedem Bild gerechnet und darf nichts anlegen.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}). Von dort stammen die Reihenfolge (vier Plaetze untereinander, von oben nach unten
 * Halskette, Umhang, Guertel, Handschuhe) und der Ort: in der Luecke zwischen der Spielfigur und dem Bastelfeld. Der vierte Platz liegt dabei auf dem Zweithand-Platz
 * des Spiels; der rueckt deshalb ein Stueck nach rechts (unter das Bastelfeld), und der Knopf des Rezeptbuchs mit ihm. Die Umsetzung ist eigene Arbeit: Skyblocker
 * ersetzt das ganze Inventarfenster durch ein eigenes; hier bleibt das Fenster, wie es ist, und {@link EquipmentInventory} zeichnet hinein.
 *
 * Alle Zahlen sind vom Fenster aus gezaehlt (linke obere Ecke = 0/0), wie die Plaetze des Spiels; mit dem Rezeptbuch wandert alles mit dem Fenster. Die Rahmen und
 * der Hintergrund kommen aus dem Spiel (der Fensterhintergrund und das Bild {@code container/slot}): Sie folgen damit dem Aussehen, das man gewaehlt hat (Texturpaket,
 * dunkle oder helle Oberflaeche) - die Mod legt keine eigenen Farben fest.
 */
public final class EquipmentLayout {

    /** Wie breit das Inventarfenster des Spiels ist */
    public static final int IMAGE_WIDTH = 176;
    /** Ein Platz mit seinem Rahmen: achtzehn mal achtzehn; der Gegenstand darin ist 16 gross und sitzt einen Punkt eingerueckt */
    public static final int SLOT = 18;
    /** Der linke Rand des ersten Rahmens: Der Gegenstand steht bei 77 (wie der Zweithand-Platz des Spiels), der Rahmen einen Punkt davor */
    public static final int FRAME_X = 76;
    /** Der obere Rand des ersten Rahmens: Der Gegenstand steht bei 8 (wie der Helm links), der Rahmen einen Punkt davor */
    public static final int FRAME_Y = 7;
    /** Der Zweithand-Platz des Spiels: Gegenstand bei 77 / 62 */
    public static final int OFFHAND_X = 77;
    public static final int OFFHAND_Y = 62;
    /** Um so viele Punkte rueckt der Zweithand-Platz nach rechts (und der Knopf des Rezeptbuchs): hinter den vierten Equipment-Platz, unter das Bastelfeld (Gegenstand dann bei 98) */
    public static final int SHIFT = 21;
    /** Der Knopf des Rezeptbuchs: links oben bei 104 / 61, zwanzig breit, achtzehn hoch */
    public static final int BUTTON_X = 104;
    public static final int BUTTON_Y = 61;
    public static final int BUTTON_WIDTH = 20;
    public static final int BUTTON_HEIGHT = 18;

    /**
     * Das Sechseck, das auf einem leeren Platz steht (Skyblocker zeigt dort ein Bild je Platz; hier ein schlichter Umriss, einer fuer alle vier): rechteckige Stuecke
     * {x, y, Breite, Hoehe} im Gegenstandsfeld (16 mal 16), links und rechts gleich. Ein Umriss mit zwei Punkten Steigung, 14 breit und 16 hoch.
     */
    static final int[][] HEXAGON = {
            {7, 0, 2, 1},
            {5, 1, 2, 1}, {9, 1, 2, 1},
            {3, 2, 2, 1}, {11, 2, 2, 1},
            {1, 3, 2, 1}, {13, 3, 2, 1},
            {1, 4, 1, 8}, {14, 4, 1, 8},
            {1, 12, 2, 1}, {13, 12, 2, 1},
            {3, 13, 2, 1}, {11, 13, 2, 1},
            {5, 14, 2, 1}, {9, 14, 2, 1},
            {7, 15, 2, 1},
    };

    private EquipmentLayout() {
    }

    /** Der linke Rand von Rahmen i auf dem Bildschirm (alle vier stehen gleich weit links) */
    public static int slotX(int leftPos) {
        return leftPos + FRAME_X;
    }

    /** Der obere Rand von Rahmen i auf dem Bildschirm */
    public static int slotY(int topPos, int i) {
        return topPos + FRAME_Y + i * SLOT;
    }

    /** Der linke Rand des Rahmens, den der Zweithand-Platz an seinem neuen Ort bekommt (im Fenster, nicht auf dem Bildschirm) */
    public static int movedOffhandFrameX() {
        return OFFHAND_X + SHIFT - 1;
    }

    /** Der obere Rand desselben Rahmens (im Fenster) */
    public static int offhandFrameY() {
        return OFFHAND_Y - 1;
    }

    /** Welcher Platz (0 bis 3) liegt unter dem Zeiger (Bildschirmkoordinaten), oder -1 */
    public static int slotAt(int leftPos, int topPos, double mouseX, double mouseY) {
        double dx = mouseX - slotX(leftPos);
        double dy = mouseY - slotY(topPos, 0);
        if (dx < 0 || dx >= SLOT || dy < 0) return -1;
        int i = (int) (dy / SLOT);
        return i < EquipmentRules.SLOTS ? i : -1;
    }
}
