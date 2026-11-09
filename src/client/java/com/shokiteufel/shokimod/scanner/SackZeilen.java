package com.shokiteufel.shokimod.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Sammelmeldung der Saecke ({@code [Sacks] +558 items. (Last 5s.)}) und die Aufstellung an ihrem Mauszeiger.
 *
 * <p>Was Hypixel direkt in einen Sack legt, kommt nie als Platz-Update ins Inventar: Menge und Art stehen nur in dieser Meldung
 * (gemessen am Mitschnitt, siehe Gedaechtnis {@code project-hypixel-lokal-attrappe}). Die Aufstellung hat einen Block
 * {@code Added items:} mit Zeilen {@code +327 Seeds (Agronomy Sack ...)} und einen Block {@code Removed items:}. Keine Minecraft-Typen.
 */
public final class SackZeilen {
    private SackZeilen() {
    }

    /** Eine Zeile der Aufstellung: Name der Ware (wie im Spiel geschrieben) und Menge. */
    public record Zeile(String name, int menge) {
    }

    /**
     * @param hinzu            die Zeilen unter {@code Added items}
     * @param entfernt         die Zeilen unter {@code Removed items} (was aus einem Sack verschwand: geholt, gebaut, verkauft, verbraucht)
     * @param fensterSekunden  der Zeitraum, den die Meldung zusammenfasst ({@code Last 5s}), sonst {@link #STANDARD_FENSTER}
     */
    public record Meldung(List<Zeile> hinzu, List<Zeile> entfernt, int fensterSekunden, int versteckt) {
        /** Eine Meldung ohne abgeschnittenen Rest */
        public Meldung(List<Zeile> hinzu, List<Zeile> entfernt, int fensterSekunden) {
            this(hinzu, entfernt, fensterSekunden, 0);
        }
    }

    /** Wenn die Meldung kein Fenster nennt: das groesste, das Hypixel zusammenfasst. */
    public static final int STANDARD_FENSTER = 30;

    private static final Pattern ZEILE = Pattern.compile("^([+-])\\s*([\\d,.]+)\\s+(.+?)\\s*\\(");
    private static final Pattern FENSTER = Pattern.compile("\\(Last (\\d+)s", Pattern.CASE_INSENSITIVE);
    /** "+36 other items.": Hypixel zeigt hoechstens etwa fuenfzehn Posten und nennt den Rest nur als Menge */
    private static final Pattern ANDERE = Pattern.compile("^\\+\\s*([\\d,.]+)\\s+other items?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VORNE = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final Pattern HINTEN = Pattern.compile("[^\\p{L}\\p{N}]+$");

    public static boolean istSackMeldung(String klartext) {
        return klartext != null && klartext.contains("[Sacks]");
    }

    /**
     * @param klartext die Meldung ohne Farbcodes
     * @param bloecke  die Texte der Mauszeiger-Aufstellungen (je Block eine; Farbcodes schon entfernt)
     */
    public static Meldung lesen(String klartext, List<String> bloecke) {
        int fenster = STANDARD_FENSTER;
        Matcher f = FENSTER.matcher(klartext == null ? "" : klartext);
        if (f.find()) {
            fenster = Integer.parseInt(f.group(1));
        }
        List<Zeile> hinzu = new ArrayList<>();
        List<Zeile> entfernt = new ArrayList<>();
        int versteckt = 0;
        for (String block : bloecke) {
            // Jeder Block fuer sich: die Ueberschrift gilt nur innerhalb ihres Blocks
            List<Zeile> ziel = null;
            boolean unterEntfernt = false;
            for (String zeile : block.split("\n")) {
                String sauber = zeile.trim();
                String klein = sauber.toLowerCase(Locale.ROOT);
                if (klein.startsWith("added items")) {
                    ziel = hinzu;
                    unterEntfernt = false;
                    continue;
                }
                if (klein.startsWith("removed items")) {
                    ziel = entfernt;
                    unterEntfernt = true;
                    continue;
                }
                if (ziel == null) {
                    continue;
                }
                Matcher rest = ANDERE.matcher(sauber);
                if (rest.find()) {
                    // Der abgeschnittene Rest der Posten, nicht der entfernten
                    if (!unterEntfernt) {
                        versteckt += zahl(rest.group(1));
                    }
                    continue;
                }
                Matcher m = ZEILE.matcher(sauber);
                if (!m.find()) {
                    continue;
                }
                // Unter "Added items" gilt nur "+", unter "Removed items" sagt die Ueberschrift die Richtung, das Vorzeichen ist egal
                if (!unterEntfernt && "-".equals(m.group(1))) {
                    continue;
                }
                int menge = zahl(m.group(2));
                String name = HINTEN.matcher(VORNE.matcher(m.group(3)).replaceAll("")).replaceAll("").trim();
                if (menge > 0 && !name.isEmpty()) {
                    ziel.add(new Zeile(name, menge));
                }
            }
        }
        return new Meldung(hinzu, entfernt, fenster, versteckt);
    }

    /** "1,234" und "1.234" lesen, Unsinn gibt 0. */
    static int zahl(String text) {
        try {
            return Integer.parseInt(text.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
