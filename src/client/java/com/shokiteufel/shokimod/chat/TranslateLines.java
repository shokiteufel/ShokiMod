package com.shokiteufel.shokimod.chat;

/**
 * Chat (Teil 4) > Translator: die letzten Nachrichten, die einen Klick zum Uebersetzen tragen. Der Klick der Chatzeile ruft {@code /shoki translateline <Nummer>}; die
 * Nummer sagt, welchen Text er meint, damit nicht der ganze Text im Befehl steht (er waere dort zu lesen, zu lang und konnte falsch zerlegt werden).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TranslatorCommand}): Dort steht der Text selbst im Befehl ({@code /shtranslate <Text>}). Die Umsetzung ist eigene Arbeit:
 * Java, ein Ring aus hundert Eintraegen; eine aeltere Nummer ist vergessen.
 */
public final class TranslateLines {

    /** Wie viele Nachrichten die Mod behaelt */
    static final int CAPACITY = 100;
    /** Laenger wird eine Nachricht nicht gemerkt (Hypixel erlaubt weniger; es schuetzt vor einer riesigen Anfrage) */
    static final int MAX_LENGTH = 1000;

    private static final String[] TEXTS = new String[CAPACITY];
    private static int next = 0;

    private TranslateLines() {
    }

    /** Merkt den Text und gibt seine Nummer (nie negativ, zaehlt immer weiter) */
    public static synchronized int remember(String text) {
        String t = text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
        int id = next++;
        TEXTS[id % CAPACITY] = t;
        // Eine Nummer, die mehr als hundert Nummern zurueckliegt, ist aus dem Ring gefallen (get prueft das)
        return id;
    }

    /** Der Text zu einer Nummer, oder null, wenn er schon vergessen ist (oder es die Nummer nie gab) */
    public static synchronized String get(int id) {
        if (id < 0 || id >= next || next - id > CAPACITY) return null;
        return TEXTS[id % CAPACITY];
    }

    /** Alles vergessen (die Tests) */
    static synchronized void clear() {
        java.util.Arrays.fill(TEXTS, null);
        next = 0;
    }
}
