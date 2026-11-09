package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SkillXpCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zaehlt, wie viel Erfahrung jede Faehigkeit (Fishing, Farming, Mining ...) in dieser Sitzung gebracht hat, und rechnet sie auf die Stunde um.
 *
 * Die Zahlen kommen aus der Aktionsleiste: Bei jedem Zugewinn steht dort "+3,909.8 Fishing (1,846,023,505/0)" - der Zugewinn, der Name,
 * dahinter Stand und Ziel der Stufe (bei einer vollen Faehigkeit steht das Ziel auf 0). Hypixel schickt dieselbe Leiste mehrmals,
 * solange sie steht (im Mitschnitt vom 09.11. 114 Mal dieselbe Zeile); gezaehlt wird darum jeder Zugewinn nur einmal: ein neuer, wenn
 * sich Stand oder Ziel gegenueber dem letzten dieser Faehigkeit geaendert haben.
 *
 * Die Zeit je Faehigkeit ist die aktive: zwischen zwei Zugewinnen zaehlt der Abstand, hoechstens aber die eingestellte Pause (Vorgabe 60 s) -
 * wer zwischendurch AFK ist oder etwas anderes macht, drueckt die Rate nicht. Das laufende Stueck seit dem letzten Zugewinn zaehlt mit,
 * auch hier bis zur Pause. Es gibt keine Datei dazu: Der Stand gilt fuer diese Sitzung und geht mit dem Reset oder dem Beenden.
 */
public final class SkillXpTracker {

    /** "+3,909.8 Fishing (1,846,023,505/0)" - das Wort davor und dahinter darf alles sein, die Leiste traegt auch Leben und Mana */
    private static final Pattern GAIN = Pattern.compile("\\+([\\d,]+(?:\\.\\d+)?)\\s+([A-Za-z]+)\\s+\\(([\\d,.]+)/([\\d,.]+)\\)");
    /** Mehr Faehigkeiten gibt es nicht; die Grenze schuetzt vor einer Leiste, die jedes Mal anders aussieht */
    private static final int MAX_SKILLS = 24;
    /** Eine Rate sagt erst etwas, wenn so lange gezaehlt wurde */
    static final long MIN_ACTIVE_FOR_RATE_MS = 5_000L;

    /**
     * Zwei Zugewinne kurz hintereinander zeigt die Leiste nur mit dem letzten (bei einem Double Hook kommen beide Faenge im selben Augenblick),
     * der Stand dahinter steigt aber um beide. Steigt er zwischen zwei Leisten desselben Ziels um mehr als der gezeigte Zugewinn, zaehlt die
     * Differenz - aber nur, wenn die Leisten nicht weiter als dieses Fenster auseinanderliegen und die Differenz nicht absurd gross ist
     * (anderes Profil, lange Pause). An Schiggys Aufnahme vom 09.11.: Fishing 271.760 gezeigt gegen 323.200 nach dem Stand, in 12 von 68
     * Zugewinnen war der Stand doppelt so weit gestiegen wie gezeigt.
     */
    static final long STAND_FENSTER_MS = 15_000L;
    static final double STAND_MAX_FAKTOR = 10.0;
    /** Der Stand ist auf ganze Zahlen gerundet, der Zugewinn hat eine Nachkommastelle: darunter ist es dasselbe */
    static final double STAND_TOLERANZ = 1.0;

    /** Ein erkannter Zugewinn: {@code key} ist "Stand/Ziel" der Leiste, damit dieselbe Leiste nicht zweimal zaehlt */
    record Gain(String skill, double xp, String key, double stand, double ziel) {
    }

    /** Eine Zeile des Kastens: die Faehigkeit, ihre Erfahrung, die aktive Zeit und die Rate je Stunde (0, solange noch zu wenig gezaehlt ist) */
    public record Row(String skill, double xp, long activeMs, double perHour) {
    }

    private static final class Entry {
        double xp;
        long activeMs;
        long lastAt;
        String lastKey = "";
        double lastStand;
        double lastZiel;
    }

    private static final Map<String, Entry> entries = new LinkedHashMap<>();

    private SkillXpTracker() {
    }

    private static SkillXpCategory cfg() {
        return ModConfig.INSTANCE.skillXp;
    }

    /** Die Pause in Millisekunden, auf 10 bis 600 Sekunden zurechtgerueckt */
    static long gapMillis() {
        return Math.max(10, Math.min(SkillXpCategory.MAX_IDLE_SECONDS, cfg().idleSeconds)) * 1000L;
    }

    // ------------------------------------------------------------------ Lesen

    /** Die Zugewinn-Zeile einer Aktionsleiste (ohne Farbcodes); null, wenn keine darin steht */
    static Gain parse(String plain) {
        if (plain == null || plain.indexOf('+') < 0) return null;
        Matcher m = GAIN.matcher(plain);
        if (!m.find()) return null;
        try {
            double xp = Double.parseDouble(m.group(1).replace(",", ""));
            if (xp <= 0.0) return null;
            return new Gain(m.group(2), xp, m.group(3) + "/" + m.group(4),
                    Double.parseDouble(m.group(3).replace(",", "")), Double.parseDouble(m.group(4).replace(",", "")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Eine Aktionsleiste ist gekommen (der Text ohne Farbcodes). Bei ausgeschaltetem Tracker wird sie gar nicht erst gelesen */
    public static void onActionBar(String plain) {
        onActionBar(plain, System.currentTimeMillis());
    }

    static synchronized void onActionBar(String plain, long now) {
        Gain gain = parse(plain);
        if (gain == null) return;
        Entry entry = entries.get(gain.skill());
        if (entry == null) {
            if (entries.size() >= MAX_SKILLS) return;
            entry = new Entry();
            entries.put(gain.skill(), entry);
        }
        // Dieselbe Leiste noch einmal: schon gezaehlt
        if (gain.key().equals(entry.lastKey)) return;
        double add = gain.xp();
        // Gleiches Ziel, kurz nach der letzten Leiste, und der Stand ist weiter gestiegen als gezeigt: Es kamen Zugewinne dazwischen
        if (entry.lastAt > 0L && now - entry.lastAt <= STAND_FENSTER_MS && gain.ziel() == entry.lastZiel && gain.stand() > entry.lastStand) {
            double diff = gain.stand() - entry.lastStand;
            if (diff > gain.xp() + STAND_TOLERANZ && diff <= gain.xp() * STAND_MAX_FAKTOR) add = diff;
        }
        entry.lastKey = gain.key();
        entry.lastStand = gain.stand();
        entry.lastZiel = gain.ziel();
        entry.xp += add;
        if (entry.lastAt > 0L) entry.activeMs += Math.min(Math.max(0L, now - entry.lastAt), gapMillis());
        entry.lastAt = now;
    }

    // ------------------------------------------------------------------ Zeigen

    private static long active(Entry e, long now) {
        // Das laufende Stueck seit dem letzten Zugewinn zaehlt bis zur Pause mit
        return e.activeMs + (e.lastAt > 0L ? Math.min(Math.max(0L, now - e.lastAt), gapMillis()) : 0L);
    }

    /** Die Faehigkeiten dieser Sitzung, die mit der meisten Erfahrung zuerst */
    public static synchronized List<Row> rows(long now) {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry v = e.getValue();
            long active = active(v, now);
            double perHour = active >= MIN_ACTIVE_FOR_RATE_MS ? v.xp * 3_600_000.0 / active : 0.0;
            out.add(new Row(e.getKey(), v.xp, active, perHour));
        }
        out.sort(Comparator.comparingDouble(Row::xp).reversed());
        return out;
    }

    /** Wie lange der Tracker in dieser Sitzung gearbeitet hat: die laengste aktive Zeit einer Faehigkeit */
    public static synchronized long longestActiveMs(long now) {
        long longest = 0L;
        for (Entry e : entries.values()) longest = Math.max(longest, active(e, now));
        return longest;
    }

    public static synchronized boolean hasRows() {
        return !entries.isEmpty();
    }

    // ------------------------------------------------------------------ Zuruecksetzen

    public static synchronized void reset() {
        entries.clear();
    }

    /** Der Reset-Knopf: erst fragen */
    public static void askReset() {
        com.shokiteufel.shokimod.gui.ResetConfirm.ask("Skill XP tracker",
                "Clears the experience of every skill in this session and its timers.", SkillXpTracker::reset);
    }
}
