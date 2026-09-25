package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.MineshaftCorpses;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In welchem Glacite Mineshaft man gerade steht.
 *
 * Hypixel schreibt es in die unterste Zeile der Seitenleiste, hinter Datum und
 * Server: "12/07/25 mini123 TUNG_1". Die vier Buchstaben sind der Bauplan
 * (Tungsten, Peridot, Ruby ...), das Zeichen danach die Ausfuehrung. Beides
 * zusammen sagt, welcher Schacht steht - und damit, wo die Leichen sein koennen.
 *
 * Woher das Muster stammt: SkyBlock API von thatgravyboat (MIT), die es fuer
 * dieselbe Sache liest. Es ist eine Tatsache ueber Hypixels Seitenleiste, kein
 * fremder Code.
 */
public final class MineshaftState {

    /** "12/07/25 mini123AB TUNG_1" - Datum, Server, Bauplan und Ausfuehrung */
    private static final Pattern SHAFT = Pattern.compile(
            "^\\d+/\\d+/\\d+\\s+\\S+\\s+([A-Za-z]{4})_([A-Za-z0-9])$");
    /** Nur alle paar Ticks: die Seitenleiste in Text zu verwandeln ist nicht umsonst */
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static String type = null;
    private static String variant = null;
    private static int ticks = 0;

    private MineshaftState() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MineshaftState::tick);
    }

    /** Der Bauplan des Schachts, vierstellig - oder null, wenn man in keinem steht */
    public static String type() {
        return type;
    }

    /** Die Ausfuehrung: 1, 2 oder C */
    public static String variant() {
        return variant;
    }

    public static boolean inMineshaft() {
        return type != null && variant != null;
    }

    /** Die bekannten Stellen fuer den Schacht, in dem man steht */
    public static List<net.minecraft.core.BlockPos> corpses() {
        return inMineshaft() ? MineshaftCorpses.forShaft(type, variant) : List.of();
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.mineshaftCorpses()) {
            forget();
            return;
        }
        if (client.player == null || client.level == null || !GameState.Server.isSkyblock()) {
            forget();
            return;
        }
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        String foundType = null;
        String foundVariant = null;
        List<String> lines = ScoreboardUtils.getSidebarLines(client);
        for (int i = 0; i < lines.size(); i++) {
            String clean = ScoreboardUtils.stripColor(lines.get(i)).trim();
            Matcher matcher = SHAFT.matcher(clean);
            if (matcher.matches()) {
                foundType = matcher.group(1).toUpperCase(java.util.Locale.ROOT);
                foundVariant = matcher.group(2).toUpperCase(java.util.Locale.ROOT);
                break;
            }
        }

        if (foundType == null) {
            forget();
            return;
        }
        if (!foundType.equals(type) || !foundVariant.equals(variant)) {
            type = foundType;
            variant = foundVariant;
            // Die Liste liegt auf der Platte; der Abruf laeuft nur, wenn sie alt ist
            MineshaftCorpses.prefetch();
            ShokiMod.LOGGER.info("[Mineshaft] {}_{} - {} known corpse spots",
                    type, variant, corpses().size());
        }
    }

    private static void forget() {
        type = null;
        variant = null;
    }
}
