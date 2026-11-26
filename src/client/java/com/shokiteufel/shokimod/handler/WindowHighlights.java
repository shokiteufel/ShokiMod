package com.shokiteufel.shokimod.handler;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Farbige Quadrate hinter einzelnen Plaetzen eines offenen Behaelters - fuer die Hilfen im Basar (Purchase helper, Order helper).
 *
 * Wer etwas markieren will, setzt mit {@link #mark} die Plaetze (Nummer im Fenster) und loescht sie mit {@link #clear}, sobald das Fenster zu ist.
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
    /** Mehr Plaetze hat auch die groesste Kiste nicht (sechs Reihen und das Inventar) */
    private static final int MAX_SLOTS = 128;

    private static final byte[] kinds = new byte[MAX_SLOTS];
    private static int count = 0;

    private WindowHighlights() {
    }

    /** Einen Platz markieren; Nummern ausserhalb des Fensters werden uebergangen */
    public static void mark(int slotIndex, int kind) {
        if (slotIndex < 0 || slotIndex >= MAX_SLOTS || (kind != GREEN && kind != GOLD)) return;
        if (kinds[slotIndex] == 0) count++;
        kinds[slotIndex] = (byte) kind;
    }

    /** Alle Markierungen loeschen */
    public static void clear() {
        if (count == 0) return;
        java.util.Arrays.fill(kinds, (byte) 0);
        count = 0;
    }

    /** Wie viele Plaetze gerade markiert sind */
    public static int count() {
        return count;
    }

    /** Wie ein Platz markiert ist: {@link #GREEN}, {@link #GOLD} oder 0 */
    public static int kindOf(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_SLOTS ? kinds[slotIndex] : 0;
    }

    /** Vom Einsprung gerufen, kurz bevor das Stueck im Platz gezeichnet wird */
    public static void onSlot(GuiGraphicsExtractor graphics, Slot slot) {
        if (count == 0 || slot.container instanceof Inventory) return;
        int kind = kindOf(slot.index);
        if (kind == 0) return;
        int fill = kind == GREEN ? FILL_GREEN : FILL_GOLD;
        int edge = kind == GREEN ? EDGE_GREEN : EDGE_GOLD;
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
