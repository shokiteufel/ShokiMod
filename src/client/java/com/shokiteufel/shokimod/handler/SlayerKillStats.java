package com.shokiteufel.shokimod.handler;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Zahlen hinter den Kill-Zeiten: persoenlicher Rang und Bestzeit, der Durchschnitt der Sitzung und die Schreibweise. Keine
 * Minecraft-Typen.
 *
 * <p>Die Zeiten eines Bosses (Name und Stufe) liegen aufsteigend in einer Liste, die schnellste zuerst; so ist der Rang die Stelle, an
 * der die neue Zeit einsortiert wird. Gleiche Zeit gleicher Rang: Wer eine bestehende Bestzeit nur einholt, hat keinen neuen Rekord.
 */
public final class SlayerKillStats {

    /** So viele Zeiten je Boss bleiben gespeichert; die langsamsten fallen weg */
    public static final int KEEP = 500;
    /** Was schneller ist, kann kein Kampf sein (falsch erkannter Beginn): nicht zaehlen */
    public static final long MIN_MILLIS = 500L;
    /** Was laenger ist, ist kein Kampf, sondern ein verlorener Beginn: nicht zaehlen */
    public static final long MAX_MILLIS = 30L * 60_000L;

    public static final String BEST_TEXT = "!!NEW PERSONAL BEST!!";

    /**
     * Wo die neue Zeit steht.
     *
     * @param rank     Platz unter allen Zeiten dieses Bosses (1 = die schnellste)
     * @param total    wie viele Zeiten es jetzt gibt, die neue eingerechnet
     * @param best     ob es ein neuer Rekord ist (schneller als jede bisherige; die allererste Zeit ist keiner)
     * @param previous die bisherige Bestzeit in ms, oder -1
     */
    public record Standing(int rank, int total, boolean best, long previous) {
    }

    private SlayerKillStats() {
    }

    /** Ist die Zeit eine glaubwuerdige Kampfzeit? */
    public static boolean plausible(long millis) {
        return millis >= MIN_MILLIS && millis <= MAX_MILLIS;
    }

    /**
     * Sortiert die Zeit in die Liste ein und sagt, wo sie steht. Die Liste bleibt aufsteigend und hoechstens {@link #KEEP} lang.
     *
     * @param times die Zeiten dieses Bosses (veraenderbar, aufsteigend; null ist erlaubt und wird nicht gespeichert)
     */
    public static Standing record(List<Long> times, long millis) {
        if (times == null) {
            return new Standing(1, 1, false, -1L);
        }
        long previous = times.isEmpty() ? -1L : times.get(0);
        int rank = 1;
        for (Long t : times) {
            if (t != null && t < millis) {
                rank++;
            }
        }
        // Einsortieren hinter den gleich schnellen
        int at = 0;
        while (at < times.size() && times.get(at) <= millis) {
            at++;
        }
        times.add(at, millis);
        int total = times.size();
        while (times.size() > KEEP) {
            times.remove(times.size() - 1);
        }
        boolean best = previous >= 0L && millis < previous;
        return new Standing(rank, total, best, previous);
    }

    /** Der Durchschnitt einer Sitzung - je Boss einer; beginnt neu, wenn die Lobby wechselt */
    public static final class Session {
        private int count;
        private long sum;

        public void add(long millis) {
            count++;
            sum += millis;
        }

        /**
         * Eine Quest in die Sitzung aufnehmen - nur, wenn sie hoechstens {@link SlayerKillStats#SESSION_MAX_MILLIS} (zwei Minuten) dauerte: eine laengere ist "nicht
         * gewertet" und verfaelscht den Durchschnitt nicht.
         *
         * @return ob die Quest gewertet wurde
         */
        public boolean addQuest(long millis) {
            if (!countsForSession(millis)) {
                return false;
            }
            add(millis);
            return true;
        }

        public int count() {
            return count;
        }

        /** Der Durchschnitt in ms, oder -1 ohne Zeiten */
        public long average() {
            return count == 0 ? -1L : Math.round((double) sum / count);
        }
    }

    // ------------------------------------------------------------------ Schreibweise

    /**
     * Eine Zeit in der Schreibweise des Users: "15,05Sec" unter einer Minute, darueber "1m 05,20Sec". Auf Hundertstel gerundet, das
     * Dezimalzeichen folgt der Sprache (Komma im Deutschen).
     */
    public static String format(long millis, Locale locale) {
        long cs = Math.round(Math.max(0L, millis) / 10.0);
        char sep = DecimalFormatSymbols.getInstance(locale == null ? Locale.ROOT : locale).getDecimalSeparator();
        long minutes = cs / 6000L;
        long rest = cs % 6000L;
        long seconds = rest / 100L;
        long hundredths = rest % 100L;
        String frac = (hundredths < 10 ? "0" : "") + hundredths;
        if (minutes == 0L) {
            return seconds + String.valueOf(sep) + frac + "Sec";
        }
        return minutes + "m " + (seconds < 10 ? "0" : "") + seconds + sep + frac + "Sec";
    }

    /** "Rank #3 of 41" */
    public static String rankText(Standing standing) {
        return "Rank #" + standing.rank() + " of " + standing.total();
    }

    /** Die ganze Zeile zur Bosszeit, ohne Farben (Log und Tests); die Farben setzt der Aufrufer aus denselben Teilen */
    public static String bossLine(long millis, Standing standing, Locale locale) {
        StringBuilder out = new StringBuilder("Boss Kill time: ").append(format(millis, locale));
        if (standing.best()) {
            out.append("  ").append(BEST_TEXT);
        }
        return out.append("  (").append(rankText(standing)).append(')').toString();
    }

    // ------------------------------------------------------------------ Die schnellsten Zeiten (/shoki topslayer)

    /** Die schnellsten Zeiten eines Bosses (mit Stufe), die schnellste zuerst */
    public record Top(String key, List<Long> best) {
    }

    /** Die Slayer in der Reihenfolge des Spiels; was hier nicht steht, kommt dahinter, alphabetisch */
    private static final List<String> SLAYER_ORDER = List.of("Revenant Horror", "Tarantula Broodfather", "Sven Packmaster", "Voidgloom Seraph", "Inferno Demonlord",
            "Riftstalker Bloodfiend");

    /** Wie viele Zeiten {@code /shoki topslayer} je Boss zeigt */
    public static final int TOP_COUNT = 3;

    /** Die Stufe am Ende des Namens ("Tarantula Broodfather V" = 5), oder 0, wenn dort keine roemische Zahl steht */
    static int tierOf(String key) {
        if (key == null) {
            return 0;
        }
        String word = key.trim();
        int space = word.lastIndexOf(' ');
        if (space >= 0) {
            word = word.substring(space + 1);
        }
        return switch (word) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            default -> 0;
        };
    }

    private static int slayerIndex(String key) {
        for (int i = 0; i < SLAYER_ORDER.size(); i++) {
            if (key.startsWith(SLAYER_ORDER.get(i))) {
                return i;
            }
        }
        return SLAYER_ORDER.size();
    }

    /**
     * Die {@code count} schnellsten Zeiten je Boss und Stufe, sortiert: die Slayer in der Reihenfolge des Spiels (Revenant, Tarantula, Sven, Voidgloom, Inferno,
     * Riftstalker), dann nach Stufe aufsteigend. Die Zeiten verschiedener Stufen werden nicht vermischt - eine Stufe 3 und eine Stufe 5 lassen sich nicht vergleichen.
     * Ein Boss ohne Zeiten steht nicht darin.
     *
     * @param records die gespeicherten Zeiten je Boss (aufsteigend); null ist erlaubt
     */
    public static List<Top> top(Map<String, List<Long>> records, int count) {
        List<Top> out = new ArrayList<>();
        if (records == null || count <= 0) {
            return out;
        }
        for (Map.Entry<String, List<Long>> entry : records.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            List<Long> sorted = new ArrayList<>();
            for (Long time : entry.getValue()) {
                if (time != null && time > 0L) {
                    sorted.add(time);
                }
            }
            if (sorted.isEmpty()) {
                continue;
            }
            java.util.Collections.sort(sorted);
            out.add(new Top(entry.getKey(), List.copyOf(sorted.subList(0, Math.min(count, sorted.size())))));
        }
        out.sort(Comparator.comparingInt((Top t) -> slayerIndex(t.key())).thenComparingInt(t -> tierOf(t.key())).thenComparing(Top::key));
        return out;
    }

    /** Eine Zeile zu einem Boss, ohne Farben: "Revenant Horror V: #1 1,03Sec  #2 1,10Sec  #3 1,12Sec" */
    public static String topLine(Top top, Locale locale) {
        StringBuilder out = new StringBuilder(top.key()).append(':');
        for (int i = 0; i < top.best().size(); i++) {
            out.append(i == 0 ? " " : "  ").append('#').append(i + 1).append(' ').append(format(top.best().get(i), locale));
        }
        return out.toString();
    }

    /** Die Zeile zur Questzeit mit dem Durchschnitt der Sitzung dahinter, ohne Farben */
    public static String questLine(long millis, long average, Locale locale) {
        return questLine(millis, average, true, locale);
    }

    /** Eine Quest, die laenger als zwei Minuten dauert, geht nicht in den Durchschnitt der Sitzung ein: Sie ist "nicht gewertet" (Wunsch des Users, 27.11.) */
    public static final long SESSION_MAX_MILLIS = 2L * 60_000L;

    /** Wertet die Sitzung diese Quest? Hoechstens zwei Minuten zaehlen; genau zwei Minuten noch */
    public static boolean countsForSession(long questMillis) {
        return questMillis <= SESSION_MAX_MILLIS;
    }

    /** Der Zusatz hinter einer Questzeit, die nicht in den Durchschnitt eingeht */
    public static final String NOT_COUNTED_TEXT = "not counted - over 2 min";

    /**
     * Die Zeile zur Questzeit ohne Farben.
     *
     * @param average der Durchschnitt der Sitzung (ohne diese Quest, wenn sie nicht gewertet wird), oder -1, wenn es noch keinen gibt
     * @param counted ob die Quest in den Durchschnitt einging; sonst steht "(not counted - over 2 min)" dabei
     */
    public static String questLine(long millis, long average, boolean counted, Locale locale) {
        StringBuilder out = new StringBuilder("Quest Kill time: ").append(format(millis, locale));
        if (!counted) {
            out.append("  (").append(NOT_COUNTED_TEXT).append(')');
        }
        if (average >= 0L) {
            out.append("  (Avg. Time this Session: ").append(format(average, locale)).append(')');
        }
        return out.toString();
    }
}
