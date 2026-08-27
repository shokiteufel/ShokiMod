package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.util.ScoreboardUtils;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datum und Uhrzeit von SkyBlock, aus der Seitenleiste.
 *
 * Das ist Hypixels eigene Zeit, nicht die der Minecraft-Welt - die beiden laufen
 * nicht synchron, gemessen lagen sie Minuten auseinander. Die Seitenleiste fuehrt
 * beides ueberall, auch im Hub, und ist damit die Quelle, auf die man sich verlassen
 * kann.
 *
 * Ein SkyBlock-Tag dauert 20 echte Minuten. Aus der Uhrzeit laesst sich also
 * ausrechnen, wie lange er noch geht.
 */
public final class SkyblockClock {

    private static final Pattern DATE = Pattern.compile("(?<season>Early |Late )?(?<month>[A-Za-z]+) (?<day>\\d{1,2})(st|nd|rd|th)");
    private static final Pattern TIME = Pattern.compile("(?<hour>\\d{1,2}):(?<minute>\\d{2})\\s*(?<half>am|pm)", Pattern.CASE_INSENSITIVE);

    private static final int MINUTES_PER_DAY = 24 * 60;
    /** 20 echte Minuten je SkyBlock-Tag */
    private static final double REAL_SECONDS_PER_DAY = 20 * 60;

    private static String date = "";
    private static int minuteOfDay = -1;

    private SkyblockClock() {
    }

    /** Das Datum als Text, etwa "Winter 7th". Wechselt es, ist ein Tag vergangen */
    public static String date() {
        return date;
    }

    /** Minuten seit Mitternacht, oder -1 wenn die Seitenleiste gerade nichts hergibt */
    public static int minuteOfDay() {
        return minuteOfDay;
    }

    public static boolean known() {
        return minuteOfDay >= 0;
    }

    /** Echte Sekunden bis zum Tageswechsel */
    public static long secondsToDayEnd() {
        if (minuteOfDay < 0) return -1;
        double left = (MINUTES_PER_DAY - minuteOfDay) * (REAL_SECONDS_PER_DAY / MINUTES_PER_DAY);
        return Math.round(left);
    }

    public static void tick() {
        List<String> lines = ScoreboardUtils.getSidebarLines(Minecraft.getInstance());

        String foundDate = "";
        int foundMinute = -1;
        for (String raw : lines) {
            String line = ScoreboardUtils.stripColor(raw).trim();
            if (line.isEmpty()) continue;

            if (foundDate.isEmpty()) {
                Matcher matcher = DATE.matcher(line);
                if (matcher.find()) foundDate = matcher.group().trim();
            }
            if (foundMinute < 0) {
                Matcher matcher = TIME.matcher(line);
                if (matcher.find()) foundMinute = toMinuteOfDay(matcher);
            }
        }

        if (!foundDate.isEmpty()) date = foundDate;
        minuteOfDay = foundMinute;
    }

    /** 12-Stunden-Angabe in Minuten seit Mitternacht */
    private static int toMinuteOfDay(Matcher matcher) {
        int hour = Integer.parseInt(matcher.group("hour")) % 12;
        int minute = Integer.parseInt(matcher.group("minute"));
        if (matcher.group("half").equalsIgnoreCase("pm")) hour += 12;
        return hour * 60 + minute;
    }
}
