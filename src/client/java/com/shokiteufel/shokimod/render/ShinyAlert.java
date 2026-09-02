package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Einblendung fuer seltene Critter - und der Ruf an die Party.
 *
 * Hypixel nennt die seltene Variante "Sparkling <Art>"; angezeigt wird "SHINY!".
 * Gezeichnet wird ueber AlertBanner, das sich auch die Chatregeln teilen.
 *
 * Wer einen Shiny sieht, ruft die Party: ein fester Satz geht per /pc hinaus. Und
 * wer diesen Satz im Chat liest - von wem auch immer - hoert einen Ton. Der Satz
 * ist absichtlich nicht einstellbar: nur wenn alle denselben schicken, hoert auch
 * jeder den Ruf der anderen. Der eigene Ruf, der vom Server zurueckkommt, loest
 * den Ton nicht aus; den Fund hat man ja selbst vor Augen.
 */
public final class ShinyAlert {

    private static final String HEADLINE = "SHINY!";
    private static final long DISPLAY_MILLIS = 7000L;

    /** Der Ruf. Fest, damit jede Mod in der Party denselben Satz erkennt */
    public static final String CALL = "OMG!! WHO IS THAT SHINY?!";
    private static final String CALL_LOWER = CALL.toLowerCase(Locale.ROOT);

    /**
     * Bereits gemeldete Funde. Gemerkt wird die UUID des Namenstraegers, nicht die
     * Position - so loest derselbe Critter nicht bei jeder Bewegung erneut aus.
     */
    private static final Set<UUID> announced = new HashSet<>();

    private ShinyAlert() {
    }

    /** Beim Serverwechsel vergessen, sonst bleibt ein Fund fuer immer als gemeldet stehen */
    public static void reset() {
        announced.clear();
    }

    public static void onSighting(Entity nameTag, String plainName, Entity body) {
        ModConfig.SafariCategory cfg = ModConfig.INSTANCE.safari;
        if (!cfg.shinyAlertEnabled) return;
        if (!announced.add(nameTag.getUUID())) return;

        BlockPos pos = body != null ? body.blockPosition() : nameTag.blockPosition();
        AlertBanner.show(HEADLINE, plainName,
                pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                cfg.shinyColorRGB(), DISPLAY_MILLIS);

        if (cfg.shinyCallParty) callParty();
    }

    private static void callParty() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        ShokiMod.LOGGER.info("[Shiny] calling the party: {}", CALL);
        connection.sendCommand("pc " + CALL);
    }

    /**
     * Der Ruf eines anderen im Chat.
     *
     * @param plain die Chatzeile ohne Farbcodes
     */
    public static void onChatMessage(String plain) {
        if (!ModConfig.INSTANCE.safari.shinyCallSound || plain == null) return;

        String line = plain.trim();
        if (!line.toLowerCase(Locale.ROOT).contains(CALL_LOWER)) return;
        if (isOwnEcho(line)) return;

        ShokiMod.LOGGER.info("[Shiny] party call heard: {}", line);
        playCallSound();
    }

    /**
     * "Party > [MVP+] Name: Text" - steht der eigene Name vor dem Doppelpunkt, ist es
     * der eigene Ruf, der vom Server zurueckkommt.
     */
    private static boolean isOwnEcho(String line) {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() == null) return false;
        String self = client.getUser().getName();
        int colon = line.indexOf(':');
        if (colon < 0) return false;
        return line.substring(0, colon).contains(self);
    }

    /** Der Testknopf: derselbe Ton, den ein fremder Ruf ausloest */
    public static void testCallSound() {
        playCallSound();
    }

    /**
     * Die eingestellte Datei, sonst Minecrafts Notenblock.
     *
     * Die Standarddatei liegt der Mod bei und wird beim Start nach
     * config/shokimod/sounds gelegt, falls dort noch keine liegt. Eine fehlende
     * Datei soll den Alarm nicht stumm machen, sondern nur anders klingen lassen.
     */
    private static void playCallSound() {
        String file = ModConfig.INSTANCE.safari.shinyCallSoundFile;
        if (file != null && !file.isBlank() && CustomSoundPlayer.isSupported(file)
                && Files.exists(CustomSoundPlayer.SOUND_DIRECTORY.resolve(file))) {
            CustomSoundPlayer.play(file, AlertVolume.factor(), ShinyAlert.class);
            return;
        }

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, AlertVolume.factor()));
    }
}
