package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.BannerDesign.Accent;
import com.shokiteufel.shokimod.data.BannerDesign.Anchor;
import com.shokiteufel.shokimod.data.BannerDesign.Animation;
import com.shokiteufel.shokimod.data.BannerDesign.Background;
import com.shokiteufel.shokimod.data.BannerDesign.Frame;
import com.shokiteufel.shokimod.data.BannerDesign.Icon;
import com.shokiteufel.shokimod.data.BannerDesign.TextColour;
import com.shokiteufel.shokimod.data.BannerDesign.TextEffect;
import com.shokiteufel.shokimod.util.ItemIcons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Die Einblendung fuer seltene Funde - gezeichnet nach einem {@link BannerDesign}.
 *
 * Ein Zeichenweg fuer alle Banner: das Design sagt, wo es haengt, was dahinter liegt,
 * wie die Schrift aussieht und wie es hereinkommt. Die frueheren zwanzig Stile sind
 * Vorlagen desselben Modells, kein eigener Code mehr.
 *
 * Getrennt vom {@link AlertBanner}, der fuer SHINY-Critter und Chatregeln bleibt.
 * Kein Timer: nur ein Zeitstempel, aus dem beim Zeichnen Deckkraft, Lage und
 * Animationsfortschritt folgen. Das Design wird beim Zeichnen gelesen - wer im
 * Sandbox oder Editor dreht, sieht es sofort.
 */
public final class DropBanner {

    private static final long FADE_MILLIS = 900L;
    private static final long SLIDE_MILLIS = 250L;
    private static final long POP_MILLIS = 220L;
    private static final long SWEEP_MILLIS = 350L;
    private static final long TYPE_MILLIS = 450L;
    private static final long FLASH_MILLIS = 160L;
    private static final int PADDING = 10;

    private static BannerDesign design = null;
    private static String headline = "";
    private static String worth = "";
    private static String tierLabel = "";
    private static int tint = 0xFFFFFF;
    private static ItemStack icon = null;
    private static long displayMillis = 3000L;
    private static long shownAtMillis = 0L;

    private DropBanner() {
    }

    /**
     * Blendet ein Banner ein. Ein zweiter Aufruf ersetzt das laufende.
     *
     * @param chosen das Design; null nimmt die erste Vorlage
     * @param rgb    die Farbe der Stufe - gilt, wenn das Design keine eigene hat
     */
    public static void show(BannerDesign chosen, String headlineText, String worthText, String tierText,
                            int rgb, ItemStack iconStack) {
        design = chosen == null ? BannerDesign.presets().get(0) : chosen;
        headline = headlineText == null ? "" : headlineText;
        worth = worthText == null ? "" : worthText;
        tierLabel = tierText == null ? "" : tierText;
        tint = rgb & 0xFFFFFF;
        icon = iconStack;
        displayMillis = Math.max(design.durationMillis, FADE_MILLIS + 300L);
        shownAtMillis = System.currentTimeMillis();
    }

    /** Sandbox und Befehl: ein Beispiel im gewuenschten Design, laenger als im Spiel */
    public static void preview(BannerDesign chosen) {
        show(chosen, "+ 3x Ghost Shard", "(10.5k)", "Tier 1", 0xFFD700, ItemIcons.stackFor("SHARD_GHOST"));
        displayMillis = Math.max(displayMillis, 4500L);
    }

    public static boolean visible() {
        return shownAtMillis != 0L;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (shownAtMillis == 0L || design == null) return;

        long age = System.currentTimeMillis() - shownAtMillis;
        if (age > displayMillis) {
            shownAtMillis = 0L;
            return;
        }

        float alpha = 1.0f;
        long fadeStart = displayMillis - FADE_MILLIS;
        if (age > fadeStart) alpha = 1.0f - (age - fadeStart) / (float) FADE_MILLIS;

        Font font = Minecraft.getInstance().font;
        int width = g.guiWidth();
        int height = g.guiHeight();
        BannerDesign d = design;
        int colour = parseColour(d.colour, tint);
        float scale = clamp(d.scale, 0.3f, 4.0f);

        // ---- Texte und Groessen ----
        String head = d.prefix + headline + d.suffix;
        String value = d.showValue ? worth : "";
        String tier = d.showTier ? tierLabel : "";
        float hs = clamp(d.headlineSize, 0.5f, 6.0f) * scale;
        float vs = clamp(d.valueSize, 0.5f, 6.0f) * scale;
        float ts = Math.max(0.8f, scale);

        float pop = 1.0f;
        if (d.animation == Animation.POP) {
            float progress = Math.min(1.0f, age / (float) POP_MILLIS);
            pop = progress < 0.6f ? 0.4f + progress : 1.3f - (progress - 0.6f) * 0.75f;
        }
        String shownHead = head;
        boolean typing = false;
        if (d.animation == Animation.TYPEWRITER) {
            float progress = Math.min(1.0f, age / (float) TYPE_MILLIS);
            int shown = Math.min(head.length(), Math.round(head.length() * progress));
            shownHead = head.substring(0, shown) + (progress < 1.0f ? "_" : "");
            typing = progress < 1.0f;
        }

        int headW = (int) (font.width(head) * hs * pop);
        int headH = (int) (10 * hs * pop);
        int valueW = value.isEmpty() ? 0 : (int) (font.width(value) * vs);
        int valueH = value.isEmpty() ? 0 : (int) (10 * vs);
        int tierW = tier.isEmpty() ? 0 : (int) (font.width(tier) * ts);
        int tierH = tier.isEmpty() ? 0 : (int) (10 * ts);
        int iconSize = d.icon == Icon.NONE || icon == null ? 0 : (int) (16 * clamp(d.iconScale, 0.5f, 6.0f) * scale);
        int gap = (int) (4 * scale);

        // Der Textblock: Ueberschrift, (Bild), Wert, Stufe untereinander
        int textW = Math.max(headW, Math.max(valueW, tierW));
        int textH = headH + (valueH > 0 ? gap + valueH : 0) + (tierH > 0 ? gap + tierH : 0);
        int blockW;
        int blockH;
        if (d.icon == Icon.MIDDLE && iconSize > 0) {
            blockW = Math.max(textW, iconSize);
            blockH = textH + gap + iconSize;
        } else if (d.icon == Icon.LEFT && iconSize > 0) {
            blockW = iconSize + gap * 2 + textW;
            blockH = Math.max(textH, iconSize);
        } else {
            blockW = textW;
            blockH = textH;
        }
        int accentPad = d.accent == Accent.LEFT_BAR ? (int) (6 * scale) : d.accent == Accent.DOT ? (int) (8 * scale) : 0;
        int boxW = blockW + PADDING * 2 + accentPad;
        int boxH = blockH + PADDING * 2;

        // ---- Lage ----
        boolean fullWidth = d.anchor == Anchor.BAND || d.anchor == Anchor.TOP;
        int cx;
        int cy;
        switch (d.anchor) {
            case CENTER -> {
                cx = width / 2;
                cy = height / 2;
            }
            case HOTBAR -> {
                cx = width / 2;
                cy = (int) (height * 0.62f);
            }
            case BAND -> {
                cx = width / 2;
                cy = height / 4 + boxH / 2;
            }
            case TOP -> {
                cx = width / 2;
                cy = boxH / 2 + 4;
            }
            case RIGHT_EDGE -> {
                cx = width - boxW / 2;
                cy = height / 3 + boxH / 2;
            }
            default -> {
                cx = (int) (width * clamp(d.x, 0f, 1f));
                cy = (int) (height * clamp(d.y, 0f, 1f));
            }
        }

        float slide = Math.min(1.0f, age / (float) SLIDE_MILLIS);
        if (d.animation == Animation.SLIDE_RIGHT) cx += (int) ((1.0f - slide) * (width - cx + boxW));
        if (d.animation == Animation.DROP) cy -= (int) ((1.0f - slide) * (cy + boxH));

        int left = fullWidth ? 0 : cx - boxW / 2;
        int right = fullWidth ? width : cx + boxW / 2;
        int top = cy - boxH / 2;
        int bottom = cy + boxH / 2;

        // ---- Hintergrund und Rahmen ----
        if (d.animation == Animation.FLASH && age < FLASH_MILLIS) {
            g.fill(0, 0, width, height, argb((1.0f - age / (float) FLASH_MILLIS) * 0.45f, colour));
        }
        float bgAlpha = alpha * clamp(d.backgroundAlpha, 0f, 1f);
        switch (d.background) {
            case BOX -> g.fill(left, top, right, bottom, argb(bgAlpha, 0x101010));
            case FILL -> g.fill(left, top, right, bottom, argb(bgAlpha, colour));
            case SPLIT -> {
                int mid = left + (right - left) * 3 / 5;
                g.fill(left, top, mid, bottom, argb(bgAlpha, 0x101010));
                g.fill(mid, top, right, bottom, argb(bgAlpha, colour));
            }
            default -> {
            }
        }
        if (d.frame == Frame.SINGLE) frame(g, left, top, right, bottom, 2, argb(alpha, colour));
        if (d.frame == Frame.DOUBLE) {
            frame(g, left, top, right, bottom, 1, argb(alpha, colour));
            frame(g, left + 4, top + 4, right - 4, bottom - 4, 1, argb(alpha, 0xFFFFFF));
        }
        if (d.accent == Accent.EDGES) {
            g.fill(left, top, right, top + 1, argb(alpha, colour));
            g.fill(left, bottom - 1, right, bottom, argb(alpha, colour));
        }
        if (d.accent == Accent.LEFT_BAR) g.fill(left, top, left + (int) (3 * scale) + 1, bottom, argb(alpha, colour));

        // ---- Inhalt ----
        int contentLeft = left + PADDING + accentPad + ((right - left) - boxW) / 2;
        int textLeft = contentLeft;
        int textTop = top + PADDING;
        if (d.icon == Icon.LEFT && iconSize > 0) {
            drawIcon(g, contentLeft, top + (boxH - iconSize) / 2, iconSize);
            textLeft = contentLeft + iconSize + gap * 2;
            textTop = top + (boxH - textH) / 2;
        }
        int textCentreX = textLeft + textW / 2;
        if (d.accent == Accent.DOT) {
            int dot = Math.max(3, (int) (4 * scale));
            g.fill(textLeft - accentPad, textTop + headH / 2 - dot / 2, textLeft - accentPad + dot, textTop + headH / 2 + dot / 2, argb(alpha, colour));
        }

        int y = textTop;
        drawText(g, font, shownHead, textCentreX, y, hs * pop, textColour(d.headlineColour, colour), d.textEffect, alpha, colour);
        y += headH;

        if (d.accent == Accent.UNDERLINE || d.accent == Accent.SWEEP) {
            int half = headW / 2 + (int) (6 * scale);
            if (d.accent == Accent.SWEEP) half = (int) (half * Math.min(1.0f, age / (float) SWEEP_MILLIS));
            g.fill(textCentreX - half, y + 1, textCentreX + half, y + 1 + Math.max(1, (int) (2 * scale)), argb(alpha, colour));
        }
        if (d.icon == Icon.MIDDLE && iconSize > 0) {
            y += gap;
            drawIcon(g, textCentreX - iconSize / 2, y, iconSize);
            y += iconSize;
        }
        if (valueH > 0 && !typing) {
            y += gap;
            if (d.accent == Accent.DIVIDER) {
                int half = textW / 2 + (int) (6 * scale);
                g.fill(textCentreX - half, y - gap / 2, textCentreX + half, y - gap / 2 + 1, argb(alpha, colour));
            }
            int valueX = textCentreX;
            if (d.background == Background.SPLIT) valueX = left + (right - left) * 4 / 5;
            drawText(g, font, value, valueX, y, vs, textColour(d.valueColour, colour), d.textEffect, alpha, colour);
            y += valueH;
        }
        if (tierH > 0) {
            y += gap;
            drawText(g, font, tier, textCentreX, y, ts, 0xAAAAAA, TextEffect.PLAIN, alpha, colour);
        }
    }

    // ---- Helfer ----

    private static int textColour(TextColour choice, int accent) {
        return switch (choice) {
            case WHITE -> 0xFFFFFF;
            case DARK -> 0x101010;
            default -> accent;
        };
    }

    private static void drawIcon(GuiGraphicsExtractor g, int x, int y, int size) {
        if (icon == null) return;
        float s = size / 16f;
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        g.fakeItem(icon, (int) (x / s), (int) (y / s));
        g.pose().popMatrix();
    }

    private static void drawText(GuiGraphicsExtractor g, Font font, String text, int centreX, int y, float size,
                                 int rgb, TextEffect effect, float alpha, int accent) {
        if (text == null || text.isEmpty()) return;
        int colour = argb(alpha, rgb);
        int x = (int) (centreX / size) - font.width(text) / 2;
        int sy = (int) (y / size);

        g.pose().pushMatrix();
        g.pose().scale(size, size);
        switch (effect) {
            case OUTLINE -> {
                int edge = argb(alpha, 0x000000);
                g.text(font, text, x - 1, sy, edge, false);
                g.text(font, text, x + 1, sy, edge, false);
                g.text(font, text, x, sy - 1, edge, false);
                g.text(font, text, x, sy + 1, edge, false);
                g.text(font, text, x, sy, colour, false);
            }
            case GLOW -> {
                int haze = argb(alpha * 0.28f, accent);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if (dx != 0 || dy != 0) g.text(font, text, x + dx, sy + dy, haze, false);
                    }
                }
                g.text(font, text, x, sy, colour, false);
            }
            case SHADOW -> g.text(font, text, x, sy, colour, true);
            default -> g.text(font, text, x, sy, colour, false);
        }
        g.pose().popMatrix();
    }

    private static void frame(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int thick, int colour) {
        g.fill(left, top, right, top + thick, colour);
        g.fill(left, bottom - thick, right, bottom, colour);
        g.fill(left, top, left + thick, bottom, colour);
        g.fill(right - thick, top, right, bottom, colour);
    }

    /** "FFD700" oder "#ffd700" - leer oder unlesbar heisst: Farbe der Stufe */
    public static int parseColour(String text, int fallback) {
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

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
