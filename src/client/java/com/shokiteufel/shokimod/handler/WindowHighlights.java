package com.shokiteufel.shokimod.handler;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Farbige Quadrate hinter einzelnen Plaetzen eines offenen Behaelters - fuer die Hilfen im Basar (Purchase helper, Order helper) und im Auktionshaus
 * (Highlight Auctions, Highlight Underbid Auctions).
 *
 * Wer etwas markieren will, setzt mit {@link #mark} die Plaetze (Nummer im Fenster) und loescht sie mit {@link #clear}, sobald das Fenster zu ist. Das sind die zwei
 * festen Farben {@link #GREEN} und {@link #GOLD}. Daneben kann ein Platz eine freie Farbe tragen ({@link #markColour}, ARGB; geloescht mit {@link #clearColours}): die
 * Farben, die man im Farbwaehler der Einstellungen gewaehlt hat. Beide Listen sind getrennt, damit eine Hilfe die Markierungen der anderen nicht loescht, wenn sie
 * ihr Fenster verlaesst (der Basar loescht seine in jedem Tick, in dem er kein Fenster des Basars sieht). Traegt ein Platz beides, gilt die freie Farbe.
 *
 * Gezeichnet wird vom Einsprung in {@code AbstractContainerScreen.extractSlot} ({@code SlotBackgroundMixin}) vor dem Stueck, damit es oben liegt; nur Plaetze
 * des Behaelters, nicht die des eigenen Inventars. Ohne Markierung kostet der Aufruf je Platz einen Zahlenvergleich.
 */
public final class WindowHighlights {

    /** Gruen: fertig, oder das gesuchte Stueck */
    public static final int GREEN = 1;
    /** Gold: ueberboten */
    public static final int GOLD = 2;

    private static final int FILL_GREEN = 0x5500FF00;
    private static final int EDGE_GREEN = 0xFF00FF00;
    private static final int FILL_GOLD = 0x55FFAA00;
    private static final int EDGE_GOLD = 0xFFFFAA00;
    /** Die Deckkraft der Fuellung (der Rand ist ganz deckend): wie bei Gruen und Gold */
    private static final int FILL_ALPHA = 0x55;
    /** Mehr Plaetze hat auch die groesste Kiste nicht (sechs Reihen und das Inventar) */
    private static final int MAX_SLOTS = 128;

    private static final byte[] kinds = new byte[MAX_SLOTS];
    private static int count = 0;
    /** Die freien Farben je Platz (ARGB), 0 = keine */
    private static final int[] colours = new int[MAX_SLOTS];
    private static int colourCount = 0;

    private WindowHighlights() {
    }

    /** Einen Platz in einer der festen Farben markieren; Nummern ausserhalb des Fensters werden uebergangen */
    public static void mark(int slotIndex, int kind) {
        if (slotIndex < 0 || slotIndex >= MAX_SLOTS || (kind != GREEN && kind != GOLD)) return;
        if (kinds[slotIndex] == 0) count++;
        kinds[slotIndex] = (byte) kind;
    }

    /** Alle Markierungen in den festen Farben loeschen (die freien Farben bleiben) */
    public static void clear() {
        if (count == 0) return;
        java.util.Arrays.fill(kinds, (byte) 0);
        count = 0;
    }

    /** Wie viele Plaetze gerade in einer der festen Farben markiert sind */
    public static int count() {
        return count;
    }

    /** Wie ein Platz markiert ist: {@link #GREEN}, {@link #GOLD} oder 0 */
    public static int kindOf(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_SLOTS ? kinds[slotIndex] : 0;
    }

    // ------------------------------------------------------------------ freie Farben

    /**
     * Einen Platz in einer freien Farbe markieren. {@code argb} ist die Farbe des Randes samt Deckkraft ({@code 0xFF55FF55}); die Fuellung hat dieselbe Farbe mit
     * einem Drittel der Deckkraft. Eine Farbe ohne Deckkraft (Alpha 0) und Nummern ausserhalb des Fensters werden uebergangen.
     */
    public static void markColour(int slotIndex, int argb) {
        if (slotIndex < 0 || slotIndex >= MAX_SLOTS || (argb >>> 24) == 0) return;
        if (colours[slotIndex] == 0) colourCount++;
        colours[slotIndex] = argb;
    }

    /** Alle Markierungen in freien Farben loeschen (die festen bleiben) */
    public static void clearColours() {
        if (colourCount == 0) return;
        java.util.Arrays.fill(colours, 0);
        colourCount = 0;
    }

    /** Wie viele Plaetze gerade in einer freien Farbe markiert sind */
    public static int colourCount() {
        return colourCount;
    }

    /** Die freie Farbe eines Platzes (ARGB) oder 0 */
    public static int colourOf(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_SLOTS ? colours[slotIndex] : 0;
    }

    /** Die Fuellung zu einer freien Farbe: dieselbe Farbe, die Deckkraft im Verhaeltnis wie bei Gruen und Gold (0x55 von 0xFF) */
    static int fillOf(int argb) {
        int alpha = (argb >>> 24) * FILL_ALPHA / 0xFF;
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Der Rand zu einer freien Farbe: die Farbe selbst */
    static int edgeOf(int argb) {
        return argb;
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Vom Einsprung gerufen, kurz bevor das Stueck im Platz gezeichnet wird */
    public static void onSlot(GuiGraphicsExtractor graphics, Slot slot) {
        if ((count == 0 && colourCount == 0) || slot.container instanceof Inventory) return;
        int colour = colourOf(slot.index);
        if (colour != 0) {
            box(graphics, slot, fillOf(colour), edgeOf(colour));
            return;
        }
        int kind = kindOf(slot.index);
        if (kind == 0) return;
        box(graphics, slot, kind == GREEN ? FILL_GREEN : FILL_GOLD, kind == GREEN ? EDGE_GREEN : EDGE_GOLD);
    }

    private static void box(GuiGraphicsExtractor graphics, Slot slot, int fill, int edge) {
        int x = slot.x;
        int y = slot.y;
        graphics.fill(x, y, x + 16, y + 16, fill);
        // Der Rand, einen Punkt breit, ganz deckend
        graphics.fill(x, y, x + 16, y + 1, edge);
        graphics.fill(x, y + 15, x + 16, y + 16, edge);
        graphics.fill(x, y + 1, x + 1, y + 15, edge);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, edge);
    }
}
