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
        // Teilen und anzeigen sind zwei Entscheidungen: Wer einen Fang meldet, will
        // ihn nicht zwangslaeufig auch selbst eingeblendet bekommen - und umgekehrt
        if (wantsShare(gefangen)) shareToParty(gefangen, doppelt);
        if (!wantsOwn(gefangen)) return;
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
        if (creature == null || !wantsParty(creature)) return;
        announce(creature, doppelt, spieler);
    }

    /**
     * Soll sich dieser Bewohner beim eigenen Fang melden?
     *
     * Steht er nicht in der Auswahl, gilt die Voreinstellung: die seltenen ja, die
     * uebrigen nein. Dadurch bekommt ein Bewohner, den Hypixel spaeter hinzufuegt, von
     * selbst das Richtige, statt stumm zu bleiben, bis jemand die Liste pflegt.
     */
    public static boolean wantsOwn(SeaCreatures.Creature creature) {
        if (creature == null) return false;
        return cfg().own.getOrDefault(creature.name(), creature.rare());
    }

    /** Dasselbe fuer einen Fang aus der Gruppe - der Hauptschalter geht vor */
    public static boolean wantsParty(SeaCreatures.Creature creature) {
        if (creature == null || !cfg().fromParty) return false;
        return cfg().party.getOrDefault(creature.name(), creature.rare());
    }

    /** Den Schalter fuer den eigenen Fang umlegen */
    public static void toggleOwn(SeaCreatures.Creature creature) {
        if (creature == null) return;
        cfg().own.put(creature.name(), !wantsOwn(creature));
        ModConfig.INSTANCE.saveNow();
    }

    /** Dasselbe fuer die Gruppe - unabhaengig vom Hauptschalter, sonst liesse es sich
     *  bei ausgeschalteter Gruppe nicht vorbereiten */
    public static void toggleParty(SeaCreatures.Creature creature) {
        if (creature == null) return;
        boolean jetzt = cfg().party.getOrDefault(creature.name(), creature.rare());
        cfg().party.put(creature.name(), !jetzt);
        ModConfig.INSTANCE.saveNow();
    }

    /** Ob die Gruppe fuer diesen Bewohner angehakt ist - ohne den Hauptschalter */
    public static boolean partyPicked(SeaCreatures.Creature creature) {
        if (creature == null) return false;
        return cfg().party.getOrDefault(creature.name(), creature.rare());
    }

    /**
     * Soll dieser Fang in den Gruppenchat?
     *
     * Anders als die beiden anderen Schalter ist dieser von Haus aus aus - auch bei den
     * seltenen. Die anderen aendern, was auf dem eigenen Bildschirm steht; dieser
     * schickt etwas an andere Leute.
     */
    public static boolean wantsShare(SeaCreatures.Creature creature) {
        if (creature == null) return false;
        return cfg().share.getOrDefault(creature.name(), false);
    }

    public static void toggleShare(SeaCreatures.Creature creature) {
        if (creature == null) return;
        cfg().share.put(creature.name(), !wantsShare(creature));
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Den Fang in den Gruppenchat schreiben - in der Schreibweise von Feesh.
     *
     * Absichtlich deren Wortlaut: "--> A YETI has spawned <--". Wer in der Gruppe Feesh
     * benutzt, bekommt die Meldung damit genauso eingeblendet wie von einem
     * Feesh-Nutzer, und diese Mod liest sie ohnehin schon. Ein eigener Wortlaut haette
     * nur erreicht, dass niemand ihn versteht.
     */
    private static void shareToParty(SeaCreatures.Creature creature, boolean doppelt) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) return;

        String gross = creature.name().toUpperCase(java.util.Locale.ROOT);
        String text = doppelt
                ? "--> DOUBLE HOOK! Two " + gross + "s have spawned <--"
                : "--> " + artikel(gross) + " " + gross + " has spawned <--";
        client.getConnection().sendCommand("pc " + text);
        ShokiMod.LOGGER.info("[RareCatch] shared to party: {}", text);
    }

    /** "An" vor einem Selbstlaut, sonst "A" - so steht es auch in Feeshs Zeilen */
    private static String artikel(String name) {
        return name.isEmpty() || "AEIOU".indexOf(name.charAt(0)) < 0 ? "A" : "An";
    }

    /** Der eigene Klang dieses Bewohners, oder leer */
    public static String soundOf(SeaCreatures.Creature creature) {
        if (creature == null) return "";
        String ton = cfg().sounds.get(creature.name());
        return ton == null ? "" : ton;
    }

    public static void setSound(SeaCreatures.Creature creature, String datei) {
        if (creature == null) return;
        if (datei == null || datei.isBlank()) cfg().sounds.remove(creature.name());
        else cfg().sounds.put(creature.name(), datei);
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Die Einblendung.
     *
     * Der Name des Bewohners steht gross, darunter wer ihn hat - bei einem eigenen Fang
     * bleibt die Zeile leer, denn dann ist es klar.
     */
    /**
     * Das Aussehen der Einblendung.
     *
     * Wer eines benennt, bekommt es. Ohne Angabe aber nicht die allgemeine Vorlage,
     * sondern die schlichte: Beim Angeln taucht alle paar Minuten etwas auf, und dann
     * will man lesen, was da ist, statt einer Kiste beim Oeffnen zuzusehen. Ein eigenes
     * Design mit diesem Namen geht der Vorlage vor - so laesst es sich im Sandkasten
     * umbauen, ohne dass hier etwas zu aendern waere.
     */
    private static com.shokiteufel.shokimod.data.BannerDesign design() {
        String name = cfg().bannerDesign;
        if (name != null && !name.isBlank()) {
            return ModConfig.INSTANCE.chat.banner.designOrDefault(name);
        }
        com.shokiteufel.shokimod.data.BannerDesign eigenes =
                ModConfig.INSTANCE.chat.banner.design(
                        com.shokiteufel.shokimod.data.BannerDesign.PLAIN_CATCH);
        if (eigenes != null) return eigenes;
        for (com.shokiteufel.shokimod.data.BannerDesign vorlage
                : com.shokiteufel.shokimod.data.BannerDesign.presets()) {
            if (com.shokiteufel.shokimod.data.BannerDesign.PLAIN_CATCH.equals(vorlage.name)) {
                return vorlage;
            }
        }
        return ModConfig.INSTANCE.chat.banner.designOrDefault("");
    }

    /**
     * Die Ueberschrift des Banners: "2x Yeti", bei einem Fang aus der Gruppe
     * "Schiggy: 2x Yeti".
     *
     * Der Name steht vorn in der Ueberschrift und nicht in einer zweiten Zeile: Die
     * schlichte Vorlage zeigt die klein, und im Vorbeigehen liest man sie nicht. Wer
     * etwas gezogen hat, ist beim Angeln mit anderen die halbe Meldung.
     */
    static String headline(SeaCreatures.Creature creature, boolean doppelt, String spieler) {
        String fang = (doppelt ? "2x " : "") + creature.name();
        return spieler == null || spieler.isEmpty() ? fang : spieler + ": " + fang;
    }

    private static void announce(SeaCreatures.Creature creature, boolean doppelt, String spieler) {
        String kopf = headline(creature, doppelt, spieler);
        String unten = "";
        String vonWem = spieler.isEmpty() ? "" : " - caught by " + spieler;

        if (cfg().banner) {
            DropBanner.show(design(), kopf, unten, creature.rarity(), creature.colour(), ItemStack.EMPTY);
        }
        if (cfg().chatLine) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                        .withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal(kopf).withStyle(style -> style
                                .withColor(creature.colour()).withBold(true)))
                        .append(Component.literal(vonWem).withStyle(ChatFormatting.GRAY)));
            }
        }
        // Der eigene Klang des Bewohners geht vor; ohne ihn der allgemeine
        String ton = soundOf(creature);
        if (ton.isBlank()) ton = cfg().sound;
        if (ton != null && !ton.isBlank()) {
            CustomSoundPlayer.play(ton, AlertVolume.factor(), RareCatchAlert.class);
        }
        ShokiMod.LOGGER.info("[RareCatch] {}{}", creature.name(),
                spieler.isEmpty() ? " (own)" : " by " + spieler);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren */
    /** Einen einzelnen Bewohner vorfuehren - der Knopf in der Auswahl */
    public static void preview(SeaCreatures.Creature creature) {
        if (creature != null) announce(creature, false, "");
    }

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
