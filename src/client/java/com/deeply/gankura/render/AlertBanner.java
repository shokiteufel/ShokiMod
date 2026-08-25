package com.deeply.gankura.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;


/**
 * Einblendung für seltene Critter.
 *
 * Hypixel nennt die seltene Variante "Sparkling <Art>"; angezeigt wird hier "SHINY!".
 * Aufbau nach crittermods FullScreenAlert: kein Timer und kein Aufräumen, sondern
 * nur ein Zeitstempel, aus dem beim Zeichnen die Deckkraft folgt.
 */
public final class AlertBanner {

    private static long displayMillis = 7000L;
    private static final long FADE_MILLIS = 1200L;
    private static final float TITLE_SCALE = 3.0f;
    private static final float SUBTITLE_SCALE = 1.6f;

    private static String headline = "";
    private static String subject = "";
    private static String where = "";
    private static int tint = 0xFFFFFF;
    private static long shownAtMillis;

    private AlertBanner() {
    }

    /** Blendet ein Banner ein. Ein zweiter Aufruf ersetzt das laufende. */
    public static void show(String headlineText, String subjectText, String whereText,
                            int rgb, long millis) {
        headline = headlineText == null ? "" : headlineText;
        subject = subjectText == null ? "" : subjectText;
        where = whereText == null ? "" : whereText;
        tint = rgb;
        displayMillis = Math.max(millis, FADE_MILLIS + 200L);
        shownAtMillis = System.currentTimeMillis();
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (shownAtMillis == 0L) return;

        long age = System.currentTimeMillis() - shownAtMillis;
        if (age > displayMillis) {
            shownAtMillis = 0L;
            return;
        }

        // Die letzten FADE_MILLIS werden ausgeblendet
        float alpha = 1.0f;
        long fadeStart = displayMillis - FADE_MILLIS;
        if (age > fadeStart) {
            alpha = 1.0f - (age - fadeStart) / (float) FADE_MILLIS;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int rgb = tint;
        int textAlpha = (int) (alpha * 255) << 24;
        int bandAlpha = (int) (alpha * 140) << 24;

        // Waagerechtes Band mit Ober- und Unterkante, quer über den Bildschirm
        int bandTop = height / 4;
        int bandBottom = bandTop + 54;
        graphics.fill(0, bandTop, width, bandBottom, bandAlpha);
        graphics.fill(0, bandTop, width, bandTop + 1, textAlpha | rgb);
        graphics.fill(0, bandBottom - 1, width, bandBottom, textAlpha | rgb);

        // centeredText kennt keine Größe, deshalb über die Matrix skalieren
        graphics.pose().pushMatrix();
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(font, Component.literal(headline),
                (int) (width / 2 / TITLE_SCALE), (int) ((bandTop + 6) / TITLE_SCALE), textAlpha | rgb);
        graphics.pose().popMatrix();

        graphics.pose().pushMatrix();
        graphics.pose().scale(SUBTITLE_SCALE, SUBTITLE_SCALE);
        graphics.centeredText(font, Component.literal(subject),
                (int) (width / 2 / SUBTITLE_SCALE), (int) ((bandTop + 30) / SUBTITLE_SCALE),
                textAlpha | 0xFFFFFF);
        graphics.pose().popMatrix();

        graphics.centeredText(font, Component.literal(where),
                width / 2, bandBottom + 4, textAlpha | 0xAAAAAA);
    }
}
