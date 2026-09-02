package com.shokiteufel.shokimod.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Die Einblendung fuer seltene Funde, in fuenf Stilen.
 *
 * Getrennt vom {@link AlertBanner}: der bleibt, wie er ist, fuer SHINY-Critter und
 * Chatregeln. Dieses Banner hier gehoert den Funden und darf anders aussehen -
 * welcher Stil, entscheidet die Einstellung; der Test-Reiter zeigt alle fuenf.
 *
 * Wie beim AlertBanner gibt es keinen Timer: nur ein Zeitstempel, aus dem beim
 * Zeichnen Deckkraft und Position folgen.
 */
public final class DropBanner {

    public enum Style {
        CLASSIC("1 - Classic band"),
        COMPACT("2 - Compact strip"),
        TITLE("3 - Big title"),
        CARD("4 - Side card"),
        RIBBON("5 - Top ribbon");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        public static Style byNumber(int number) {
            Style[] all = values();
            return all[Math.min(Math.max(number - 1, 0), all.length - 1)];
        }
    }

    private static final long FADE_MILLIS = 900L;
    private static final long SLIDE_MILLIS = 250L;

    private static Style style = Style.CLASSIC;
    private static String headline = "";
    private static String worth = "";
    private static String tierLabel = "";
    private static int tint = 0xFFFFFF;
    private static long displayMillis = 3000L;
    private static long shownAtMillis = 0L;

    private DropBanner() {
    }

    /** Blendet ein Banner ein. Ein zweiter Aufruf ersetzt das laufende. */
    public static void show(Style chosen, String headlineText, String worthText, String tierText,
                            int rgb, long millis) {
        style = chosen == null ? Style.CLASSIC : chosen;
        headline = headlineText == null ? "" : headlineText;
        worth = worthText == null ? "" : worthText;
        tierLabel = tierText == null ? "" : tierText;
        tint = rgb & 0xFFFFFF;
        displayMillis = Math.max(millis, FADE_MILLIS + 300L);
        shownAtMillis = System.currentTimeMillis();
    }

    /** Der Test-Reiter: ein Beispiel im gewuenschten Stil, laenger als im Spiel */
    public static void preview(Style chosen) {
        show(chosen, "+ 3x Ghost Shard", "(10.5k)", "Tier 1 - " + chosen, 0xFFD700, 4500L);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (shownAtMillis == 0L) return;

        long age = System.currentTimeMillis() - shownAtMillis;
        if (age > displayMillis) {
            shownAtMillis = 0L;
            return;
        }

        float alpha = 1.0f;
        long fadeStart = displayMillis - FADE_MILLIS;
        if (age > fadeStart) alpha = 1.0f - (age - fadeStart) / (float) FADE_MILLIS;
        float slide = Math.min(1.0f, age / (float) SLIDE_MILLIS);

        Font font = Minecraft.getInstance().font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        switch (style) {
            case CLASSIC -> classic(graphics, font, width, height, alpha);
            case COMPACT -> compact(graphics, font, width, height, alpha);
            case TITLE -> title(graphics, font, width, height, alpha);
            case CARD -> card(graphics, font, width, height, alpha, slide);
            case RIBBON -> ribbon(graphics, font, width, height, alpha, slide);
        }
    }

    // ---- Stil 1: das Band, wie es SHINY nutzt ----

    private static void classic(GuiGraphicsExtractor g, Font font, int width, int height, float alpha) {
        int text = argb(alpha, 0xFFFFFF);
        int accent = argb(alpha, tint);
        int band = (int) (alpha * 140) << 24;

        int top = height / 4;
        int bottom = top + 54;
        g.fill(0, top, width, bottom, band);
        g.fill(0, top, width, top + 1, accent);
        g.fill(0, bottom - 1, width, bottom, accent);

        scaledCentered(g, font, headline, width / 2, top + 6, 3.0f, accent);
        scaledCentered(g, font, worth, width / 2, top + 30, 1.6f, text);
        g.centeredText(font, tierLabel, width / 2, bottom + 4, argb(alpha, 0xAAAAAA));
    }

    // ---- Stil 2: ein schmaler Streifen ueber der Hotbar ----

    private static void compact(GuiGraphicsExtractor g, Font font, int width, int height, float alpha) {
        float scale = 1.5f;
        String line = headline + (worth.isEmpty() ? "" : "  " + worth);
        int textWidth = (int) (font.width(line) * scale);
        int boxHalf = textWidth / 2 + 10;
        int centreY = (int) (height * 0.62f);
        int top = centreY - 10;
        int bottom = centreY + 10;

        g.fill(width / 2 - boxHalf, top, width / 2 + boxHalf, bottom, (int) (alpha * 170) << 24);
        g.fill(width / 2 - boxHalf, bottom - 2, width / 2 + boxHalf, bottom, argb(alpha, tint));
        scaledCentered(g, font, line, width / 2, top + 4, scale, argb(alpha, 0xFFFFFF));
        if (!tierLabel.isEmpty()) {
            g.centeredText(font, tierLabel, width / 2, bottom + 3, argb(alpha, 0x999999));
        }
    }

    // ---- Stil 3: gross und frei, wie ein Minecraft-Titel ----

    private static void title(GuiGraphicsExtractor g, Font font, int width, int height, float alpha) {
        int centreY = height / 2;
        scaledCentered(g, font, headline, width / 2, centreY - 34, 4.0f, argb(alpha, tint));
        scaledCentered(g, font, worth, width / 2, centreY + 6, 2.0f, argb(alpha, 0xFFFFFF));
        if (!tierLabel.isEmpty()) {
            g.centeredText(font, tierLabel, width / 2, centreY + 28, argb(alpha, 0xAAAAAA));
        }
    }

    // ---- Stil 4: eine Karte am rechten Rand, die hereinfaehrt ----

    private static void card(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, float slide) {
        float scale = 1.3f;
        int inner = Math.max((int) (font.width(headline) * scale), Math.max(font.width(worth), font.width(tierLabel)));
        int cardWidth = inner + 22;
        int cardHeight = 44;
        // Erst draussen, dann an den Rand: die Bewegung faellt mehr auf als jede Farbe
        int right = width + (int) ((1.0f - slide) * cardWidth);
        int left = right - cardWidth;
        int top = height / 3;

        g.fill(left, top, right, top + cardHeight, (int) (alpha * 200) << 24);
        g.fill(left, top, left + 3, top + cardHeight, argb(alpha, tint));

        int x = left + 10;
        scaledText(g, font, headline, x, top + 6, scale, argb(alpha, 0xFFFFFF));
        g.text(font, worth, x, top + 22, argb(alpha, tint), false);
        g.text(font, tierLabel, x, top + 33, argb(alpha, 0x999999), false);
    }

    // ---- Stil 5: ein farbiges Band ganz oben ----

    private static void ribbon(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, float slide) {
        int ribbonHeight = 30;
        // Von oben herabgelassen
        int top = -ribbonHeight + (int) (slide * (ribbonHeight + 6));
        int bottom = top + ribbonHeight;

        g.fill(0, top, width, bottom, (int) (alpha * 150) << 24 | tint);
        g.fill(0, bottom, width, bottom + 1, argb(alpha, 0x000000));

        scaledCentered(g, font, headline, width / 2, top + 7, 2.0f, argb(alpha, 0x101010));
        if (!worth.isEmpty()) {
            g.text(font, worth, width - font.width(worth) - 8, top + 11, argb(alpha, 0x101010), false);
        }
        if (!tierLabel.isEmpty()) {
            g.text(font, tierLabel, 8, top + 11, argb(alpha, 0x101010), false);
        }
    }

    // ---- Helfer ----

    private static void scaledCentered(GuiGraphicsExtractor g, Font font, String text, int centreX, int y,
                                       float scale, int colour) {
        if (text.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.centeredText(font, text, (int) (centreX / scale), (int) (y / scale), colour);
        g.pose().popMatrix();
    }

    private static void scaledText(GuiGraphicsExtractor g, Font font, String text, int x, int y,
                                   float scale, int colour) {
        if (text.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.text(font, text, (int) (x / scale), (int) (y / scale), colour, true);
        g.pose().popMatrix();
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
