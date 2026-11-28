package com.shokiteufel.shokimod.handler;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das Fenster "The Forge" lesen - ohne Minecraft-Klassen, damit es sich mit Beispielzeilen pruefen laesst.
 *
 * Herkunft der Idee: SkyOcean (meowdding/SkyOcean, Forge Reminder, Quelltext unter MIT) und die Bibliothek SkyblockAPI (SkyblockAPI/SkyblockAPI, MIT, {@code ForgeAPI}), auf der
 * sie aufbaut. Von dort stammen die Regeln: Das Fenster heisst "The Forge"; die sieben Plaetze liegen auf den Plaetzen 10 bis 16 des Fensters (Platz 1 = 10, Platz 7 = 16:
 * die erste Reihe im Inneren, ohne Rand); ein belegter Platz traegt den Gegenstand, der gerade geschmiedet wird (mit SkyBlock-Kennung), und in seiner Beschreibung steht
 * die Zeile "Time Remaining: 2h 20m 24s". Ohne Gegenstand ist der Platz leer oder gesperrt. Die Umsetzung ist eigene Arbeit: Java, ohne Bibliothek, und wir speichern wie
 * dort das Ende (jetzt + Restzeit), nicht die Restzeit.
 *
 * Die Zeit im Fenster steht auf die Sekunde. Der Tab ({@link ForgeTab}) rundet auf die Einheit auf, ab zwei Stunden sogar auf volle Stunden ab, und ihre Anzeige springt
 * beim Farmen minutenweise - das Fenster ist die genaue Quelle. Ein Mitschnitt des Users vom 09.11. nennt "Time Remaining: 2h 20m 24s" fuer ein Stueck, das der Tab mit "2h" zeigte.
 *
 * Belegt ist nur das "Time Remaining:" eines laufenden Platzes (SkyblockAPI und der Mitschnitt). Wie ein FERTIGER Platz beschrieben wird, ist es nicht: SkyblockAPI findet bei
 * ihm keine Zeit und vergisst den Platz. Hier gilt ein Platz mit Gegenstand und einer Zeile mit "collect" oder "ready" als fertig; alles andere ist UNKNOWN und wird nicht
 * angefasst, sondern einmal ins Log geschrieben ({@link ForgeWindow}).
 */
final class ForgeWindowParser {

    static final String TITLE = "The Forge";
    /** Platz 1 liegt auf Platz 10 des Fensters, Platz 7 auf 16 */
    static final int FIRST_CONTAINER_SLOT = 10;
    static final int LAST_CONTAINER_SLOT = 16;
    /** Ein Fenster, in dem weniger Plaetze belegt sind, ist noch nicht gefuellt: Der Server liefert die Plaetze kurz nach dem Oeffnen. Das Fenster hat Rand und Knoepfe, auch bei leerer Forge */
    static final int POPULATED_AT_LEAST = 10;

    private static final Pattern TIME_REMAINING = Pattern.compile("^Time Remaining:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLECT = Pattern.compile("(?i)collect|ready");
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    private static final Pattern LEADING_COUNT = Pattern.compile("^\\d+\\s*x\\s+");
    private static final Pattern TRAILING_COUNT = Pattern.compile("\\s+x\\s*\\d+$");

    private ForgeWindowParser() {
    }

    /** Was ein Platz im Fenster ist. UNKNOWN: Gegenstand da, aber weder eine Zeit noch ein Hinweis zum Abholen */
    enum Kind { RUNNING, READY, NONE, UNKNOWN }

    /**
     * Ein gelesener Platz.
     *
     * @param number      1 bis 7
     * @param item        der Name des Gegenstands, leer bei NONE
     * @param remainingMs die Restzeit auf die Sekunde, nur bei RUNNING
     * @param raw         die Zeilen der Beschreibung, durch " | " getrennt - fuers Log, wenn sie unverstanden bleiben
     */
    record Entry(int number, Kind kind, String item, long remainingMs, String raw) {
    }

    /** Ist das das Fenster der Forge? Der Titel ohne Farbcodes, ohne Beachtung der Gross- und Kleinschreibung */
    static boolean isForgeTitle(String title) {
        return title != null && TITLE.equalsIgnoreCase(COLOUR_CODE.matcher(title).replaceAll("").trim());
    }

    /** Der Platz der Forge (1 bis 7) zu einem Platz des Fensters; 0, wenn er nicht dazugehoert */
    static int numberOf(int containerIndex) {
        if (containerIndex < FIRST_CONTAINER_SLOT || containerIndex > LAST_CONTAINER_SLOT) return 0;
        return containerIndex - FIRST_CONTAINER_SLOT + 1;
    }

    /** Ist das Fenster gefuellt, so dass ein leerer Platz wirklich leer ist? Zaehlt die belegten Plaetze der Forge selbst, ohne den Rucksack */
    static boolean populated(int filledSlots) {
        return filledSlots >= POPULATED_AT_LEAST;
    }

    /**
     * Einen Platz des Fensters lesen.
     *
     * @param containerIndex   der Platz im Fenster (nicht im Rucksack)
     * @param hasSkyBlockId    ob der Gegenstand eine SkyBlock-Kennung hat - Rand, Knoepfe und leere Plaetze haben keine
     * @param name             der Name des Gegenstands ohne Farbcodes
     * @param lore             die Zeilen seiner Beschreibung ohne Farbcodes
     * @return der Platz, oder null, wenn der Platz des Fensters nicht zur Forge gehoert
     */
    static Entry read(int containerIndex, boolean hasSkyBlockId, String name, List<String> lore) {
        int number = numberOf(containerIndex);
        if (number == 0) return null;
        if (!hasSkyBlockId) return new Entry(number, Kind.NONE, "", 0L, "");

        String item = name == null ? "" : name.trim();
        StringBuilder raw = new StringBuilder();
        boolean collect = false;
        long remaining = 0L;
        boolean timeLine = false;
        if (lore != null) {
            for (String line : lore) {
                String text = line == null ? "" : line.trim();
                if (text.isEmpty()) continue;
                if (raw.length() > 0) raw.append(" | ");
                raw.append(text);
                Matcher m = TIME_REMAINING.matcher(text);
                if (m.matches()) {
                    timeLine = true;
                    remaining = ForgeTab.durationOf(m.group(1));
                } else if (COLLECT.matcher(text).find()) {
                    collect = true;
                }
            }
        }
        // Eine Zeit gibt es nur, solange das Stueck laeuft; "Time Remaining: 0s" ist schon fertig
        if (timeLine && remaining > 0L) return new Entry(number, Kind.RUNNING, item, remaining, raw.toString());
        if (timeLine || collect) return new Entry(number, Kind.READY, item, 0L, raw.toString());
        return new Entry(number, Kind.UNKNOWN, item, 0L, raw.toString());
    }

    /**
     * Der Name eines Gegenstands in einer Form, die zwischen Fenster und Tab-Liste gleich ist: klein, ohne Farbcodes, ohne eine Anzahl davor ("2x ") oder dahinter
     * (" x2") und ohne Zeichen, die keine Buchstaben oder Ziffern sind.
     */
    static String normal(String name) {
        if (name == null) return "";
        String text = COLOUR_CODE.matcher(name).replaceAll("").trim().toLowerCase(Locale.ROOT);
        text = LEADING_COUNT.matcher(text).replaceFirst("");
        text = TRAILING_COUNT.matcher(text).replaceFirst("");
        return text.replaceAll("[^a-z0-9]", "");
    }

    /** Ist es derselbe Gegenstand - nach {@link #normal}? Nur Gleichheit: ein Name, der nur ENTHALTEN ist, ist ein anderer ("Refined Diamond" und "Refined Diamond Block") */
    static boolean sameItem(String a, String b) {
        String x = normal(a);
        return !x.isEmpty() && x.equals(normal(b));
    }
}
