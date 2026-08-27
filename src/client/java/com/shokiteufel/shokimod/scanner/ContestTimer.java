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

    public static boolean known() {
        return timeOfDay() >= 0;
    }

    /** Laeuft gerade einer, oder ist die halbe Minute Pause dazwischen? */
    public static boolean running() {
        long time = timeOfDay();
        return time >= 0 && time < RUN_TICKS;
    }

    /**
     * Restzeit als "m:ss" - bis zum Ende, waehrend der Pause bis zum naechsten Start.
     * Leer, solange keine Welt geladen ist.
     */
    public static String remaining() {
        long time = timeOfDay();
        if (time < 0) return "";

        long ticks = running() ? RUN_TICKS - time : DAY_TICKS - time;
        long seconds = ticks / TICKS_PER_SECOND;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
