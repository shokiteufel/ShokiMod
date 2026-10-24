package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ReminderCategory;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Warnt kurz bevor die Dark Auction aufmacht.
 *
 * Die Dark Auction beginnt jeden dritten SkyBlock-Tag. Ein SkyBlock-Tag dauert zwanzig Minuten echte Zeit,
 * das sind genau eine Stunde, und sie beginnt immer zur Minute 55 der vollen Stunde. Der Zeitpunkt haengt
 * deshalb nicht am Spiel, sondern allein an der Uhr: Es wird nichts aus dem Chat oder der Tab-Liste gelesen,
 * und die Warnung kommt auf jeder Insel. Gerechnet wird in UTC - die Minute ist dort dieselbe wie bei der
 * Ortszeit, ausser in Zeitzonen mit halben oder Viertelstunden-Versatz, und fuer die ist UTC richtig.
 *
 * Gewarnt wird einmal je Eroeffnung: Wer erst im Zeitfenster nach SkyBlock kommt, bekommt die Warnung mit
 * dem, was noch bleibt; wer nach der Eroeffnung kommt, bekommt keine - die naechste ist dann eine Stunde weg.
 */
public final class DarkAuctionReminder {

    static final long HOUR_MILLIS = 3_600_000L;
    /** Minute 55 der vollen Stunde */
    static final long OPEN_OFFSET_MILLIS = 55L * 60_000L;
    /** Alle zehn Ticks schauen reicht: Die kuerzeste Vorwarnung sind zehn Sekunden */
    private static final int CHECK_TICKS = 10;
    private static final int MIN_LEAD_SECONDS = 10;
    private static final int MAX_LEAD_SECONDS = 60;
    private static final int COLOUR = 0xAA55FF;

    private static int ticks = 0;
    /** Die Eroeffnung, vor der schon gewarnt wurde (Zeitstempel) */
    private static long announcedFor = 0L;

    private DarkAuctionReminder() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DarkAuctionReminder::tick);
    }

    private static ReminderCategory cfg() {
        return ModConfig.INSTANCE.chat.reminder;
    }

    /** Der Zeitpunkt der naechsten Eroeffnung ab {@code now} (Millisekunden seit 1970). Genau zur Eroeffnung ist es dieselbe */
    public static long nextOpening(long now) {
        long hourStart = now - Math.floorMod(now, HOUR_MILLIS);
        long opening = hourStart + OPEN_OFFSET_MILLIS;
        return opening >= now ? opening : opening + HOUR_MILLIS;
    }

    /** Wie lange es bis zur naechsten Eroeffnung dauert, in Millisekunden - nie negativ */
    public static long millisUntilOpening(long now) {
        return nextOpening(now) - now;
    }

    /** Die eingestellte Vorwarnung in Sekunden, auf 10 bis 60 zurechtgerueckt */
    public static int leadSeconds() {
        return Math.max(MIN_LEAD_SECONDS, Math.min(MAX_LEAD_SECONDS, cfg().darkAuctionLeadSeconds));
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.darkAuction()) return;
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (client.player == null || !GameState.Server.isSkyblock()) return;
        check(client, System.currentTimeMillis());
    }

    /**
     * Ist es Zeit zu warnen? Dann wird gewarnt, einmal je Eroeffnung.
     *
     * Getrennt vom Tick, damit sich die Uhr von aussen vorgeben laesst: Die Warnung kommt nur in einem Fenster
     * von hoechstens einer Minute je Stunde, und ein Test, der auf dieses Fenster wartet, wuerde nie laufen.
     *
     * @return ob gewarnt wurde
     */
    public static boolean check(Minecraft client, long now) {
        long opening = nextOpening(now);
        long left = opening - now;
        if (left > leadSeconds() * 1000L || opening == announcedFor) return false;
        announcedFor = opening;
        announce(client, left);
        return true;
    }

    /** Banner und Ton, je nach Einstellung */
    private static void announce(Minecraft client, long leftMillis) {
        int seconds = (int) Math.max(1L, Math.round(leftMillis / 1000.0));
        ReminderCategory cfg = cfg();
        if (cfg.darkAuctionBanner) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(cfg.darkAuctionBannerDesign),
                    "Dark Auction in " + seconds + "s", "", "", COLOUR, ItemStack.EMPTY);
        }
        sound(client);
        ShokiMod.LOGGER.info("[DarkAuction] opens in {}s - alert shown", seconds);
    }

    /**
     * Der Ton: die gewaehlte Datei, sonst das Pling. Fehlt die Datei (umbenannt, geloescht), faellt es
     * aufs Pling zurueck - eine Warnung, die stumm bleibt, ist schlimmer als eine mit dem falschen Ton.
     */
    private static void sound(Minecraft client) {
        ReminderCategory cfg = cfg();
        if (!cfg.darkAuctionSound || client.player == null) return;
        String file = cfg.darkAuctionSoundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), DarkAuctionReminder.class);
            return;
        }
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
    }

    /** Der Testknopf: die Warnung einmal, mit der eingestellten Vorwarnzeit */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        announce(client, leadSeconds() * 1000L);
    }
}
