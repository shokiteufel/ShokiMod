package com.shokiteufel.shokimod.equipment;

/**
 * Wo die vier Equipment-Plaetze neben dem Inventarfenster stehen - nur Ganzzahlen, ohne Spieltypen: Es wird in jedem Bild gerechnet und darf nichts anlegen.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}). Von dort stammt die Reihenfolge: vier Plaetze untereinander, von oben nach unten
 * Halskette, Umhang, Guertel, Handschuhe. Die Umsetzung ist eigene Arbeit: Skyblocker ersetzt das ganze Inventarfenster durch ein eigenes und legt die Plaetze
 * hinein (neben die Spielfigur); hier bleibt das Fenster, wie es ist, und die Plaetze stehen in einem schmalen Streifen links daneben (rechts davon zeichnet
 * das Spiel die Statuseffekte). Ist das Rezeptbuch offen, rueckt der Streifen links neben das Buch.
 *
 * Die Zahlen sind die des Spiels: Das Inventarfenster ist 176 breit, ein Platz 18, der Gegenstand darin 16; das Rezeptbuch ist 147 breit und schliesst links an
 * das Fenster an.
 */
public final class EquipmentLayout {

    /** Wie breit das Inventarfenster des Spiels ist (die Breite steht in einem geschuetzten Feld, an das hier niemand heran will) */
    public static final int IMAGE_WIDTH = 176;
    /** Ein Platz mit seinem Rahmen: achtzehn mal achtzehn */
    public static final int SLOT = 18;
    /** Der Rand zwischen dem Platz und dem Rand des Streifens */
    public static final int PAD = 4;
    public static final int PANEL_WIDTH = SLOT + 2 * PAD;
    public static final int PANEL_HEIGHT = SLOT * EquipmentRules.SLOTS + 2 * PAD;
    /** Der Abstand zwischen dem Streifen und dem Fenster (oder dem Rezeptbuch) */
    public static final int GAP = 2;
    public static final int RECIPE_BOOK_WIDTH = 147;
    /** Ab dieser Verschiebung des Fensters gegen die Mitte gilt das Rezeptbuch als offen (es verschiebt das Fenster um fast achtzig) */
    static final int RECIPE_BOOK_SHIFT = 8;
    /** Kein Platz fuer den Streifen: Er wuerde ueber den linken Rand des Bildschirms ragen */
    public static final int NO_ROOM = Integer.MIN_VALUE;

    private EquipmentLayout() {
    }

    /** Ist das Rezeptbuch offen? Dann sitzt das Fenster nicht mehr in der Mitte des Bildschirms, sondern ein gutes Stueck weiter rechts */
    static boolean recipeBookOpen(int leftPos, int screenWidth) {
        return leftPos - (screenWidth - IMAGE_WIDTH) / 2 > RECIPE_BOOK_SHIFT;
    }

    /**
     * Der linke Rand des Streifens, oder {@link #NO_ROOM}, wenn links neben dem Fenster kein Platz ist.
     *
     * @param leftPos     der linke Rand des Inventarfensters
     * @param screenWidth die Breite des Bildschirms (in Spielpunkten)
     */
    public static int panelX(int leftPos, int screenWidth) {
        int right = leftPos - GAP - (recipeBookOpen(leftPos, screenWidth) ? RECIPE_BOOK_WIDTH + 1 : 0);
        int x = right - PANEL_WIDTH;
        return x >= 0 ? x : NO_ROOM;
    }

    /** Der obere Rand des Streifens: auf der Hoehe des Fensters */
    public static int panelY(int topPos) {
        return topPos;
    }

    /** Der linke Rand von Platz i (mit seinem Rahmen) */
    public static int slotX(int panelX) {
        return panelX + PAD;
    }

    /** Der obere Rand von Platz i (mit seinem Rahmen) */
    public static int slotY(int panelY, int i) {
        return panelY + PAD + i * SLOT;
    }

    /** Welcher Platz (0 bis 3) liegt unter dem Zeiger, oder -1 */
    public static int slotAt(int panelX, int panelY, double mouseX, double mouseY) {
        double dx = mouseX - slotX(panelX);
        double dy = mouseY - slotY(panelY, 0);
        if (dx < 0 || dx >= SLOT || dy < 0) return -1;
        int i = (int) (dy / SLOT);
        return i < EquipmentRules.SLOTS ? i : -1;
    }

    /** Liegt der Zeiger irgendwo im Streifen (auch zwischen den Plaetzen und auf dem Rand)? */
    public static boolean inPanel(int panelX, int panelY, double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + PANEL_WIDTH && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT;
    }
}
