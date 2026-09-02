package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * Die Einblendung fuer seltene Funde, in zehn Stilen.
 *
 * Getrennt vom {@link AlertBanner}: der bleibt, wie er ist, fuer SHINY-Critter und
 * Chatregeln. Dieses Banner hier gehoert den Funden und darf anders aussehen -
 * welcher Stil, entscheidet die Einstellung; der Test-Reiter zeigt alle zehn.
 *
 * Die Stile 1 bis 5 haben ihren festen Platz. Die Stile 6 bis 10 sitzen dort, wo
 * die Regler fuer X und Y sie hinschieben, in der eingestellten Groesse. Die
 * Farbe kommt von der Stufe - ausser es steht eine eigene in der Einstellung.
 *
 * Wie beim AlertBanner gibt es keinen Timer: nur ein Zeitstempel, aus dem beim
 * Zeichnen Deckkraft und Position folgen. Die Regler werden beim Zeichnen gelesen,
 * nicht beim Ausloesen - so sieht man beim Schieben sofort, wohin es geht.
 */
public final class DropBanner {

    public enum Style {
        CLASSIC("1 - Classic band"),
        COMPACT("2 - Compact strip"),
        TITLE("3 - Big title"),
        CARD("4 - Side card"),
        RIBBON("5 - Top ribbon"),
        OUTLINE("6 - Outlined text (free)"),
        BOXED("7 - Boxed (free)"),
        TWO_TONE("8 - Two tone (free)"),
        POP("9 - Pop-in (free)"),
        MINIMAL("10 - Minimal dot (free)");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        /** Die freien Stile folgen den Reglern fuer Ort und Groesse */
        public boolean free() {
            return ordinal() >= OUTLINE.ordinal();
        }
    }

    private static final long FADE_MILLIS = 900L;
    private static final long SLIDE_MILLIS = 250L;
    private static final long POP_MILLIS = 220L;

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
        Look look = Look.fromConfig(tint);

        switch (style) {
            case CLASSIC -> classic(graphics, font, width, height, alpha, look);
            case COMPACT -> compact(graphics, font, width, height, alpha, look);
            case TITLE -> title(graphics, font, width, height, alpha, look);
            case CARD -> card(graphics, font, width, height, alpha, slide, look);
            case RIBBON -> ribbon(graphics, font, width, height, alpha, slide, look);
            case OUTLINE -> outline(graphics, font, width, height, alpha, look);
            case BOXED -> boxed(graphics, font, width, height, alpha, look);
            case TWO_TONE -> twoTone(graphics, font, width, height, alpha, look);
            case POP -> pop(graphics, font, width, height, alpha, age, look);
            case MINIMAL -> minimal(graphics, font, width, height, alpha, look);
        }
    }

    /** Die Regler, beim Zeichnen gelesen: Farbe, Groesse, Schatten, Ort */
    private record Look(int colour, float scale, boolean shadow, float x, float y) {

        static Look fromConfig(int tierColour) {
            ModConfig.RareLootCategory cfg = ModConfig.INSTANCE.chat.rareLoot;
            int colour = parseColour(cfg.bannerColour, tierColour);
            float scale = clamp(Float.isNaN(cfg.bannerScale) ? 1.0f : cfg.bannerScale, 0.5f, 3.0f);
            float x = clamp(Float.isNaN(cfg.bannerX) ? 0.5f : cfg.bannerX, 0.0f, 1.0f);
            float y = clamp(Float.isNaN(cfg.bannerY) ? 0.3f : cfg.bannerY, 0.0f, 1.0f);
            return new Look(colour, scale, cfg.bannerShadow, x, y);
        }
    }

    /** "FFD700" oder "#ffd700" - leer oder unlesbar heisst: Farbe der Stufe */
    static int parseColour(String text, int fallback) {
        if (text == null) return fallback;
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return fallback;
        try {
            return Integer.parseInt(hex.toUpperCase(Locale.ROOT), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- Stil 1: das Band, wie es SHINY nutzt ----

    private static void classic(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        int text = argb(alpha, 0xFFFFFF);
        int accent = argb(alpha, look.colour());
        int band = (int) (alpha * 140) << 24;

        int top = height / 4;
        int bottom = top + 54;
        g.fill(0, top, width, bottom, band);
        g.fill(0, top, width, top + 1, accent);
        g.fill(0, bottom - 1, width, bottom, accent);

        scaledCentered(g, font, headline, width / 2, top + 6, 3.0f, accent, look.shadow());
        scaledCentered(g, font, worth, width / 2, top + 30, 1.6f, text, look.shadow());
        g.centeredText(font, tierLabel, width / 2, bottom + 4, argb(alpha, 0xAAAAAA));
    }

    // ---- Stil 2: ein schmaler Streifen ueber der Hotbar ----

    private static void compact(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        float scale = 1.5f;
        String line = headline + (worth.isEmpty() ? "" : "  " + worth);
        int textWidth = (int) (font.width(line) * scale);
        int boxHalf = textWidth / 2 + 10;
        int centreY = (int) (height * 0.62f);
        int top = centreY - 10;
        int bottom = centreY + 10;

        g.fill(width / 2 - boxHalf, top, width / 2 + boxHalf, bottom, (int) (alpha * 170) << 24);
        g.fill(width / 2 - boxHalf, bottom - 2, width / 2 + boxHalf, bottom, argb(alpha, look.colour()));
        scaledCentered(g, font, line, width / 2, top + 4, scale, argb(alpha, 0xFFFFFF), look.shadow());
        if (!tierLabel.isEmpty()) {
            g.centeredText(font, tierLabel, width / 2, bottom + 3, argb(alpha, 0x999999));
        }
    }

    // ---- Stil 3: gross und frei, wie ein Minecraft-Titel ----

    private static void title(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        int centreY = height / 2;
        scaledCentered(g, font, headline, width / 2, centreY - 34, 4.0f, argb(alpha, look.colour()), look.shadow());
        scaledCentered(g, font, worth, width / 2, centreY + 6, 2.0f, argb(alpha, 0xFFFFFF), look.shadow());
        if (!tierLabel.isEmpty()) {
            g.centeredText(font, tierLabel, width / 2, centreY + 28, argb(alpha, 0xAAAAAA));
        }
    }

    // ---- Stil 4: eine Karte am rechten Rand, die hereinfaehrt ----

    private static void card(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, float slide, Look look) {
        float scale = 1.3f;
        int inner = Math.max((int) (font.width(headline) * scale), Math.max(font.width(worth), font.width(tierLabel)));
        int cardWidth = inner + 22;
        int cardHeight = 44;
        int right = width + (int) ((1.0f - slide) * cardWidth);
        int left = right - cardWidth;
        int top = height / 3;

        g.fill(left, top, right, top + cardHeight, (int) (alpha * 200) << 24);
        g.fill(left, top, left + 3, top + cardHeight, argb(alpha, look.colour()));

        int x = left + 10;
        scaledText(g, font, headline, x, top + 6, scale, argb(alpha, 0xFFFFFF), true);
        g.text(font, worth, x, top + 22, argb(alpha, look.colour()), false);
        g.text(font, tierLabel, x, top + 33, argb(alpha, 0x999999), false);
    }

    // ---- Stil 5: ein farbiges Band ganz oben ----

    private static void ribbon(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, float slide, Look look) {
        int ribbonHeight = 30;
        int top = -ribbonHeight + (int) (slide * (ribbonHeight + 6));
        int bottom = top + ribbonHeight;

        g.fill(0, top, width, bottom, (int) (alpha * 150) << 24 | look.colour());
        g.fill(0, bottom, width, bottom + 1, argb(alpha, 0x000000));

        scaledCentered(g, font, headline, width / 2, top + 7, 2.0f, argb(alpha, 0x101010), false);
        if (!worth.isEmpty()) {
            g.text(font, worth, width - font.width(worth) - 8, top + 11, argb(alpha, 0x101010), false);
        }
        if (!tierLabel.isEmpty()) {
            g.text(font, tierLabel, 8, top + 11, argb(alpha, 0x101010), false);
        }
    }

    // ---- Stil 6: Text mit dunklem Rand, ohne Kasten ----

    private static void outline(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        int cx = (int) (width * look.x());
        int cy = (int) (height * look.y());
        float big = 2.4f * look.scale();
        float small = 1.2f * look.scale();

        outlinedCentered(g, font, headline, cx, cy, big, argb(alpha, look.colour()), argb(alpha, 0x000000));
        outlinedCentered(g, font, worth, cx, cy + (int) (big * 11), small, argb(alpha, 0xFFFFFF), argb(alpha, 0x000000));
        if (!tierLabel.isEmpty()) {
            g.centeredText(font, tierLabel, cx, cy + (int) (big * 11 + small * 12), argb(alpha, 0xAAAAAA));
        }
    }

    // ---- Stil 7: ein Kasten mit farbigem Rahmen ----

    private static void boxed(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        int cx = (int) (width * look.x());
        int cy = (int) (height * look.y());
        float big = 2.0f * look.scale();
        float small = 1.1f * look.scale();

        int inner = Math.max((int) (font.width(headline) * big), (int) (font.width(worth) * small));
        int halfWidth = inner / 2 + 14;
        int boxHeight = (int) (big * 10 + small * 10 + 22);
        int top = cy - boxHeight / 2;
        int bottom = top + boxHeight;

        g.fill(cx - halfWidth, top, cx + halfWidth, bottom, (int) (alpha * 190) << 24);
        int frame = argb(alpha, look.colour());
        g.fill(cx - halfWidth, top, cx + halfWidth, top + 2, frame);
        g.fill(cx - halfWidth, bottom - 2, cx + halfWidth, bottom, frame);
        g.fill(cx - halfWidth, top, cx - halfWidth + 2, bottom, frame);
        g.fill(cx + halfWidth - 2, top, cx + halfWidth, bottom, frame);

        scaledCentered(g, font, headline, cx, top + 8, big, argb(alpha, look.colour()), look.shadow());
        scaledCentered(g, font, worth, cx, top + 8 + (int) (big * 10) + 4, small, argb(alpha, 0xFFFFFF), look.shadow());
    }

    // ---- Stil 8: Farbe oben, Weiss unten, ein Strich dazwischen ----

    private static void twoTone(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        int cx = (int) (width * look.x());
        int cy = (int) (height * look.y());
        float big = 2.2f * look.scale();
        float small = 1.4f * look.scale();

        int lineHalf = Math.max((int) (font.width(headline) * big), (int) (font.width(worth) * small)) / 2 + 6;
        int lineY = cy + (int) (big * 10) + 2;

        scaledCentered(g, font, headline, cx, cy, big, argb(alpha, look.colour()), look.shadow());
        g.fill(cx - lineHalf, lineY, cx + lineHalf, lineY + 1, argb(alpha, look.colour()));
        scaledCentered(g, font, worth, cx, lineY + 4, small, argb(alpha, 0xFFFFFF), look.shadow());
        if (!tierLabel.isEmpty()) {
            g.centeredText(font, tierLabel, cx, lineY + 4 + (int) (small * 11), argb(alpha, 0x999999));
        }
    }

    // ---- Stil 9: springt kurz auf und setzt sich dann ----

    private static void pop(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, long age, Look look) {
        int cx = (int) (width * look.x());
        int cy = (int) (height * look.y());

        // Erst ueber die Zielgroesse hinaus, dann zurueck: das macht den Sprung
        float progress = Math.min(1.0f, age / (float) POP_MILLIS);
        float bump = progress < 0.6f ? 0.4f + progress : 1.3f - (progress - 0.6f) * 0.75f;
        float big = 2.6f * look.scale() * bump;
        float small = 1.2f * look.scale();

        scaledCentered(g, font, headline, cx, cy, big, argb(alpha, look.colour()), look.shadow());
        scaledCentered(g, font, worth, cx, cy + (int) (2.6f * look.scale() * 11), small, argb(alpha, 0xFFFFFF), look.shadow());
    }

    // ---- Stil 10: eine kleine Zeile mit farbigem Punkt ----

    private static void minimal(GuiGraphicsExtractor g, Font font, int width, int height, float alpha, Look look) {
        int cx = (int) (width * look.x());
        int cy = (int) (height * look.y());
        float scale = 1.0f * look.scale();

        String line = headline + (worth.isEmpty() ? "" : " " + worth);
        int textWidth = (int) (font.width(line) * scale);
        int dot = Math.max(3, (int) (4 * scale));
        int left = cx - (textWidth + dot + 4) / 2;

        g.fill(left, cy + (int) (3 * scale), left + dot, cy + (int) (3 * scale) + dot, argb(alpha, look.colour()));
        scaledText(g, font, line, left + dot + 4, cy, scale, argb(alpha, 0xFFFFFF), look.shadow());
    }

    // ---- Helfer ----

    private static void scaledCentered(GuiGraphicsExtractor g, Font font, String text, int centreX, int y,
                                       float scale, int colour, boolean shadow) {
        if (text.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int x = (int) (centreX / scale) - font.width(text) / 2;
        g.text(font, text, x, (int) (y / scale), colour, shadow);
        g.pose().popMatrix();
    }

    private static void outlinedCentered(GuiGraphicsExtractor g, Font font, String text, int centreX, int y,
                                         float scale, int colour, int edge) {
        if (text.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int x = (int) (centreX / scale) - font.width(text) / 2;
        int sy = (int) (y / scale);
        g.text(font, text, x - 1, sy, edge, false);
        g.text(font, text, x + 1, sy, edge, false);
        g.text(font, text, x, sy - 1, edge, false);
        g.text(font, text, x, sy + 1, edge, false);
        g.text(font, text, x, sy, colour, false);
        g.pose().popMatrix();
    }

    private static void scaledText(GuiGraphicsExtractor g, Font font, String text, int x, int y,
                                   float scale, int colour, boolean shadow) {
        if (text.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.text(font, text, (int) (x / scale), (int) (y / scale), colour, shadow);
        g.pose().popMatrix();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
