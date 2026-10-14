package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was ein Slayer-Boss zu starten kostet - so, wie SkyHanni es rechnet.
 *
 * Die Kosten kommen nicht als Chatzeile, nur die Purse in der Seitenleiste sinkt. Also wird die
 * Purse beobachtet: Sinkt sie in dem Augenblick, in dem "SLAYER QUEST STARTED!" im Chat steht,
 * sind das die Kosten. Der Chat kommt mal einen Tick vor, mal nach der Seitenleiste, deshalb
 * gilt eine Senkung, die kurz vor oder kurz nach der Zeile liegt. Mehr als 350.000 Coins
 * kostet kein Boss - groessere Abzuege (ein Kauf, ein Wurf) werden verworfen.
 *
 * Beim Auto-Slayer zieht Hypixel das Geld aus der Bank und sagt es im Chat:
 * "Took 1.9k coins from your bank for auto-slayer...".
 */
public final class SlayerSpawnCost {

    private static final long WINDOW_MILLIS = 1_500L;
    private static final long MAX_COST = 350_000L;
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy):\\s*([\\d,]+)");
    private static final Pattern QUEST_START = Pattern.compile("^SLAYER QUEST STARTED!");
    private static final Pattern BANK = Pattern.compile(
            "^Took (?<coins>[\\d.,]+[kKmM]?) coins from your bank for auto-slayer");

    private static long purse = -1L;
    /** Die letzte Senkung der Purse und wann sie war - falls die Chatzeile erst danach kommt */
    private static long lastDrop = 0L;
    private static long lastDropAt = 0L;
    private static long questStartAt = 0L;

    private SlayerSpawnCost() {
    }

    /** Einmal je Tick: die Purse in der Seitenleiste lesen */
    public static void tick(Minecraft client) {
        if (client == null || client.level == null || !ProfitTracker.enabled()) return;

        long now = readPurse(client);
        if (now < 0) return;
        long before = purse;
        purse = now;
        if (before < 0 || now >= before) return;

        long drop = before - now;
        long at = System.currentTimeMillis();
        // Kam die Zeile schon, gehoert die Senkung dazu; sonst wird sie fuer die Zeile vorgemerkt
        if (at - questStartAt <= WINDOW_MILLIS) {
            book(drop);
            questStartAt = 0L;
        } else {
            lastDrop = drop;
            lastDropAt = at;
        }
    }

    public static void onChatMessage(String plain) {
        if (plain == null || !ProfitTracker.enabled()) return;
        String line = plain.trim();
        if (QUEST_START.matcher(line).find()) {
            long at = System.currentTimeMillis();
            if (lastDrop > 0 && at - lastDropAt <= WINDOW_MILLIS) {
                book(lastDrop);
                lastDrop = 0L;
            } else {
                questStartAt = at;
            }
            return;
        }
        Matcher bank = BANK.matcher(line);
        if (bank.find()) book(Math.round(ItemValue.parseAmount(bank.group("coins"))));
    }

    private static void book(long coins) {
        if (coins <= 0 || coins > MAX_COST) {
            ShokiMod.LOGGER.info("[Profit] slayer spawn cost {} out of range - ignored", coins);
            return;
        }
        ProfitTracker.adjust(ProfitTracker.SPAWN_COINS, (int) -coins);
        ShokiMod.LOGGER.info("[Profit] slayer spawn cost -{}", coins);
    }

    /** Die Purse aus der Seitenleiste, oder -1 */
    private static long readPurse(Minecraft client) {
        for (String raw : ScoreboardUtils.getSidebarLines(client)) {
            Matcher m = PURSE.matcher(ScoreboardUtils.stripColor(raw).trim());
            if (!m.find()) continue;
            try {
                return Long.parseLong(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return -1L;
    }
}
