package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meldet, wenn ein seltener Meeresbewohner auftaucht - der eigene und der der Gruppe.
 *
 * Nachgebaut aus Feesh von MoonTheSadFisher (Apache 2.0, Sleepy-Panda/Feesh), dort
 * features/alerts/RareCatchAlert.kt. Uebernommen ist das Verfahren: Jeder Fang hat
 * seinen eigenen Satz im Chat, und wer alle Saetze kennt, erkennt den Fang daran. Dazu
 * die Muster, mit denen Feesh und SkyHanni ihre Funde in den Gruppenchat schreiben -
 * so sieht man auch, was die anderen aus dem Wasser ziehen.
 *
 * <p>Die Einblendung selbst kommt aus dieser Mod: dasselbe Banner und derselbe Ton wie
 * bei einem seltenen Fund, damit nicht zwei verschiedene Sprachen auf dem Bildschirm
 * stehen.
 */
public final class RareCatchAlert {

    /** "It's a Double Hook!" - gilt fuer den naechsten Fang */
    private static final Pattern DOUBLE_HOOK = Pattern.compile("^It's a Double Hook!");

    /**
     * Was Feesh und SkyHanni in den Gruppenchat schreiben.
     *
     * Beide melden ihre Faenge, nur anders formuliert. Wer mit Leuten fischt, die das
     * eine oder das andere benutzen, sieht so trotzdem, was gerade hochkommt.
     */
    private static final Pattern PARTY_FEESH = Pattern.compile(
            "^--> (?:A|An) (?<name>.+?) has spawned ?.*<--$");
    private static final Pattern PARTY_FEESH_DOUBLE = Pattern.compile(
            "^--> DOUBLE HOOK! Two (?<name>.+?)s have spawned ?.*<--$");
    private static final Pattern PARTY_SKYHANNI = Pattern.compile(
            "^(?<dh>(?:DOUBLE HOOK: )?)I caught (?:a|an) (?<name>.+)!$");
    /** Die Zeile, mit der Hypixel eine Gruppennachricht kennzeichnet */
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^(?:Party|Партия|Компания) > (?:\\[[^\\]]*\\] )?(?<player>[A-Za-z0-9_]+): (?<text>.+)$");

    /** Der naechste Fang kommt doppelt - die Meldung dazu steht eine Zeile vorher */
    private static boolean doubleHook = false;

    private RareCatchAlert() {
    }

    private static ModConfig.RareCatchCategory cfg() {
        return ModConfig.INSTANCE.fishing.rareCatch;
    }

    public static void onChatMessage(String plain) {
        if (plain == null || plain.isEmpty() || !cfg().enabled) return;
        String text = plain.trim();

        if (DOUBLE_HOOK.matcher(text).find()) {
            doubleHook = true;
            return;
        }

        // Erst die Gruppe: Eine Zeile von dort ist nie der eigene Fang, und der Name
        // des Bewohners steht darin anders als in Hypixels eigener Meldung
        Matcher party = PARTY_LINE.matcher(text);
        if (party.matches()) {
            if (cfg().fromParty) fromParty(party.group("player"), party.group("text").trim());
            return;
        }

        SeaCreatures.Creature gefangen = SeaCreatures.caughtIn(text);
        if (gefangen == null) return;
        boolean doppelt = doubleHook;
        doubleHook = false;
        if (!gefangen.rare()) return;
        announce(gefangen, doppelt, "");
    }

    /** Was ein Mitglied der Gruppe gemeldet hat */
    private static void fromParty(String spieler, String nachricht) {
        Minecraft client = Minecraft.getInstance();
        // Die eigene Meldung kommt doppelt an - einmal als Fang, einmal aus der Gruppe
        if (client != null && client.player != null
                && client.player.getName().getString().equalsIgnoreCase(spieler)) {
            return;
        }

        Matcher m = PARTY_FEESH.matcher(nachricht);
        if (m.matches()) {
            melde(m.group("name"), false, spieler);
            return;
        }
        m = PARTY_FEESH_DOUBLE.matcher(nachricht);
        if (m.matches()) {
            melde(m.group("name"), true, spieler);
            return;
        }
        m = PARTY_SKYHANNI.matcher(nachricht);
        if (m.matches()) {
            // SkyHanni nennt den Loch Emperor beim alten Namen
            String name = m.group("name");
            if ("The Sea Emperor".equalsIgnoreCase(name)) name = "The Loch Emperor";
            melde(name, !m.group("dh").isEmpty(), spieler);
        }
    }

    private static void melde(String name, boolean doppelt, String spieler) {
        SeaCreatures.Creature creature = SeaCreatures.byName(name);
        if (creature == null || !creature.rare()) return;
        announce(creature, doppelt, spieler);
    }

    /**
     * Die Einblendung.
     *
     * Der Name des Bewohners steht gross, darunter wer ihn hat - bei einem eigenen Fang
     * bleibt die Zeile leer, denn dann ist es klar.
     */
    private static void announce(SeaCreatures.Creature creature, boolean doppelt, String spieler) {
        String kopf = (doppelt ? "DOUBLE HOOK! " : "") + creature.name();
        String unten = spieler.isEmpty() ? "" : "caught by " + spieler;

        if (cfg().banner) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(cfg().bannerDesign),
                    kopf, unten, creature.rarity(), creature.colour(), ItemStack.EMPTY);
        }
        if (cfg().chatLine) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                        .withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal(kopf).withStyle(style -> style
                                .withColor(creature.colour()).withBold(true)))
                        .append(Component.literal(unten.isEmpty() ? "" : " - " + unten)
                                .withStyle(ChatFormatting.GRAY)));
            }
        }
        String ton = cfg().sound;
        if (ton != null && !ton.isBlank()) {
            CustomSoundPlayer.play(ton, AlertVolume.factor(), RareCatchAlert.class);
        }
        ShokiMod.LOGGER.info("[RareCatch] {}{}", creature.name(),
                spieler.isEmpty() ? " (own)" : " by " + spieler);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren */
    public static void test() {
        SeaCreatures.Creature probe = SeaCreatures.byName("Yeti");
        if (probe == null) {
            java.util.List<SeaCreatures.Creature> selten = SeaCreatures.rare();
            if (selten.isEmpty()) return;
            probe = selten.get(0);
        }
        announce(probe, false, "");
    }
}
