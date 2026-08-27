package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;

/**
 * Wie lange der laufende Contest noch geht.
 *
 * In der Tab-Liste steht dazu nichts - die einzige Zeit dort gehoert dem Event und
 * laeuft ueber Tage. Der Contest haengt aber am Tageswechsel, und die Rechnung geht
 * glatt auf: 19:30 Laufzeit plus 30 Sekunden Pause sind 20 Minuten, also genau ein
 * SkyBlock-Tag. Damit laesst sich die Restzeit aus der Weltzeit ableiten, und zwar
 * ueberall - man muss nicht am Ort des Contests stehen.
 */
public final class ContestTimer {

    /** Ein Tag in Ticks. Bei 20 Ticks je Sekunde sind das 20 Minuten */
    private static final long DAY_TICKS = 24_000L;
    /** 19 Minuten 30 Sekunden */
    private static final long RUN_TICKS = 23_400L;
    private static final long TICKS_PER_SECOND = 20L;

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

    /** Fortlaufende Tagesnummer. Wechselt sie, faengt ein neuer Contest an */
    public static long day() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return -1;
        return Math.floorDiv(client.level.getDefaultClockTime(), DAY_TICKS);
    }

    /** Restsekunden bis zum Ende, waehrend der Pause bis zum naechsten Start */
    public static long secondsRemaining() {
        long stated = statedSeconds();
        if (stated >= 0) return stated;

        long time = timeOfDay();
        if (time < 0) return -1;
        long ticks = running() ? RUN_TICKS - time : DAY_TICKS - time;
        return ticks / TICKS_PER_SECOND;
    }

    public static boolean known() {
        return timeOfDay() >= 0;
    }

    /** Hypixels "0m35s" in Sekunden, oder -1 wenn nichts dasteht */
    private static long statedSeconds() {
        String stated = TabContest.time();
        if (stated.isEmpty()) return -1;

        long seconds = 0;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(stated);
        boolean any = false;
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

    /** Laeuft gerade einer, oder ist die halbe Minute Pause dazwischen? */
    public static boolean running() {
        // Steht in der Seitenleiste eine Restzeit, laeuft er
        if (!TabContest.time().isEmpty()) return true;

        long time = timeOfDay();
        return time >= 0 && time < RUN_TICKS;
    }

    /**
     * Restzeit als "m:ss" - bis zum Ende, waehrend der Pause bis zum naechsten Start.
     *
     * Hypixels eigene Angabe aus der Seitenleiste hat Vorrang. Die Rechnung aus dem
     * Tageszyklus ist nur der Ersatz fuer den Fall, dass die Seitenleiste sie nicht
     * fuehrt - sie unterstellt, dass der Contest am Tageswechsel beginnt, und das
     * stimmt nicht immer.
     */
    public static String remaining() {
        String stated = TabContest.time();
        if (!stated.isEmpty()) return stated;

        long time = timeOfDay();
        if (time < 0) return "";

        long seconds = secondsRemaining();
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
