package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

/**
 * Titel und Ton, wenn der eigene Slayer-Boss eingesponnen wird (Guertel The Primordial, Umschmiedung Blood Shot).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code SlayerCocoonWarning}, "Slayer Cocoon Title" und "Slayer Cocoon Notification Sound"). Die Regel von dort:
 * Hypixel schreibt "YOU COCOONED YOUR SLAYER BOSS" - dann kommt der Titel "Slayer Boss Cocooned!" und zehn Toene hintereinander (im Abstand von hundert
 * Millisekunden). Die Umsetzung ist eigene Arbeit: Der Titel laeuft ueber die Banner dieser Mod (Design im Sandkasten), der Ton ist waehlbar
 * (eigene Datei oder die zehn Toene), beides ist einzeln einzuschalten.
 */
public final class SlayerCocoon {

    private static final Pattern COCOONED = Pattern.compile("^YOU COCOONED YOUR SLAYER BOSS");
    private static final int COLOUR = 0x55FFFF;
    /** Zehn Toene, alle zwei Ticks einer - hundert Millisekunden Abstand wie bei SkyHanni */
    static final int PINGS = 10;
    static final int TICKS_BETWEEN_PINGS = 2;

    private static int pingsLeft = 0;
    private static int pingWait = 0;

    private SlayerCocoon() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SlayerCocoon::tick);
    }

    /** Ist das die Cocoon-Zeile des Slayer-Bosses? Hypixel rueckt sie mit Leerzeichen ein */
    static boolean isCocoonLine(String plain) {
        return plain != null && COCOONED.matcher(plain.trim()).find();
    }

    public static void onChatMessage(String plain) {
        if (!FeatureGate.slayerCocoon() || !GameState.Server.isSkyblock()) return;
        if (!isCocoonLine(plain)) return;
        ModConfig.SlayerCategory c = ModConfig.INSTANCE.slayer;
        announce(Minecraft.getInstance(), c.cocoonTitle, c.cocoonDing);
    }

    private static void announce(Minecraft client, boolean title, boolean sound) {
        if (title) {
            BannerDesign look = ModConfig.INSTANCE.chat.banner.designOrDefault(ModConfig.INSTANCE.slayer.cocoonBannerDesign);
            DropBanner.show(look, "", "Slayer Boss Cocooned!", "", "", COLOUR, ItemStack.EMPTY, 0d);
        }
        if (sound) startSound();
        ShokiMod.LOGGER.info("[Slayer] boss cocooned - title {}, sound {}", title, sound);
    }

    /** Die gewaehlte Datei, sonst zehn Toene hintereinander (einer alle zwei Ticks, im Tick-Ereignis) */
    private static void startSound() {
        String file = ModConfig.INSTANCE.slayer.cocoonSoundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), SlayerCocoon.class);
            return;
        }
        pingsLeft = PINGS;
        pingWait = 0;
    }

    private static void tick(Minecraft client) {
        if (pingsLeft <= 0) return;
        if (client.player == null) {
            pingsLeft = 0;
            return;
        }
        if (pingWait-- > 0) return;
        pingWait = TICKS_BETWEEN_PINGS - 1;
        pingsLeft--;
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 1.0f);
    }

    /** Der Knopf in den Einstellungen: Titel und Ton einmal, auch wenn beide aus sind */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) announce(client, true, true);
    }
}
