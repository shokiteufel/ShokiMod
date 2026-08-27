package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Zeitquellen fuer den Contest.
 *
 * Zwei gibt es. Hypixel schreibt die Restzeit in die Seitenleiste - das ist die
 * genaue. Fehlt sie, bleibt die Rechnung aus dem Tageszyklus: 19:30 Laufzeit plus
 * 30 Sekunden Pause sind 20 Minuten, also genau ein SkyBlock-Tag. Gemessen liegt die
 * aber daneben, weil der Contest offenbar nicht punktgenau am Tageswechsel beginnt.
 * Sie taugt deshalb nur als Notnagel, bevor man zum ersten Mal etwas gehoert hat.
 */
public final class ContestTimer {

    /** Ein Tag in Ticks. Bei 20 Ticks je Sekunde sind das 20 Minuten */
    private static final long DAY_TICKS = 24_000L;
    /** 19 Minuten 30 Sekunden */
    private static final long RUN_TICKS = 23_400L;
    private static final long TICKS_PER_SECOND = 20L;

    /** Hypixel schreibt die Zeit als "0m35s", gelegentlich mit Stunden */
    private static final Pattern STATED = Pattern.compile("(\\d+)([hms])");

    private ContestTimer() {
    }

    /** Ticks seit Tagesbeginn, oder -1 wenn keine Welt da ist */
    private static long timeOfDay() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return -1;
        // 26.1 hat die Tageszeit in das WorldClock-System verschoben; getDayTime gibt es
        // nicht mehr. getDefaultClockTime liefert die Uhr der aktuellen Dimension
        return Math.floorMod(client.level.getDefaultClockTime(), DAY_TICKS);
    }

    public static boolean known() {
        return timeOfDay() >= 0;
    }

    /** Fortlaufende Tagesnummer. Wechselt sie, faengt ein neuer Contest an */
    public static long day() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return -1;
        return Math.floorDiv(client.level.getDefaultClockTime(), DAY_TICKS);
    }

    /** Restsekunden allein aus dem Tageszyklus - ungenau, nur als Notnagel */
    public static long cycleSecondsRemaining() {
        long time = timeOfDay();
        if (time < 0) return -1;

        long ticks = time < RUN_TICKS ? RUN_TICKS - time : DAY_TICKS - time;
        return ticks / TICKS_PER_SECOND;
    }

    /** Hypixels Angabe aus der Seitenleiste in Sekunden, oder -1 wenn dort nichts steht */
    public static long statedSeconds() {
        String stated = TabContest.time();
        if (stated.isEmpty()) return -1;

        long seconds = 0;
        boolean any = false;
        Matcher matcher = STATED.matcher(stated);
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            seconds += switch (matcher.group(2)) {
                case "h" -> value * 3600;
                case "m" -> value * 60;
                default -> value;
            };
            any = true;
        }
        return any ? seconds : -1;
    }
}
