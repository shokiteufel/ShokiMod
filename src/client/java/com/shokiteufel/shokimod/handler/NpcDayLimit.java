package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.NpcDayLimitCategory;
import com.shokiteufel.shokimod.data.ModConfig.NpcNumberFormat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der NPC Daily Limit Tracker: wie viel man heute an NPCs verkauft hat, gemessen an der Grenze von 500 Millionen Coins am Tag.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "NPC Daily Limit Tracker"). Von dort stammen die Regel - gezaehlt wird aus der Chatzeile
 * "You sold Evergreen Chip x64 for 9,600,000 Coins!", der Tag wechselt um Mitternacht GMT - und die Anzeige 400k/500m (kurz) oder
 * 400,000/500,000,000 (lang). Die Umsetzung ist eigene Arbeit: Java, der Zaehler liegt in der Einstellungsdatei, gespeichert wird hoechstens
 * einmal je Minute, und beim Kuerzen wird abgeschnitten statt gerundet (499,999,999 steht als 499.9m da, nicht als 500m - das waere ein
 * Grenzfall, den es noch nicht gibt).
 *
 * <p>Anders als bei SkyHanni gehoert der Zaehler zum Spieler und nicht zum Profil: Ob Hypixel die Grenze je Profil oder je Konto zaehlt, ist hier
 * nicht gesehen worden.
 */
public final class NpcDayLimit {

    /** Wie viel ein NPC am Tag insgesamt abnimmt */
    public static final long DAILY_LIMIT = 500_000_000L;

    private static final Pattern SOLD = Pattern.compile("^You sold .+ for ([\\d,]+) Coins!$");
    private static final long SAVE_EVERY_MILLIS = 60_000L;

    private static boolean dirty = false;
    private static long savedAt = 0L;

    private NpcDayLimit() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NpcDayLimit::tick);
    }

    /** Die Coins einer Verkaufszeile, oder -1, wenn die Zeile keine ist (Farbcodes sind schon entfernt) */
    static long parseSale(String plain) {
        if (plain == null) return -1L;
        Matcher m = SOLD.matcher(plain.trim());
        if (!m.matches()) return -1L;
        try {
            return Long.parseLong(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Der heutige Tag nach GMT, in Tagen seit 1970 */
    static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** Was heute verkauft wurde. Gehoert der Zaehler zu einem anderen Tag, beginnt er hier bei null */
    static long soldToday(NpcDayLimitCategory c, long epochDay) {
        if (c.gmtEpochDay != epochDay) {
            c.gmtEpochDay = epochDay;
            c.soldCoins = 0L;
        }
        return c.soldCoins;
    }

    /** Eine Verkaufssumme dazuzaehlen */
    static void add(NpcDayLimitCategory c, long coins, long epochDay) {
        soldToday(c, epochDay);
        c.soldCoins += coins;
    }

    /** Was bis zur Grenze noch bleibt, nie unter null */
    static long remaining(long sold) {
        return Math.max(0L, DAILY_LIMIT - sold);
    }

    /** Ganze Einheiten und eine Nachkommastelle, abgeschnitten, ohne ".0": 1500 wird 1.5, 400000 wird 400 */
    private static String unit(long coins, long unit) {
        long whole = coins / unit;
        long tenth = (coins % unit) * 10 / unit;
        return tenth == 0 ? Long.toString(whole) : whole + "." + tenth;
    }

    /** 400k, 12.3m, 1.5b - oder die Zahl, wenn sie unter tausend liegt */
    static String shorten(long coins) {
        if (coins >= 1_000_000_000L) return unit(coins, 1_000_000_000L) + "b";
        if (coins >= 1_000_000L) return unit(coins, 1_000_000L) + "m";
        if (coins >= 1_000L) return unit(coins, 1_000L) + "k";
        return Long.toString(coins);
    }

    static String format(long coins, NpcNumberFormat format) {
        if (format == NpcNumberFormat.LONG) return NumberFormat.getIntegerInstance(Locale.US).format(coins);
        return shorten(coins);
    }

    /** Die Zeile im Kasten: "400k/500m", die Zahlen in Gold, der Strich weiss */
    static String line(long sold, NpcNumberFormat format) {
        return "§6" + format(sold, format) + "§f/§6" + format(DAILY_LIMIT, format);
    }

    /** Die Zeile fuer den Kasten, mit dem Stand von jetzt */
    public static String hudLine() {
        NpcDayLimitCategory c = ModConfig.INSTANCE.misc.npcLimit;
        return line(soldToday(c, today()), c.numberFormat);
    }

    /** Eine Chatzeile (ohne Farbcodes): ist es ein Verkauf an einen NPC, geht er in die Summe */
    public static void onChatMessage(String plain) {
        if (!FeatureGate.npcDayLimit() || !GameState.Server.isSkyblock()) return;
        long coins = parseSale(plain);
        if (coins <= 0L) return;
        add(ModConfig.INSTANCE.misc.npcLimit, coins, today());
        dirty = true;
    }

    private static void tick(Minecraft client) {
        if (!dirty) return;
        long now = System.currentTimeMillis();
        // Hoechstens einmal je Minute schreiben - ein Verkaufsschwung bringt viele Zeilen hintereinander
        if (now - savedAt >= SAVE_EVERY_MILLIS) {
            dirty = false;
            savedAt = now;
            ModConfig.INSTANCE.saveNow();
        }
    }
}
