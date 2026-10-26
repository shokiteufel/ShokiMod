package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ColdWatch;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ColdAlertCategory;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Warnt, wenn die Kaelte in den eisigen Gebieten auf 90 steigt - kurz bevor man bei 100 erfriert.
 *
 * Gelesen wird die Zeile "Cold" in der Seitenleiste (so auch bei SkyHanni, dessen Muster dafuer als Vorbild
 * diente); die Rechnung steht in {@link ColdWatch}. Viermal je Sekunde nachzusehen reicht: Die Kaelte steigt
 * langsam, und zwischen 90 und 100 bleibt Zeit genug fuer das naechste Lagerfeuer.
 *
 * Wie bei den anderen Warnungen gilt: nur in SkyBlock, nur wenn der Schalter an ist, ein Banner, auf Wunsch
 * eine Chatzeile und ein Ton - die gewaehlte Datei, sonst der Ton, mit dem das Spiel das Frieren anzeigt.
 */
public final class ColdAlert {

    private static final int READ_EVERY_TICKS = 5;
    /** Eisblau: die Farbe der Kaelte in der Seitenleiste */
    private static final int COLOUR = 0x55FFFF;

    private static final ColdWatch watch = new ColdWatch();
    private static int ticks = 0;
    private static Object lastLevel = null;

    private ColdAlert() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ColdAlert::tick);
    }

    private static ColdAlertCategory cfg() {
        return ModConfig.INSTANCE.mining.cold;
    }

    private static void tick(Minecraft client) {
        // Abgeschaltet oder nicht in SkyBlock: nichts lesen, und beim naechsten Mal neu anfangen
        if (client == null || client.level == null || client.player == null || !FeatureGate.coldAlert()
                || !GameState.Server.isSkyblock()) {
            watch.reset();
            return;
        }
        // Eine neue Welt (Insel gewechselt) vergisst, dass schon gewarnt wurde
        if (client.level != lastLevel) {
            lastLevel = client.level;
            watch.reset();
        }
        if (++ticks % READ_EVERY_TICKS != 0) return;

        List<String> lines = ScoreboardUtils.getSidebarLines(client);
        // Ist die Seitenleiste gerade nicht da, ist das keine Antwort
        if (lines.isEmpty()) return;

        int cold = ColdWatch.read(plain(lines));
        if (watch.feed(cold, threshold())) fire(client, cold);
    }

    /** Die Zeilen ohne Farbcodes */
    private static List<String> plain(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(ScoreboardUtils.stripColor(line));
        return out;
    }

    /** Die Schwelle aus den Einstellungen, auf 1 bis 99 begrenzt: Bei 100 ist es schon zu spaet */
    static int threshold() {
        return Math.max(1, Math.min(99, cfg().atCold));
    }

    // ---- Die Meldung ----

    private static void fire(Minecraft client, int cold) {
        ColdAlertCategory c = cfg();
        if (c.banner) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(c.bannerDesign),
                    "Cold at " + cold + "!", "", "", COLOUR, ItemStack.EMPTY);
        }
        if (client.player != null) {
            if (c.chatLine) {
                client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal("Your cold is at " + cold + " - warm up at a campfire!")
                                .withStyle(ChatFormatting.WHITE)));
            }
            sound(client);
        }
        ShokiMod.LOGGER.info("[Cold] warned at {}", cold);
    }

    /** Der Ton: die gewaehlte Datei, sonst der Frier-Ton des Spiels */
    private static void sound(Minecraft client) {
        ColdAlertCategory c = cfg();
        if (!c.sound) return;
        String file = c.soundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), ColdAlert.class);
            return;
        }
        client.player.playSound(SoundEvents.PLAYER_HURT_FREEZE, AlertVolume.factor(), 1.0f);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren, auch wenn die Warnung aus ist */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) fire(client, threshold());
    }
}
