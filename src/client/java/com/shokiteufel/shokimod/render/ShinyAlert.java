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
 * Wer einen Shiny sieht, kann die Party rufen: ein fester Satz geht per /pc hinaus.
 * Und wer diesen Satz im Chat liest - von wem auch immer - hoert einen Ton. So
 * erfaehrt jeder in der Party vom Fund, auch wer gerade woanders hinschaut. Der
 * eigene Ruf loest den Ton nicht aus; den Fund hat man ja selbst vor Augen.
 */
public final class ShinyAlert {

    private static final String HEADLINE = "SHINY!";
    private static final long DISPLAY_MILLIS = 7000L;

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

        if (cfg.shinyShareEnabled) shareWithParty(cfg.shinyShareMessage);
    }

    private static void shareWithParty(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) return;

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        ShokiMod.LOGGER.info("[Shiny] sharing to party: {}", text);
        connection.sendCommand("pc " + text);
    }

    /**
     * Der Ruf eines anderen im Chat.
     *
     * @param plain die Chatzeile ohne Farbcodes
     */
    public static void onChatMessage(String plain) {
        ModConfig.SafariCategory cfg = ModConfig.INSTANCE.safari;
        if (!cfg.shinyChatAlert || plain == null) return;

        String message = cfg.shinyShareMessage == null ? "" : cfg.shinyShareMessage.trim();
        if (message.isEmpty()) return;

        String line = plain.trim();
        if (!line.toLowerCase(Locale.ROOT).contains(message.toLowerCase(Locale.ROOT))) return;
        if (isOwnEcho(line)) return;

        ShokiMod.LOGGER.info("[Shiny] party call heard: {}", line);
        playChatAlert();
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
    public static void testChatAlert() {
        playChatAlert();
    }

    /**
     * Die eingestellte Datei, sonst Minecrafts Notenblock.
     *
     * Die Datei muss man selbst in config/shokimod/sounds legen - eine fehlende soll
     * den Alarm nicht stumm machen, sondern nur anders klingen lassen.
     */
    private static void playChatAlert() {
        String file = ModConfig.INSTANCE.safari.shinyChatAlertSound;
        if (file != null && !file.isBlank() && CustomSoundPlayer.isSupported(file)
                && Files.exists(CustomSoundPlayer.SOUND_DIRECTORY.resolve(file))) {
            CustomSoundPlayer.play(file, AlertVolume.factor(), ShinyAlert.class);
            return;
        }

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, AlertVolume.factor()));
    }
}
