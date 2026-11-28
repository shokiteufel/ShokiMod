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
 *
 * <p>Die festen Farben fuellen das Feld gleichmaessig und deckend (seit 2.6.10; vorher ein durchscheinendes Feld mit hartem Rand, das wie ein Leuchten aussah). Dazu
 * gibt es kleine Symbole oben links im Platz ({@link #markIcon}), die nach dem Stueck gezeichnet werden: die Order icons im Basar.
 */
public final class WindowHighlights {

    /** Gruen: fertig, oder das gesuchte Stueck */
    public static final int GREEN = 1;
    /** Gold: ueberboten */
    public static final int GOLD = 2;

    /**
     * Die zwei festen Farben fuellen das ganze Feld gleichmaessig und deckend, ohne Rand - wie bei SkyHanni ({@code Slot.highlight}: ein Rechteck 16 mal 16 in
     * {@code LorenzColor.GREEN} (85, 255, 85) und {@code GOLD} (255, 170, 0)). Bis 2.6.9 war es ein Drittel Deckkraft mit einem ganz deckenden Rand, und das sah aus
     * wie ein Leuchten um das Feld (Meldung des Users 28.11.: "der Glow gefaellt mir noch nicht, der Kasten soll mit der Farbe gleichmaessig ausgefuellt sein").
     */
    private static final int FILL_GREEN = 0xFF55FF55;
    private static final int FILL_GOLD = 0xFFFFAA00;
    /** Die Deckkraft der Fuellung bei den freien Farben (deren Rand ist ganz deckend): ein Drittel */
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

    /** Alle Markierungen in den festen Farben loeschen, auch die Symbole (die freien Farben bleiben) */
    public static void clear() {
        clearIcons();
        if (count == 0) return;
        java.util.Arrays.fill(kinds, (byte) 0);
        count = 0;
    }

    /** Die Farbe, in der ein Feld der festen Farbe gefuellt wird (ARGB, deckend): {@link #GREEN} oder {@link #GOLD}; sonst 0 */
    static int fillOfKind(int kind) {
        return kind == GREEN ? FILL_GREEN : kind == GOLD ? FILL_GOLD : 0;
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
        int fill = fillOfKind(kindOf(slot.index));
        // Die festen Farben: das ganze Feld gleichmaessig gefuellt, ohne Rand
        if (fill != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fill);
    }

    /** Die freien Farben: eine durchscheinende Fuellung und ein ganz deckender Rand, einen Punkt breit */
    private static void box(GuiGraphicsExtractor graphics, Slot slot, int fill, int edge) {
        int x = slot.x;
        int y = slot.y;
        graphics.fill(x, y, x + 16, y + 16, fill);
        graphics.fill(x, y, x + 16, y + 1, edge);
        graphics.fill(x, y + 15, x + 16, y + 16, edge);
        graphics.fill(x, y + 1, x + 1, y + 15, edge);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, edge);
    }

    // ------------------------------------------------------------------ Symbole (Order icons)

    /** Teilweise gekauft, und es liegt etwas zum Abholen bereit: ein gelbes Prozentzeichen */
    public static final int ICON_PART = 1;
    /** Ganz erfuellt, bereit zum Abholen: ein gruenes Haeckchen */
    public static final int ICON_FULL = 2;
    /** Laeuft bald ab: ein gelber Wecker */
    public static final int ICON_EXPIRING = 3;
    /** Abgelaufen: ein roter Wecker */
    public static final int ICON_EXPIRED = 4;

    private static final byte[] icons = new byte[MAX_SLOTS];
    private static int iconCount = 0;

    /** Ein kleines Zeichen in die linke obere Ecke eines Platzes setzen; Nummern ausserhalb des Fensters und unbekannte Zeichen werden uebergangen */
    public static void markIcon(int slotIndex, int icon) {
        if (slotIndex < 0 || slotIndex >= MAX_SLOTS || icon < ICON_PART || icon > ICON_EXPIRED) return;
        if (icons[slotIndex] == 0) iconCount++;
        icons[slotIndex] = (byte) icon;
    }

    /** Alle Symbole loeschen */
    public static void clearIcons() {
        if (iconCount == 0) return;
        java.util.Arrays.fill(icons, (byte) 0);
        iconCount = 0;
    }

    /** Wie viele Plaetze gerade ein Symbol tragen */
    public static int iconCount() {
        return iconCount;
    }

    /** Das Symbol eines Platzes ({@link #ICON_PART} ... {@link #ICON_EXPIRED}) oder 0 */
    public static int iconOf(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_SLOTS ? icons[slotIndex] : 0;
    }

    /** Der Text zu einem Symbol, fett und in der Farbe von Skyblockers Basar-Hilfe; einmal gebaut, nicht je Bild */
    private static final class Icons {
        private static final int RED = 0xE60B1E;
        private static final int YELLOW = 0xE6BA0B;
        private static final int GREEN_TEXT = 0x1EE60B;

        static final net.minecraft.network.chat.Component PART = text("%", YELLOW);
        static final net.minecraft.network.chat.Component FULL = text("✅", GREEN_TEXT);
        static final net.minecraft.network.chat.Component EXPIRING = text("⏰", YELLOW);
        static final net.minecraft.network.chat.Component EXPIRED = text("⏰", RED);

        private static net.minecraft.network.chat.Component text(String glyph, int rgb) {
            return net.minecraft.network.chat.Component.literal(glyph).withColor(rgb).withStyle(net.minecraft.ChatFormatting.BOLD);
        }

        static net.minecraft.network.chat.Component of(int icon) {
            return switch (icon) {
                case ICON_PART -> PART;
                case ICON_FULL -> FULL;
                case ICON_EXPIRING -> EXPIRING;
                case ICON_EXPIRED -> EXPIRED;
                default -> null;
            };
        }
    }

    /** Vom Einsprung gerufen, nachdem das Stueck gezeichnet ist: Das Symbol liegt oben links im Platz, kleiner als die Schrift sonst, und ueber dem Stueck */
    public static void onSlotDrawn(GuiGraphicsExtractor graphics, Slot slot) {
        if (iconCount == 0 || slot.container instanceof Inventory) return;
        net.minecraft.network.chat.Component text = Icons.of(iconOf(slot.index));
        if (text == null) return;
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(slot.x, slot.y);
        graphics.pose().scale(0.75f, 0.75f);
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }
}
