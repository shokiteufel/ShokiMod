package com.shokiteufel.shokimod.util;

import java.util.HashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Gemerkte Antworten fuer das, was die Oberflaeche in jedem Bild fragt und selten aendert: wie breit ein Text ist, wie er ohne Farbcodes
 * aussieht, welche Teile eine Zelle hat, welches {@link Component} zu einer Zeichenkette gehoert. Ein HUD-Element darf nur neu rechnen,
 * wenn sich an ihm etwas aendert - bei gleichem Text kommt hier dieselbe Antwort ohne neues Objekt zurueck.
 *
 * <p>Nur fuer den Render-Thread (keine Sperren). Die Breiten gelten fuer die Schrift, mit der sie gemessen wurden; ein Wechsel der Schrift
 * (Neuladen der Ressourcen) leert alles, und sonst verfallen sie nach {@link #GUELTIG_NANOS}, damit nach einer Aenderung der Schriftart
 * nichts dauerhaft falsch bleibt. Jede Tabelle ist begrenzt und wird bei Ueberlauf geleert (die naechsten Bilder fuellen sie wieder).
 */
public final class TextCache {

    static final int GRENZE = 512;
    static final long GUELTIG_NANOS = 30_000_000_000L;
    /** Die Uhr wird nur bei jeder 256. Anfrage gefragt, nicht bei jeder */
    private static final int UHR_MASKE = 255;

    private static final HashMap<String, Integer> BREITEN = new HashMap<>();
    private static final HashMap<String, String> OHNE_FARBEN = new HashMap<>();
    private static final HashMap<String, String[]> ZELLEN = new HashMap<>();
    private static final HashMap<ChatFormatting, HashMap<String, Component>> TEXTE = new HashMap<>();
    private static Font schrift;
    private static long verfaelltNach;
    private static int anfragen;

    private TextCache() {
    }

    /** Breite des Textes in Punkten, wie {@link Font#width(String)}, aber je Text nur einmal gemessen */
    public static int breite(Font font, String text) {
        if (text == null || text.isEmpty()) return 0;
        pruefen(font);
        Integer b = BREITEN.get(text);
        if (b == null) {
            if (BREITEN.size() >= GRENZE) BREITEN.clear();
            b = font.width(text);
            BREITEN.put(text, b);
        }
        return b;
    }

    /**
     * Ein einfaches Component zu dieser Zeichenkette, je Zeichenkette nur einmal gebaut (gleiche Zeichenkette, gleiches Objekt). Es wird
     * geteilt: wer es veraendert (withStyle, append), muss {@link Component#literal} selbst rufen.
     */
    public static Component literal(String text) {
        return literal(text, null);
    }

    /** Wie {@link #literal(String)}, in einer Formatierung (grau, gold, ...) - je Zeichenkette und Formatierung ein Objekt */
    public static Component literal(String text, ChatFormatting format) {
        HashMap<String, Component> tabelle = TEXTE.computeIfAbsent(format, f -> new HashMap<>());
        Component c = tabelle.get(text);
        if (c == null) {
            if (tabelle.size() >= GRENZE) tabelle.clear();
            c = format == null ? Component.literal(text) : Component.literal(text).withStyle(format);
            tabelle.put(text, c);
        }
        return c;
    }

    /** Wie {@link #stripColours}: der Text ohne Farbcodes (Ziffern, a-f, r), Schnitte wie fett bleiben stehen; je Text einmal gerechnet */
    public static String ohneFarben(String text) {
        if (text == null) return "";
        if (text.indexOf('§') < 0) return text;
        String s = OHNE_FARBEN.get(text);
        if (s == null) {
            if (OHNE_FARBEN.size() >= GRENZE) OHNE_FARBEN.clear();
            s = stripColours(text);
            OHNE_FARBEN.put(text, s);
        }
        return s;
    }

    /**
     * Eine Tabellenzelle "Zahl|Anteil" in ihre zwei Teile zerlegt, je Zelle einmal; ohne Trenner gibt es {@code null}. Das Ergebnis darf nicht
     * veraendert werden.
     */
    public static String[] zelle(String zelle, char trenner) {
        int i = zelle.indexOf(trenner);
        if (i < 0) return null;
        String[] teile = ZELLEN.get(zelle);
        if (teile == null) {
            if (ZELLEN.size() >= GRENZE) ZELLEN.clear();
            teile = new String[] {zelle.substring(0, i), zelle.substring(i + 1)};
            ZELLEN.put(zelle, teile);
        }
        return teile;
    }

    /** Wie viele Eintraege gemerkt sind - fuer die Pruefung, dass die Tabellen begrenzt bleiben */
    static int groesse() {
        int n = BREITEN.size() + OHNE_FARBEN.size() + ZELLEN.size();
        for (HashMap<String, Component> t : TEXTE.values()) n += t.size();
        return n;
    }

    /** Alles vergessen (Schriftwechsel, Test) */
    public static void leeren() {
        BREITEN.clear();
        OHNE_FARBEN.clear();
        ZELLEN.clear();
        TEXTE.clear();
        schrift = null;
        verfaelltNach = 0L;
        anfragen = 0;
    }

    private static void pruefen(Font font) {
        if (font != schrift) {
            BREITEN.clear();
            schrift = font;
            verfaelltNach = System.nanoTime() + GUELTIG_NANOS;
            return;
        }
        if ((++anfragen & UHR_MASKE) == 0 && System.nanoTime() - verfaelltNach > 0L) {
            BREITEN.clear();
            verfaelltNach = System.nanoTime() + GUELTIG_NANOS;
        }
    }

    /**
     * Farbcodes herausnehmen. Der Umriss wird mit derselben Zeichenkette gezeichnet wie die Schrift; bliebe ein Farbcode darin, waere der Umriss
     * dort hell statt dunkel. Formatierungscodes (fett, kursiv) bleiben stehen, weil sie die Breite aendern und der Umriss sonst verrutschte.
     */
    public static String stripColours(String text) {
        if (text == null) return "";
        if (text.indexOf('§') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
