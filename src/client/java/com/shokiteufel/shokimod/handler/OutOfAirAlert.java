package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.OutOfAirCategory;
import com.shokiteufel.shokimod.data.ModConstants;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Warnt, wenn beim Tauchen die Luft knapp wird - bevor man zu ertrinken anfaengt.
 *
 * <p>Nachgebaut aus Feesh (Apache 2.0, Sleepy-Panda/Feesh, {@code features/alerts/OutOfAirAlert.kt}): Einmal in
 * der Sekunde wird nachgesehen, wie viel Luft der Spieler noch hat. Faellt sie auf 15 Prozent oder darunter,
 * kommt die Meldung - einmal. Erst wenn die Luft wieder ueber die Schwelle gestiegen ist, kann sie noch einmal
 * kommen. Wie bei Feesh gilt das nur in den beiden Gebieten, in denen man unter Wasser angelt: im Moonglade
 * Marsh und im Torrhus Canyon.
 *
 * <p>15 Prozent von 300 sind 45 Spielschritte, gut zwei Sekunden. Weil nur einmal in der Sekunde nachgesehen
 * wird, bleiben davon ein bis zwei - knapp, aber das ist Feeshs Wert, und er ist auf Absicht fest.
 */
public final class OutOfAirAlert {

    /** Ab so wenig Luft wird gewarnt - Feeshs Wert, fest */
    static final double THRESHOLD_PERCENT = 15.0;
    private static final int CHECK_TICKS = 20;
    private static final long BANNER_MILLIS = 2500L;

    private static boolean alerted = false;
    private static int ticks = 0;
    private static Object lastLevel = null;

    private OutOfAirAlert() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OutOfAirAlert::tick);
    }

    private static OutOfAirCategory cfg() {
        return ModConfig.INSTANCE.fishing.air;
    }

    /** Gilt die Warnung in diesem Gebiet? Nur dort, wo man unter Wasser angelt */
    static boolean applies(String map) {
        return ModConstants.MAP_MOONGLADE_MARSH.equals(map) || ModConstants.MAP_TORRHUS_CANYON.equals(map);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.outOfAir()) return;
        // Eine neue Welt vergisst, dass schon gewarnt wurde
        if (client.level != lastLevel) {
            lastLevel = client.level;
            alerted = false;
        }
        if (client.player == null || !GameState.Server.isSkyblock() || !applies(GameState.Server.map)) return;

        if (++ticks < CHECK_TICKS) return;
        ticks = 0;

        if (evaluate(client.player.getAirSupply(), client.player.getMaxAirSupply())) fire(client);
    }

    /**
     * Muss jetzt gewarnt werden? Merkt sich dabei, dass gewarnt wurde.
     *
     * @param air    die Luft, die noch da ist (in Spielschritten)
     * @param maxAir der volle Vorrat
     */
    static boolean evaluate(int air, int maxAir) {
        if (maxAir <= 0) return false;
        double percent = air * 100.0 / maxAir;
        if (percent > THRESHOLD_PERCENT) {
            alerted = false;
            return false;
        }
        if (alerted) return false;
        alerted = true;
        return true;
    }

    /** Fuer die Probe: das Gedaechtnis loeschen */
    static void resetForTest() {
        alerted = false;
        ticks = 0;
        lastLevel = null;
    }

    // ---- Die Meldung ----

    private static void fire(Minecraft client) {
        OutOfAirCategory c = cfg();
        if (c.banner) {
            BannerDesign look = ModConfig.INSTANCE.chat.banner.designOrDefault(c.bannerDesign);
            // Feesh zeigt den Titel nicht fett: das "§r" setzt die Fettschrift des Designs zurueck, die Farbe
            // (rot, vom Banner) bleibt
            String headline = look.background == BannerDesign.Background.TITLE ? "§rOut of air soon!" : "Out of air soon!";
            DropBanner.show(look, "", headline, "", "", 0xFF5555, ItemStack.EMPTY, 0d);
        }
        if (client.player != null) {
            if (c.chatLine) {
                client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal("You are almost out of air!").withStyle(ChatFormatting.WHITE)));
            }
            sound(client);
        }
        ShokiMod.LOGGER.info("[OutOfAir] warned");
    }

    /** Der Ton: die gewaehlte Datei, sonst der Ertrink-Ton wie bei Feesh */
    private static void sound(Minecraft client) {
        OutOfAirCategory c = cfg();
        if (!c.sound) return;
        String file = c.soundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), OutOfAirAlert.class);
            return;
        }
        client.player.playSound(SoundEvents.PLAYER_HURT_DROWN, AlertVolume.factor(), 1.0f);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren, auch wenn die Warnung aus ist */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) fire(client);
    }
}
