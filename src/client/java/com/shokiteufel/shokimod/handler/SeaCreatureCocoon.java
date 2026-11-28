package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was bei einem Cocoon geschieht: ein Titel und ein Ton fuer einen selbst, eine Zeile in den Gruppenchat fuer die anderen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Cocoon Settings", {@code SeaCreatureCocoonWarning}). Von dort stammt die Regel: Hypixel schreibt
 * "CAUGHT! You cocooned a Yeti!", wenn der Guertel The Primordial oder die Umschmiedung Blood Shot einen Meeresbewohner einspinnt; je Bewohner
 * steht einzeln, ob er einen Titel bekommt ("<Name> Has Been Cocooned", rot, dazu ein Ton) und ob die Gruppe davon hoert ("My <Name> has been
 * cocooned!"). Ein Bewohner, den die Liste nicht kennt, meldet nichts. Ohne eigene Wahl melden die seltenen. Die Umsetzung ist eigene Arbeit:
 * Java, der Titel laeuft ueber die Banner dieser Mod (Design im Sandkasten), der Ton ist waehlbar, und die Liste hat ein eigenes Fenster.
 */
public final class SeaCreatureCocoon {

    private static final Pattern COCOONED = Pattern.compile("^CAUGHT! You cocooned (?:a|an) (?<name>.+)!$");
    private static final int COLOUR = 0xFF5555;
    /** Fuenf Toene hintereinander, einer je Tick - wie bei SkyHanni (fuenf Wiederholungen) */
    private static final int PINGS = 5;

    private static int pingsLeft = 0;

    private SeaCreatureCocoon() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SeaCreatureCocoon::tick);
    }

    private static ModConfig.SeaCocoonCategory cfg() {
        return ModConfig.INSTANCE.fishing.cocoon;
    }

    /** Der Name des Bewohners in einer Cocoon-Zeile, sonst null */
    static String nameIn(String plain) {
        if (plain == null) return null;
        Matcher m = COCOONED.matcher(plain);
        return m.matches() ? m.group("name").trim() : null;
    }

    /** Bekommt dieser Bewohner einen Titel? Ohne eigene Wahl: ja, wenn er selten ist (ohne den Hauptschalter) */
    public static boolean wantsWarn(SeaCreatures.Creature c) {
        if (c == null) return false;
        return cfg().warnPicks.getOrDefault(c.name(), c.rare());
    }

    /** Geht dieser Bewohner in den Gruppenchat? Ohne eigene Wahl: ja, wenn er selten ist (ohne den Hauptschalter) */
    public static boolean wantsShare(SeaCreatures.Creature c) {
        if (c == null) return false;
        return cfg().sharePicks.getOrDefault(c.name(), c.rare());
    }

    public static void toggleWarn(SeaCreatures.Creature c) {
        if (c == null) return;
        cfg().warnPicks.put(c.name(), !wantsWarn(c));
        ModConfig.INSTANCE.saveNow();
    }

    public static void toggleShare(SeaCreatures.Creature c) {
        if (c == null) return;
        cfg().sharePicks.put(c.name(), !wantsShare(c));
        ModConfig.INSTANCE.saveNow();
    }

    /** Die Chatzeile: ein eigenes Cocoon? Dann Titel und Gruppenchat, wie eingestellt */
    public static void onChatMessage(String plain) {
        if (!FeatureGate.seaCocoon() || !GameState.Server.isSkyblock()) return;
        String name = nameIn(plain);
        if (name == null) return;
        SeaCreatures.Creature creature = SeaCreatures.byName(name);
        if (creature == null) return;

        Minecraft client = Minecraft.getInstance();
        if (cfg().warn && wantsWarn(creature)) {
            announce(client, creature.name());
        }
        if (cfg().share && wantsShare(creature) && client.getConnection() != null) {
            client.getConnection().sendCommand("pc " + shareText(creature.name()));
            ShokiMod.LOGGER.info("[Cocoon] shared to party: {}", creature.name());
        }
    }

    /** Die Zeile fuer den Gruppenchat - mit Hypixels Wortlaut aus SkyHanni, damit alle Mods sie verstehen */
    static String shareText(String name) {
        return "My " + name + " has been cocooned!";
    }

    /** Der Titel: "<Name> Has Been Cocooned", rot, im gewaehlten Design */
    static String headline(String name) {
        return name + " Has Been Cocooned";
    }

    private static void announce(Minecraft client, String name) {
        ModConfig.SeaCocoonCategory c = cfg();
        BannerDesign look = ModConfig.INSTANCE.chat.banner.designOrDefault(c.bannerDesign);
        DropBanner.show(look, "", headline(name), "", "", COLOUR, ItemStack.EMPTY, 0d);
        if (c.sound) startSound(client);
        ShokiMod.LOGGER.info("[Cocoon] title shown: {}", name);
    }

    /** Die gewaehlte Datei, sonst fuenf Toene hintereinander (einer je Tick, im Tick-Ereignis) */
    private static void startSound(Minecraft client) {
        String file = cfg().soundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), SeaCreatureCocoon.class);
            return;
        }
        pingsLeft = PINGS;
    }

    private static void tick(Minecraft client) {
        if (pingsLeft <= 0) return;
        if (client.player == null) {
            pingsLeft = 0;
            return;
        }
        pingsLeft--;
        com.shokiteufel.shokimod.util.ModSound.play(client.player, SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 1.0f);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren, auch wenn der Titel aus ist */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) announce(client, "Yeti");
    }
}
