package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Drill hat nur noch wenig Treibstoff: eine Chatzeile mit einem Klick zum Auftanken.
 *
 * Hypixel schreibt "Your Auspicious Divan's Drill has 50% fuel remaining!". Darauf folgt eine
 * Zeile mit {@code [Refuel ...]}; der Klick holt die fehlenden Oil Barrels aus dem Sack
 * ({@code /gfs OIL_BARREL 5}), wartet eine Sekunde und ruft dann Jotraeline an
 * ({@code /call jotraeline}), die den Drill fuellt (siehe {@link CommandSequence}).
 *
 * Die Rechnung nach Angabe des Users: Ein Tank fasst 100.000 Treibstoff, ein Oil Barrel fuellt 10.000
 * nach - also zehn Prozentpunkte. Bei 50 % fehlen 50.000, das sind 5 Barrels. Es wird abgerundet: Ein
 * Barrel zu viel liefe ueber und ginge verloren. Bei 95 % ist kein ganzes Barrel noetig, dann kommt keine Zeile.
 *
 * Der Klick fuehrt zu {@code /shoki refuel}, nicht zu den Befehlen selbst: Die Menge steht in der Mod, nicht in
 * der Zeile, und ein zweiter Klick auf dieselbe Zeile holt nicht noch einmal Barrels.
 */
public final class DrillRefuel {

    /** Das Kommando, das die Chatzeile ausfuehrt */
    public static final String CLICK_COMMAND = "/shoki refuel";

    /** "Your Auspicious Divan's Drill has 50% fuel remaining!" - der Name des Drills wechselt, die Prozent auch */
    private static final Pattern LOW = Pattern.compile(
            "^Your (?<drill>.+?) has (?<percent>\\d{1,3})% fuel remaining!$");

    /** So viel Treibstoff fasst der Tank, und so viel fuellt ein Oil Barrel nach (Angabe des Users) */
    static final int TANK_FUEL = 100_000;
    static final int BARREL_FUEL = 10_000;
    static final String BARREL_ID = "OIL_BARREL";
    /** Wer den Drill auftankt: der Kontakt der Abiphone, angerufen mit /call */
    static final String CONTACT = "jotraeline";

    /** Was der Klick holt: der Drill, sein Stand und die Zahl der Barrels */
    record Refill(String drill, int percent, int barrels) {
    }

    private static Refill pending = null;

    private DrillRefuel() {
    }

    /** Wie viele ganze Barrels in den Tank passen, wenn er bei {@code percent} Prozent steht. Abgerundet; 0 heisst: es lohnt sich nicht */
    static int barrels(int percent) {
        if (percent < 0 || percent >= 100) return 0;
        long missing = (long) TANK_FUEL * (100 - percent) / 100;
        return (int) (missing / BARREL_FUEL);
    }

    /** Die Meldung "Your X has N% fuel remaining!" erkennen - oder null */
    static Refill parse(String plain) {
        if (plain == null) return null;
        Matcher m = LOW.matcher(plain.trim());
        if (!m.matches()) return null;
        int percent = Integer.parseInt(m.group("percent"));
        int barrels = barrels(percent);
        if (barrels <= 0) return null;
        return new Refill(m.group("drill").trim(), percent, barrels);
    }

    /** Die Befehle, die der Klick ausfuehrt: erst die Barrels holen, dann anrufen (eine Sekunde dazwischen macht die Folge) */
    static List<String> commands(Refill refill) {
        return List.of("gfs " + BARREL_ID + " " + refill.barrels(), "call " + CONTACT);
    }

    /** Eine Chatzeile, ohne Farbcodes */
    public static void onChatMessage(String plain) {
        if (!FeatureGate.drillRefuel() || !GameState.Server.isSkyblock()) return;
        Refill refill = parse(plain);
        if (refill == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        pending = refill;
        ShokiMod.LOGGER.info("[Mining] drill {} at {}% - offering {} barrels", refill.drill(), refill.percent(), refill.barrels());
        client.player.sendSystemMessage(message(refill));
    }

    /** "[Refuel: 5 Oil Barrels]" - der anklickbare Teil nennt gleich, wie viele geholt werden */
    static String clickText(Refill refill) {
        return "[Refuel: " + refill.barrels() + (refill.barrels() == 1 ? " Oil Barrel]" : " Oil Barrels]");
    }

    /** Die Zeile: der Stand und der Klick */
    static Component message(Refill refill) {
        StringBuilder hover = new StringBuilder("Runs, one second apart:");
        for (String command : commands(refill)) hover.append("\n/").append(command);

        MutableComponent click = Component.literal(clickText(refill))
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(CLICK_COMMAND))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover.toString()))));
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(refill.drill() + " at " + refill.percent() + "% fuel. ").withStyle(ChatFormatting.GOLD))
                .append(click);
    }

    /** Der Klick: Barrels holen und anrufen - einmal je Zeile */
    public static void run() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        Refill refill = pending;
        if (refill == null) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal("Nothing to refuel - that line was used already.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        pending = null;
        ShokiMod.LOGGER.info("[Mining] refuelling {}: {}", refill.drill(), commands(refill));
        CommandSequence.enqueue(commands(refill));
    }

    /** Nur fuer die Tests */
    static void resetForTests() {
        pending = null;
    }
}
