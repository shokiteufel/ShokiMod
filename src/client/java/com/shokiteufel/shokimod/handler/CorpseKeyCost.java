package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.scanner.CorpseKeys;
import com.shokiteufel.shokimod.scanner.MineshaftState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was die Schluessel einer gepluenderten Leiche kosten - nach dem Chat, nicht nach dem Inventar.
 *
 * Fruehere Fassung: Ein Schluessel, der aus dem Inventar verschwand, zaehlte als verbraucht. Die
 * Leiche oeffnet aber ein Fenster, und bei offenem Fenster vergleicht die Mod das Inventar nicht -
 * Umber, Tungsten und Skeleton Key blieben so oft ungezaehlt. SkyHanni macht es wie hier: Eine
 * Leiche, die ihre Beute ausschuettet ("UMBER CORPSE LOOT!" ... Strich), hat ihren Schluessel
 * gekostet, ausser der Resourceful-Perk hat ihn gerettet. Lapis braucht keinen.
 */
public final class CorpseKeyCost {

    private static final Pattern START = Pattern.compile("^(?<type>LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT!");
    private static final Pattern END = Pattern.compile("^▬{20,}$");
    private static final Pattern SAVED = Pattern.compile(
            "^LUCKY! Your Resourceful perk saved your key from being consumed!");
    /** So lange nach der Buchung kann eine verspaetete Rettungs-Meldung sie noch zuruecknehmen */
    private static final long SAVE_WINDOW_MILLIS = 5_000L;

    private static boolean inLoot = false;
    private static String type = "";
    /** Die Rettung kam, bevor die Beute zu Ende war (oder kurz davor) */
    private static long savedAt = 0L;
    private static String lastKey = null;
    private static long lastKeyAt = 0L;

    private CorpseKeyCost() {
    }

    public static void onChatMessage(String plain) {
        if (plain == null) return;
        String line = plain.trim();

        Matcher start = START.matcher(line);
        if (start.find()) {
            inLoot = true;
            type = start.group("type");
            return;
        }

        if (SAVED.matcher(line).find()) {
            long now = System.currentTimeMillis();
            if (lastKey != null && now - lastKeyAt < SAVE_WINDOW_MILLIS) {
                // Die Beute war schon gebucht: die Rettung kam hinterher
                ProfitTracker.keyRefunded(lastKey, 1);
                MineshaftProfit.keyRefunded(lastKey, 1);
                lastKey = null;
            } else {
                savedAt = now;
            }
            return;
        }

        if (inLoot && END.matcher(line).find()) {
            inLoot = false;
            String key = CorpseKeys.keyFor(type);
            type = "";
            if (key == null || !MineshaftState.inMineshaft()) return;

            long now = System.currentTimeMillis();
            if (savedAt > 0L && now - savedAt < SAVE_WINDOW_MILLIS) {
                savedAt = 0L;
                ShokiMod.LOGGER.info("[Profit] {} saved by Resourceful - no cost", key);
                return;
            }
            savedAt = 0L;
            lastKey = key;
            lastKeyAt = now;
            ProfitTracker.keySpent(key, 1);
            MineshaftProfit.keySpent(key, 1);
        }
    }
}
