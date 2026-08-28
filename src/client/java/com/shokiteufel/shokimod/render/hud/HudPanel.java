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

    /**
     * Einmal ausgemessen, dann behalten.
     *
     * Schriftbreiten sind teuer - Minecraft zerlegt dafuer die Zeichenkette und schlaegt
     * jede Glyphe nach. Gezeichnet wird in jedem Bild, die Zeilen aendern sich dabei
     * aber nicht: ein Kasten wird gebaut und danach nur noch angezeigt.
     */
    private int measuredWidth = -1;
    private int[] valueWidths = null;

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
        measure(font);
        return measuredWidth;
    }

    private void measure(Font font) {
        if (measuredWidth >= 0) return;

        valueWidths = new int[rows.size()];
        int widest = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            valueWidths[i] = row.value().isEmpty() ? 0 : font.width(row.value());

            int width = switch (row.kind()) {
                case TITLE, TEXT -> font.width(row.label());
                case PAIR -> font.width(row.label()) + GUTTER + valueWidths[i];
                case BAR -> font.width(row.label()) + GUTTER + BAR_WIDTH + GUTTER + valueWidths[i];
                case BLANK -> 0;
            };
            widest = Math.max(widest, width);
        }
        measuredWidth = widest;
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

    /**
     * Deckkraft auf eine Farbe rechnen.
     *
     * Angewendet auf Hintergrund und Schrift gleichermassen - waere nur der Hintergrund
     * durchsichtig, stuende die Schrift hart darueber und der Kasten wirkte kaputt statt
     * dezent.
     */
    private static int withAlpha(int colour, float alpha) {
        int base = opaque(colour);
        int a = Math.round(((base >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (base & 0xFFFFFF);
    }

    /**
     * Welche Zeile liegt unter dem Zeiger? -1, wenn keine.
     *
     * Gerechnet wird in den Massen des Kastens, deshalb wird die Skalierung
     * herausgerechnet - gezeichnet wird er ja vergroessert.
     */
    public int rowAt(Font font, int left, int top, float scale, double mouseX, double mouseY) {
        if (rows.isEmpty() || scale <= 0) return -1;

        double localX = (mouseX - left) / scale;
        double localY = (mouseY - top) / scale;
        if (localX < 0 || localX > width(font)) return -1;

        int index = (int) Math.floor((localY - PADDING) / LINE_HEIGHT);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int left, int top) {
        render(graphics, font, left, top, 1.0f);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int left, int top, float alpha) {
        if (rows.isEmpty()) return;

        int content = contentWidth(font);
        int right = left + content + PADDING * 2;
        graphics.fill(left, top, right, top + height(), withAlpha(BACKGROUND, alpha));
        graphics.fill(left, top, right, top + 1, withAlpha(TOP_EDGE, alpha));

        int x = left + PADDING;
        int y = top + PADDING;
        for (int i = 0; i < rows.size(); i++) {
            draw(graphics, font, rows.get(i), x, y, content, valueWidths[i], alpha);
            y += LINE_HEIGHT;
        }
    }

    private void draw(GuiGraphicsExtractor graphics, Font font, Row row, int x, int y,
                      int content, int valueWidth, float alpha) {
        switch (row.kind()) {
            case BLANK -> {
            }
            case TITLE, TEXT -> graphics.text(font, row.label(), x, y, withAlpha(row.labelColour(), alpha), true);
            case PAIR -> {
                graphics.text(font, row.label(), x, y, withAlpha(row.labelColour(), alpha), true);
                int valueX = x + content - valueWidth;
                graphics.text(font, row.value(), valueX, y, withAlpha(row.valueColour(), alpha), true);
            }
            case BAR -> {
                graphics.text(font, row.label(), x, y, withAlpha(row.labelColour(), alpha), true);

                // Der Balken sitzt rechts, direkt vor der Zahl - so stehen alle Balken
                // untereinander auf gleicher Hoehe, egal wie lang die Beschriftung ist
                int barLeft = x + content - valueWidth - GUTTER - BAR_WIDTH;
                int barY = y + 2;
                graphics.fill(barLeft, barY, barLeft + BAR_WIDTH, barY + 5, withAlpha(0xFF303030, alpha));
                if (row.max() > 0 && row.current() > 0) {
                    int filled = Math.max(1, BAR_WIDTH * Math.min(row.current(), row.max()) / row.max());
                    graphics.fill(barLeft, barY, barLeft + filled, barY + 5,
                            withAlpha(row.valueColour(), alpha));
                }

                graphics.text(font, row.value(), x + content - valueWidth, y,
                        withAlpha(HudColours.WHITE, alpha), true);
            }
        }
    }
}
