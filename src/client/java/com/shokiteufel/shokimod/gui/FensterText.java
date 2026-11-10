package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.util.TextCache;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Gemerkte Texte fuer die Fenster der Mod. Ein Fenster zeichnet in jedem Bild; was sich zwischen zwei Bildern nicht aendert (Ueberschriften,
 * Hinweise, "2 / 5"), wird nicht in jedem Bild neu gebaut, sondern hier einmal und danach nur noch zurueckgegeben.
 *
 * <ul>
 *   <li><b>Feste Texte</b> ({@code gold("Day profit")}): je Zeichenkette und Formatierung ein Component, geteilt - nie veraendern.</li>
 *   <li><b>Seitenzahl</b> ({@link #seite}): "2 / 5" in Grau, gebaut nur, wenn sich eine der beiden Zahlen aendert.</li>
 *   <li><b>Zusammengesetzte Texte</b> (Zahlen, Namen im Text): in einem {@link Gemerkt}, gebaut nur, wenn sich sein Schluessel aendert.</li>
 * </ul>
 */
public final class FensterText {

    private FensterText() {
    }

    // ------------------------------------------------------------------ feste Texte

    /** Ein Text ohne Formatierung (weiss, wie ihn das Fenster sonst zeichnet) */
    public static Component text(String text) {
        return TextCache.literal(text);
    }

    public static Component grau(String text) {
        return TextCache.literal(text, ChatFormatting.GRAY);
    }

    public static Component dunkelgrau(String text) {
        return TextCache.literal(text, ChatFormatting.DARK_GRAY);
    }

    public static Component gold(String text) {
        return TextCache.literal(text, ChatFormatting.GOLD);
    }

    public static Component gelb(String text) {
        return TextCache.literal(text, ChatFormatting.YELLOW);
    }

    public static Component rot(String text) {
        return TextCache.literal(text, ChatFormatting.RED);
    }

    public static Component gruen(String text) {
        return TextCache.literal(text, ChatFormatting.GREEN);
    }

    /** Ein Text in einer frei gewaehlten Formatierung */
    public static Component format(String text, ChatFormatting format) {
        return TextCache.literal(text, format);
    }

    // ------------------------------------------------------------------ Seitenzahl

    private static int seiteSeite = -1;
    private static int seiteSeiten = -1;
    private static Component seiteText;

    /** "2 / 5" in Grau (seite ab 0): gebaut, wenn sich die Seite oder die Zahl der Seiten aendert, sonst dasselbe Component wie im Bild davor */
    public static Component seite(int seite, int seiten) {
        if (seiteText == null || seite != seiteSeite || seiten != seiteSeiten) {
            seiteText = Component.literal((seite + 1) + " / " + seiten).withStyle(ChatFormatting.GRAY);
            seiteSeite = seite;
            seiteSeiten = seiten;
        }
        return seiteText;
    }

    // ------------------------------------------------------------------ zusammengesetzte Texte

    /**
     * Ein Text, der aus Zahlen und Namen entsteht und nur neu gebaut wird, wenn sich seine Quellen aendern. Der Schluessel fasst die Zahlen
     * zusammen (zum Beispiel {@code gewaehlt * 1000L + gesamt}), dazu kann ein Objekt kommen, das ueber seine Identitaet verglichen wird (eine
     * Zeichenkette aus einem Feld, die sich nur durch eine neue ersetzt).
     *
     * <pre>{@code
     * if (!info.gilt(gewaehlt * 1000L + gesamt, filter)) {
     *     info.setze(gewaehlt * 1000L + gesamt, filter, Component.literal(gewaehlt + " of " + gesamt).withStyle(ChatFormatting.DARK_GRAY));
     * }
     * graphics.centeredText(font, info.component(), ...);
     * }</pre>
     */
    public static final class Gemerkt {
        private boolean belegt;
        private long schluessel;
        private Object bezug;
        private Component component;
        private String text;

        public boolean gilt(long schluessel, Object bezug) {
            return belegt && this.schluessel == schluessel && this.bezug == bezug;
        }

        public boolean gilt(long schluessel) {
            return gilt(schluessel, null);
        }

        public void setze(long schluessel, Object bezug, Component component) {
            this.schluessel = schluessel;
            this.bezug = bezug;
            this.component = component;
            this.text = null;
            this.belegt = true;
        }

        public void setze(long schluessel, Component component) {
            setze(schluessel, null, component);
        }

        /** Dasselbe fuer eine einfache Zeichenkette (Text, der mit {@code graphics.text} gezeichnet wird) */
        public void setze(long schluessel, Object bezug, String text) {
            this.schluessel = schluessel;
            this.bezug = bezug;
            this.component = null;
            this.text = text;
            this.belegt = true;
        }

        public void setze(long schluessel, String text) {
            setze(schluessel, null, text);
        }

        public Component component() {
            return component;
        }

        public String text() {
            return text;
        }
    }
}
