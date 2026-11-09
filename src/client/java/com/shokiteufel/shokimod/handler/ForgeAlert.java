package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ForgeCategory;
import com.shokiteufel.shokimod.handler.ForgeTracker.Meldeart;
import com.shokiteufel.shokimod.handler.ForgeTracker.Meldung;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Meldet, wenn ein Forge-Platz gleich fertig ist und wenn er fertig ist - auf jeder Map, denn die Zeiten liegen gemerkt in
 * {@link ForgeTracker} (gelesen werden sie nur in den Dwarven Mines, aus der Tab-Liste).
 *
 * Alle zehn Ticks nachzusehen reicht: Die kleinste Vorwarnung ist eine Minute. Was zu melden ist, entscheidet
 * {@link ForgeTracker#faellige}; hier steht nur, wie es auf den Bildschirm kommt. Mehrere Plaetze zugleich (sieben Stueck, die zusammen
 * eingelegt wurden, werden zusammen fertig) geben ein Banner und einen Ton, nicht sieben.
 */
public final class ForgeAlert {

    private static final int CHECK_TICKS = 10;
    /** Das Orange der Forge-Ueberschrift im Kasten */
    private static final int COLOUR = 0xFFAA00;

    private static int ticks = 0;

    private ForgeAlert() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ForgeAlert::tick);
    }

    private static ForgeCategory cfg() {
        return ModConfig.INSTANCE.mining.forge;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.forgeAlert()) return;
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (client == null || client.player == null || !GameState.Server.isSkyblock()) return;
        announce(client, ForgeTracker.faellige(System.currentTimeMillis()));
    }

    /** Die Ueberschrift des Banners fuer die Meldungen eines Durchgangs; leer, wenn es nichts zu melden gibt */
    static String text(List<Meldung> list) {
        if (list == null || list.isEmpty()) return "";
        int ready = 0;
        int soon = 0;
        Meldung firstReady = null;
        Meldung firstSoon = null;
        for (Meldung m : list) {
            if (m.art() == Meldeart.FERTIG) {
                if (ready++ == 0) firstReady = m;
            } else if (soon++ == 0) {
                firstSoon = m;
            }
        }
        if (ready > 0 && soon == 0) {
            return ready == 1 ? "Forge: " + firstReady.item() + " is ready!" : "Forge: " + ready + " slots are ready!";
        }
        if (soon > 0 && ready == 0) {
            long minutes = Math.max(1L, Math.round(firstSoon.restMs() / 60_000.0));
            return soon == 1 ? "Forge: " + firstSoon.item() + " ready in " + minutes + "m"
                    : "Forge: " + soon + " slots ready in " + minutes + "m";
        }
        return "Forge: " + ready + " ready, " + soon + " almost";
    }

    private static void announce(Minecraft client, List<Meldung> list) {
        String text = text(list);
        if (text.isEmpty()) return;
        ForgeCategory c = cfg();
        if (c.banner) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(c.bannerDesign), text, "", "", COLOUR, ItemStack.EMPTY);
        }
        sound(client);
        ShokiMod.LOGGER.info("[Forge] alert: {}", text);
    }

    /**
     * Der Ton: die gewaehlte Datei, sonst das Pling. Fehlt die Datei (umbenannt, geloescht), faellt es aufs Pling zurueck - eine
     * Warnung, die stumm bleibt, ist schlimmer als eine mit dem falschen Ton.
     */
    private static void sound(Minecraft client) {
        ForgeCategory c = cfg();
        if (!c.sound || client.player == null) return;
        String file = c.soundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), ForgeAlert.class);
            return;
        }
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
    }

    /** Der Testknopf: die Warnung einmal, so wie sie kurz vor dem Ende kaeme - auch bei ausgeschalteter Warnung */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        announce(client, List.of(new Meldung(1, "Refined Diamond", Meldeart.GLEICH, 60_000L)));
    }
}
