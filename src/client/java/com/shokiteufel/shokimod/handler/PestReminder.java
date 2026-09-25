package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ReminderCategory;
import com.shokiteufel.shokimod.scanner.PestTraps;
import com.shokiteufel.shokimod.util.AlertVolume;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Die Erinnerung, wenn die Pest-Fallen voll sind.
 *
 * Eine volle Falle faengt nichts mehr - sie steht herum, bis jemand sie leert. Wer
 * das verpasst, verliert keine Zeit an einer schlechten Route, sondern schlicht die
 * ganze Zeit dazwischen.
 *
 * Gezaehlt wird nicht, sondern abgelesen: {@link PestTraps} nimmt den Stand aus der
 * Tab-Liste, die Hypixel selbst schreibt. Eine Vorhersage aus der Fangrate waere
 * eine Behauptung - hier steht, was wirklich drin ist.
 *
 * Die Erinnerung kommt einmal, sobald die eingestellte Zahl erreicht ist, und danach
 * hoechstens im eingestellten Abstand. Sobald wieder geleert wurde, ist sie scharf
 * gestellt wie am Anfang.
 */
public final class PestReminder {

    /** Nur im Garden, und dort auch nur alle paar Sekunden */
    private static final long CHECK_INTERVAL_MILLIS = 3_000L;

    private static long lastCheck = 0L;
    private static long lastReminder = 0L;
    private static boolean armed = true;

    private PestReminder() {
    }

    private static ReminderCategory cfg() {
        return ModConfig.INSTANCE.chat.reminder;
    }

    public static boolean enabled() {
        return cfg().pestTrapAlert;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PestReminder::tick);
    }

    /** Nach einem Weltwechsel gilt der alte Stand nicht mehr */
    public static void reset() {
        armed = true;
        lastReminder = 0L;
        PestTraps.reset();
    }

    private static void tick(Minecraft client) {
        if (!enabled() || client.player == null || !GameState.Server.isSkyblock()) return;

        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MILLIS) return;
        lastCheck = now;

        PestTraps.hintIfMissing();
        if (!PestTraps.available()) return;

        int threshold = Math.max(1, cfg().pestTrapAt);
        int full = PestTraps.fullCount();
        if (full < threshold) {
            // Geleert: die naechste volle Falle darf wieder melden
            armed = true;
            return;
        }

        long repeat = Math.max(0, cfg().pestTrapRepeatMinutes) * 60_000L;
        boolean again = repeat > 0 && lastReminder > 0 && now - lastReminder >= repeat;
        if (!armed && !again) return;

        armed = false;
        lastReminder = now;
        remind(client, full);
    }

    private static void remind(Minecraft client, int full) {
        client.player.sendSystemMessage(message(full, PestTraps.fullTraps()));
        if (cfg().pestTrapSound) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
        }
        ShokiMod.LOGGER.info("[Pests] {} of {} traps full: {}", full, PestTraps.placed(),
                PestTraps.fullTraps());
    }

    /** Die Zeile: wie viele voll sind, welche das sind, und ein Knopf zum Hinreisen */
    static Component message(int full, List<Integer> traps) {
        StringBuilder hover = new StringBuilder("Full traps:");
        for (int number : traps) hover.append("\n- #").append(number);
        if (PestTraps.placed() > 0) {
            hover.append("\nPlaced: ").append(PestTraps.placed()).append(" / ").append(PestTraps.maximum());
        }
        List<Integer> hungry = PestTraps.trapsWithoutBait();
        if (!hungry.isEmpty()) hover.append("\nNo bait: ").append(hungry);

        String text = full == 1 ? "1 pest trap is full" : full + " pest traps are full";
        MutableComponent line = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GOLD)
                        .withStyle(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Component.literal(hover.toString())))))
                .append(Component.literal(" "));

        String command = cfg().pestTrapCommand == null ? "" : cfg().pestTrapCommand.trim();
        MutableComponent click = Component.literal("[Go]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        if (!command.isEmpty()) {
            String run = command.startsWith("/") ? command : "/" + command;
            click = click.withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand(run))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run " + run))));
        }
        return line.append(click);
    }

    /** Knopf in den Einstellungen: die Zeile mit einem Beispiel */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(message(3, List.of(1, 2, 3)));
        if (cfg().pestTrapSound) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
        }
    }
}
