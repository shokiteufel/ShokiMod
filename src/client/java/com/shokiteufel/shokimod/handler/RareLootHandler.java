package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.RareLootCategory;
import com.shokiteufel.shokimod.data.ModConfig.RareLootCategory.Tier;
import com.shokiteufel.shokimod.data.RareLootParser;
import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.render.AlertBanner;
import com.shokiteufel.shokimod.render.ShokiModToast;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.Value;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Seltene Funde aus dem Chat: Stufen-Alarm und Teilen in Party oder Gilde.
 *
 * Hypixel meldet jeden seltenen Fund als "RARE DROP!"-Zeile. Daraus wird der Fund
 * gelesen, sein Wert im Basar oder Auktionshaus nachgeschlagen, und dann greift
 * die hoechste Stufe, deren Schwelle er erreicht - mit ihrer eigenen Reaktion.
 * Wer 50M findet, bekommt den 50M-Alarm und nicht zusaetzlich die beiden darunter.
 *
 * Das Teilen schickt dieselbe Meldung mit Wert in die Party und auf Wunsch in die
 * Gilde, ab einer eigenen Schwelle. Kommt sie als "Party > ..." zurueck, wird sie
 * nicht noch einmal gelesen - sonst teilte die Mod ihre eigene Meldung.
 *
 * Nachbau von Skysofts Rare Drop Titles und Rare Loot Sharing (LGPL-3.0,
 * Akinsoft), mit drei Stufen statt einer.
 */
public final class RareLootHandler {

    private static final long BANNER_MILLIS = 3000L;
    private static final long TOAST_MILLIS = 5000L;
    /** So lange nach "LOOT SHARE You received ..." gilt der naechste Fund als geteilt */
    private static final long LOOTSHARE_WINDOW_MILLIS = 2000L;

    /** Je Stufe eine Farbe, damit man schon am Banner sieht, welche es war */
    private static final int[] TIER_COLOURS = {0x55FF55, 0xFFD700, 0xFF55FF};

    private static final Pattern LOOTSHARE_RECEIPT = Pattern.compile(
            "^LOOT SHARE You received(?: .+?)? for assisting (?<player>[A-Za-z0-9_]{1,16})!(?: \\(\\d+\\))?$",
            Pattern.CASE_INSENSITIVE);

    /** Zeilen anderer Spieler und eigene geteilte Meldungen */
    private static final String[] SKIPPED_PREFIXES = {"Party >", "Guild >", "Co-op >", "From ", "To "};

    private static long lastLootShareAt = 0L;

    private RareLootHandler() {
    }

    public static void register() {
        // Die Preise sollen dastehen, bevor der erste Fund faellt. Ist alles aus,
        // wird hier nichts angestossen und nichts geholt
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !GameState.Server.isSkyblock()) return;
            if (!active()) return;
            ItemValue.prefetch();
        });
    }

    private static RareLootCategory cfg() {
        return ModConfig.INSTANCE.chat.rareLoot;
    }

    /** Alarm oder Teilen eingeschaltet? */
    public static boolean active() {
        RareLootCategory cfg = cfg();
        return cfg.enabled || cfg.shareEnabled;
    }

    /** Nach einem Weltwechsel gilt kein Lootshare-Beleg mehr */
    public static void reset() {
        lastLootShareAt = 0L;
    }

    /**
     * @param plain die Chatzeile ohne Farbcodes
     */
    public static void onChatMessage(String plain) {
        if (plain == null || !active()) return;
        if (!GameState.Server.isSkyblock()) return;

        String clean = plain.trim();
        for (String prefix : SKIPPED_PREFIXES) {
            if (clean.startsWith(prefix)) return;
        }

        long now = System.currentTimeMillis();
        if (LOOTSHARE_RECEIPT.matcher(clean).matches()) {
            lastLootShareAt = now;
            return;
        }

        Drop drop = RareLootParser.parse(clean);
        if (drop == null) return;

        Value value = ItemValue.resolve(drop.itemIdCandidates(), drop.amount());
        boolean lootshare = lastLootShareAt > 0L && now - lastLootShareAt <= LOOTSHARE_WINDOW_MILLIS;

        Minecraft client = Minecraft.getInstance();
        RareLootCategory cfg = cfg();

        if (cfg.enabled && value != null) {
            Tier tier = tierFor(value.coins());
            if (tier != null) announce(client, tier, headline(drop), value.coins());
        }

        if (cfg.shareEnabled) share(client, drop, value, lootshare);
    }

    private static String headline(Drop drop) {
        return drop.amount() > 1 ? drop.amount() + "x " + drop.displayName() : drop.displayName();
    }

    /**
     * Die hoechste Stufe, deren Schwelle der Betrag erreicht - oder null.
     *
     * Hoechste heisst hoechste Schwelle, nicht hoechste Nummer: die Reihenfolge im
     * Menue ist nur eine Anzeige. Eine Stufe ohne gueltige Schwelle oder mit
     * Schalter aus zaehlt nicht mit.
     */
    static Tier tierFor(double coins) {
        Tier best = null;
        double bestThreshold = 0;
        for (Tier tier : cfg().tiers()) {
            if (!tier.enabled()) continue;

            double threshold = ItemValue.parseAmount(tier.threshold());
            if (threshold <= 0 || coins < threshold) continue;
            if (best == null || threshold > bestThreshold) {
                best = tier;
                bestThreshold = threshold;
            }
        }
        return best;
    }

    /**
     * Der Testknopf: feuert eine Stufe einmal mit einem Beispiel.
     *
     * Ohne echten Fund, ohne Preisliste - genau die Reaktion, die eingestellt ist.
     * Der Betrag ist die Schwelle der Stufe selbst, damit man sieht, ab wann sie greift.
     */
    public static void test(int number) {
        Tier tier = cfg().tier(number);
        double threshold = ItemValue.parseAmount(tier.threshold());
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> announce(client, tier, "Test: Flash I", Math.max(threshold, 0)));
    }

    private static void announce(Minecraft client, Tier tier, String headline, double coins) {
        String worth = ItemValue.format(coins);
        int colour = TIER_COLOURS[Math.min(Math.max(tier.number() - 1, 0), TIER_COLOURS.length - 1)];

        if (tier.banner()) {
            AlertBanner.show("+ " + headline, "(" + worth + ")", "Tier " + tier.number(), colour, BANNER_MILLIS);
        }

        if (tier.toast() && client.getToastManager() != null) {
            client.getToastManager().addToast(new ShokiModToast(
                    Component.literal(headline + " (" + worth + ")"), TOAST_MILLIS, null));
        }

        if (tier.chat() && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "§6+ " + headline + " §e(" + worth + ") §8Tier " + tier.number()));
        }

        String sound = tier.sound();
        if (sound != null && !sound.isBlank()) {
            CustomSoundPlayer.play(sound, 1.0f, RareLootHandler.class);
        }
    }

    /**
     * Schickt den Fund in die gewaehlten Kanaele, sobald er die Teil-Schwelle erreicht.
     *
     * Schwelle 0 heisst: jeden Fund teilen, auch ohne bekannten Wert. Sonst braucht
     * es einen Wert - ein Fund ohne Preis ist nicht "wertlos", nur unbekannt, und
     * wird deshalb nicht geteilt.
     */
    private static void share(Minecraft client, Drop drop, Value value, boolean lootshare) {
        RareLootCategory cfg = cfg();
        double threshold = ItemValue.parseAmount(cfg.shareThreshold);
        String message = shareText(drop, value, lootshare);

        // Jede Entscheidung steht im Log: der Tester sieht das Spiel, nicht den Code
        if (threshold > 0 && value == null) {
            ShokiMod.LOGGER.info("[RareLoot] not shared, no price known: {}", message);
            return;
        }
        if (threshold > 0 && value.coins() < threshold) {
            ShokiMod.LOGGER.info("[RareLoot] not shared, below {}: {}", cfg.shareThreshold, message);
            return;
        }
        if (!cfg.shareParty && !cfg.shareGuild) {
            ShokiMod.LOGGER.info("[RareLoot] not shared, no channel chosen: {}", message);
            return;
        }

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            ShokiMod.LOGGER.warn("[RareLoot] not shared, no connection: {}", message);
            return;
        }

        if (cfg.shareParty) {
            ShokiMod.LOGGER.info("[RareLoot] sharing to party: {}", message);
            connection.sendCommand("pc " + message);
        }
        if (cfg.shareGuild) {
            ShokiMod.LOGGER.info("[RareLoot] sharing to guild: {}", message);
            connection.sendCommand("gc " + message);
        }
    }

    /** Skysofts Wortlaut: "RARE DROP! 3x Flash I (+289 MF) (+4.1m coins)" */
    static String shareText(Drop drop, Value value, boolean lootshare) {
        StringBuilder out = new StringBuilder(lootshare ? "LOOTSHARE DROP! " : "RARE DROP! ");
        out.append(headline(drop));
        if (drop.context() != null && !drop.context().isBlank()) {
            out.append(" (").append(drop.context()).append(')');
        }
        if (value != null) {
            out.append(" (+").append(ItemValue.format(value.coins())).append(" coins)");
        }
        return out.toString();
    }
}
