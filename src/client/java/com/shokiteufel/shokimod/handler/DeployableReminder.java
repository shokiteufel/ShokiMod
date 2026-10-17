package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployablesCategory;
import com.shokiteufel.shokimod.render.AlertBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meldet, wenn eines deiner Deployables ablaeuft: Power Orb, Flare, Umberella, Laterne, Totem of
 * Corruption oder Black Hole.
 *
 * <p>Gelesen wird die Zeile, die Hypixel selbst in den Chat schreibt - "Your Plasmaflux Power Orb
 * despawned." oder "Your Umberella despawned.". Sie kommt nur fuer das eigene Deployable. Ein
 * fremder Orb in der Naehe, der ablaeuft, steht dort nicht, und den muss man auch nicht ersetzen.
 *
 * <p><b>Wie andere Mods es machen.</b> SkyHanni (Deployable.kt), Riccio's Fishing Utils und
 * SkyCubed lesen den Namen des Ruestungsstaenders ueber dem Deployable ("Umberella 298s",
 * "Plasmaflux 28s") und rechnen die Restzeit selbst aus. Das taugt fuer eine Anzeige der Restzeit,
 * aber fuer "ist abgelaufen" ist es der Umweg: Der Staender ist nur da, solange er gerendert
 * wird - wer weitergelaufen ist, verliert ihn, und ein fremdes Deployable in Reichweite sieht
 * genauso aus wie das eigene (SkyHanni fragt deshalb nach der Entfernung). Die Chatzeile nennt den
 * Zeitpunkt, an dem das eigene Deployable wirklich verschwindet, und sonst nichts.
 *
 * <p>Zwei weitere Zeilen kennt Feesh (DeployablesTimer.kt, Apache 2.0), beide Texte von Hypixel:
 * "Your flare disappeared because you were too far away!" - der Flare ist weg, bevor er ablief,
 * das wird hier wie ein Ablauf gemeldet - und "Your previous X was removed!". Die zweite kommt, wenn
 * man ein neues Deployable setzt und das alte dadurch verschwindet; das ist gewollt und bekommt
 * keine Meldung.
 *
 * <p>Welche Sorten gemeldet werden, steht in den Einstellungen. Ein Name, den keine Sorte kennt,
 * wird nicht gemeldet, aber einmal im Log genannt - so faellt auf, wenn Hypixel ein neues
 * Deployable bringt.
 */
public final class DeployableReminder {

    /** "Your Plasmaflux Power Orb despawned." - ohne Farbcodes */
    private static final Pattern DESPAWNED = Pattern.compile("^Your (.+) despawned\\.$");
    private static final Pattern FLARE_TOO_FAR = Pattern.compile(
            "^Your flare disappeared because you were too far away!$");
    private static final int UNKNOWN_LIMIT = 50;
    private static final long BANNER_MILLIS = 4000L;

    private static final Set<String> unknownLogged = new HashSet<>();

    /** Die Sorten, aus denen man waehlt. Die Farbe ist die des Banners */
    public enum Kind {
        POWER_ORB(0xFF55FF),
        FLARE(0xFF5555),
        UMBERELLA(0x5555FF),
        LANTERN(0xFFAA00),
        TOTEM(0xAA00AA),
        BLACK_HOLE(0xAAAAAA);

        final int rgb;

        Kind(int rgb) {
            this.rgb = rgb;
        }
    }

    /** Was eine Chatzeile gemeldet hat: welche Sorte, unter welchem Namen, und ob es zu weit weg war */
    record Hit(Kind kind, String name, boolean tooFar) {
    }

    private DeployableReminder() {
    }

    private static DeployablesCategory cfg() {
        return ModConfig.INSTANCE.deployables;
    }

    /** Eine Chatzeile, ohne Farbcodes */
    public static void onChatMessage(String message) {
        if (!FeatureGate.deployables() || !GameState.Server.isSkyblock()) return;

        Hit hit = read(message);
        if (hit == null || !wanted(hit.kind())) return;
        fire(hit);
    }

    /**
     * Was die Zeile meldet - oder null, wenn sie mit Deployables nichts zu tun hat oder den Namen
     * keine Sorte kennt.
     */
    static Hit read(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) return null;

        if (FLARE_TOO_FAR.matcher(text).matches()) return new Hit(Kind.FLARE, "Flare", true);

        Matcher m = DESPAWNED.matcher(text);
        if (!m.matches()) return null;

        String name = m.group(1).trim();
        Kind kind = kindOf(name);
        if (kind == null) {
            if (unknownLogged.size() < UNKNOWN_LIMIT && unknownLogged.add(name)) {
                ShokiMod.LOGGER.info("[Deployables] \"{}\" despawned - no kind knows that name, so it is not reported", name);
            }
            return null;
        }
        return new Hit(kind, name, false);
    }

    /** Die Sorte zu einem Namen wie "Plasmaflux Power Orb", "Glacite Lantern" oder "Umberella" */
    static Kind kindOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("power orb") || n.contains("radiant") || n.contains("flux")) return Kind.POWER_ORB;
        if (n.contains("flare")) return Kind.FLARE;
        if (n.contains("umberella") || n.contains("umbrella")) return Kind.UMBERELLA;
        if (n.contains("lantern") || n.contains("will-o")) return Kind.LANTERN;
        if (n.contains("totem")) return Kind.TOTEM;
        if (n.contains("black hole")) return Kind.BLACK_HOLE;
        return null;
    }

    /** Hat man diese Sorte in den Einstellungen angehakt? */
    static boolean wanted(Kind kind) {
        DeployablesCategory c = cfg();
        return switch (kind) {
            case POWER_ORB -> c.powerOrbs;
            case FLARE -> c.flares;
            case UMBERELLA -> c.umberella;
            case LANTERN -> c.lanterns;
            case TOTEM -> c.totem;
            case BLACK_HOLE -> c.blackHole;
        };
    }

    private static void fire(Hit hit) {
        DeployablesCategory c = cfg();
        String headline = hit.tooFar() ? "Flare vanished" : "Deployable ran out";
        String where = hit.tooFar() ? "you were too far away" : "place a new one";
        if (c.banner) AlertBanner.show(headline, hit.name(), where, hit.kind().rgb, BANNER_MILLIS);

        // Chat und Ton brauchen eine Welt
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        if (c.chatLine) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(hit.name()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(hit.tooFar()
                            ? " vanished - you were too far away."
                            : " ran out.").withStyle(ChatFormatting.YELLOW)));
        }
        sound(client);
        ShokiMod.LOGGER.info("[Deployables] {} {}", hit.name(), hit.tooFar() ? "vanished (too far away)" : "ran out");
    }

    /**
     * Der Ton: die gewaehlte Datei, sonst das eingebaute Pling.
     *
     * Fehlt die gewaehlte Datei (umbenannt, geloescht), faellt es aufs Pling zurueck - eine Meldung,
     * die stumm bleibt, ist schlimmer als eine mit dem falschen Ton.
     */
    private static void sound(Minecraft client) {
        DeployablesCategory c = cfg();
        if (!c.sound) return;

        String file = c.soundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), DeployableReminder.class);
            return;
        }
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
    }

    /** Knopf in den Einstellungen: einmal vorfuehren, mit einem Plasmaflux Power Orb */
    public static void test() {
        fire(new Hit(Kind.POWER_ORB, "Plasmaflux Power Orb", false));
    }
}
