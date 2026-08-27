package com.shokiteufel.shokimod.render.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Kasten mit Zeilen, wie ihn crittermod fuer seine beiden Anzeigen benutzt.
 *
 * Vier Zeilenarten reichen aus: Ueberschrift, freier Text, Beschriftung mit Wert
 * rechtsbuendig, und ein Fortschrittsbalken. Der Kasten misst seine Breite selbst
 * am laengsten Inhalt, damit nichts abgeschnitten wird und nichts flattert.
 */
public class HudPanel {

    private static final int PADDING = 4;
    /** Abstand zwischen Beschriftung und Wert in einer Paar-Zeile */
    private static final int GUTTER = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_WIDTH = 60;
    /**
     * Kraeftig deckend, nicht bloss angedeutet.
     *
     * Bei halbdurchsichtigem Grund scheint die Welt durch und frisst den Kontrast -
     * ueber hellem Gelaende wirkt derselbe Kasten dann matt. Der Wert liegt bewusst
     * in der Groessenordnung, die Minecraft fuer seine eigenen Bildschirme nimmt.
     */
    private static final int BACKGROUND = 0xD0101010;
    /** Feine helle Kante oben, damit der Kasten eine Form hat statt zu verlaufen */
    private static final int TOP_EDGE = 0x50FFFFFF;

    private enum Kind { TITLE, TEXT, PAIR, BAR, BLANK }

    private record Row(Kind kind, String label, String value,
                       int labelColour, int valueColour, int current, int max) {
    }

    private final List<Row> rows = new ArrayList<>();

    public HudPanel title(String text, int colour) {
        rows.add(new Row(Kind.TITLE, text, "", colour, colour, 0, 0));
        return this;
    }

    public HudPanel line(String text, int colour) {
        rows.add(new Row(Kind.TEXT, text, "", colour, colour, 0, 0));
        return this;
    }

    public HudPanel pair(String label, String value, int labelColour, int valueColour) {
        rows.add(new Row(Kind.PAIR, label, value, labelColour, valueColour, 0, 0));
        return this;
    }

    public HudPanel bar(String label, int current, int max, int labelColour, int barColour) {
        rows.add(new Row(Kind.BAR, label, current + "/" + max, labelColour, barColour, current, max));
        return this;
    }

    public HudPanel blank() {
        rows.add(new Row(Kind.BLANK, "", "", 0, 0, 0, 0));
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int width(Font font) {
        return contentWidth(font) + PADDING * 2;
    }

    public int height() {
        return rows.size() * LINE_HEIGHT + PADDING * 2;
    }

    private int contentWidth(Font font) {
        int widest = 0;
        for (Row row : rows) {
            int width = switch (row.kind()) {
                case TITLE, TEXT -> font.width(row.label());
                case PAIR -> font.width(row.label()) + GUTTER + font.width(row.value());
                case BAR -> font.width(row.label()) + GUTTER + BAR_WIDTH + GUTTER
                        + font.width(row.value());
                case BLANK -> 0;
            };
            widest = Math.max(widest, width);
        }
        return widest;
    }

    /**
     * Deckend machen, falls das Alpha-Byte fehlt.
     *
     * 26.1 verwirft Text mit Alpha 0 ersatzlos - aus 0xBBBBBB wird nicht etwa graue
     * Schrift, sondern gar keine. Damit das nicht bei jedem neuen Aufrufer wieder
     * passiert, wird es hier einmal zentral abgefangen.
     */
    private static int opaque(int colour) {
        return (colour >>> 24) == 0 ? 0xFF000000 | colour : colour;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int left, int top) {
        if (rows.isEmpty()) return;

        int content = contentWidth(font);
        int right = left + content + PADDING * 2;
        graphics.fill(left, top, right, top + height(), BACKGROUND);
        graphics.fill(left, top, right, top + 1, TOP_EDGE);

        int x = left + PADDING;
        int y = top + PADDING;
        for (Row row : rows) {
            draw(graphics, font, row, x, y, content);
            y += LINE_HEIGHT;
        }
    }

    private void draw(GuiGraphicsExtractor graphics, Font font, Row row, int x, int y, int content) {
        switch (row.kind()) {
            case BLANK -> {
            }
            case TITLE, TEXT -> graphics.text(font, row.label(), x, y, opaque(row.labelColour()), true);
            case PAIR -> {
                graphics.text(font, row.label(), x, y, opaque(row.labelColour()), true);
                int valueX = x + content - font.width(row.value());
                graphics.text(font, row.value(), valueX, y, opaque(row.valueColour()), true);
            }
            case BAR -> {
                graphics.text(font, row.label(), x, y, opaque(row.labelColour()), true);

                // Der Balken sitzt rechts, direkt vor der Zahl - so stehen alle Balken
                // untereinander auf gleicher Hoehe, egal wie lang die Beschriftung ist
                int valueWidth = font.width(row.value());
                int barLeft = x + content - valueWidth - GUTTER - BAR_WIDTH;
                int barY = y + 2;
                graphics.fill(barLeft, barY, barLeft + BAR_WIDTH, barY + 5, 0xFF303030);
                if (row.max() > 0 && row.current() > 0) {
                    int filled = Math.max(1, BAR_WIDTH * Math.min(row.current(), row.max()) / row.max());
                    graphics.fill(barLeft, barY, barLeft + filled, barY + 5,
                            opaque(row.valueColour()));
                }

                graphics.text(font, row.value(), x + content - valueWidth, y, 0xFFFFFFFF, true);
            }
        }
    }
}
