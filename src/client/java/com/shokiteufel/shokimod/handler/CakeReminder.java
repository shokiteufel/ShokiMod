package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ReminderCategory;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.AlertVolume;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erinnert daran, wenn ein Kuchen-Buff abgelaufen ist.
 *
 * Uebernommen aus RiccioFishingUtils ("Outdated cake alert", GPL-3.0, Riccio): Wer
 * einen New Year Cake isst, bekommt "Yum! You gain +X ... for 48 hours!" in den Chat.
 * Die Mod merkt sich je Kuchen den Zeitpunkt; nach 48 Stunden gilt er als abgelaufen.
 * Neu abgelaufene Kuchen werden sofort gemeldet, danach in Abstaenden, solange einer
 * abgelaufen bleibt. Sind laut Tab-Liste wieder alle Kuchen aktiv, wird die Liste
 * geleert.
 *
 * Neu gegenueber RFU: die Meldung traegt einen anklickbaren Text [Get Cakes!], der
 * einen Befehl ausfuehrt - standardmaessig /visit SchiggyMobil.
 */
public final class CakeReminder {

    private static final Pattern EATEN = Pattern.compile(
            "^(?:Big )?Yum! You (?:gain|refresh) \\+\\d+. (.+) for 48 hours!$");
    private static final Pattern CENTURY = Pattern.compile("Century Cakes:\\s*\\d+[hms]\\s*\\((\\d+)/(\\d+)\\)");
    private static final long CAKE_MILLIS = 48L * 60 * 60 * 1000;
    private static final int CHECK_TICKS = 20;
    private static final String CLICK_TEXT = "[Get Cakes!]";

    private static final List<String> lastOutdated = new ArrayList<>();
    private static int ticks = 0;
    private static long lastRemindMillis = 0L;
    private static boolean dirty = false;

    private CakeReminder() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CakeReminder::tick);
    }

    private static ReminderCategory cfg() {
        return ModConfig.INSTANCE.chat.reminder;
    }

    /** Eine Chatzeile, ohne Farbcodes. Nur die Yum!-Zeile interessiert */
    public static void onChatMessage(String message) {
        if (!FeatureGate.cakeReminder()) return;
        Matcher m = EATEN.matcher(message.trim());
        if (!m.matches()) return;
        String name = m.group(1).trim();
        if (cfg().cakes == null) cfg().cakes = new HashMap<>();
        cfg().cakes.put(name, System.currentTimeMillis());
        lastOutdated.remove(name);
        dirty = true;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.cakeReminder()) return;
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (client.player == null || !GameState.Server.isSkyblock()) return;

        if (dirty) {
            dirty = false;
            ModConfig.INSTANCE.saveNow();
        }

        List<String> outdated = outdated();
        if (!outdated.isEmpty() && allCakesActiveInTab()) {
            clear();
            return;
        }

        List<String> fresh = new ArrayList<>();
        for (String name : outdated) if (!lastOutdated.contains(name)) fresh.add(name);
        long now = System.currentTimeMillis();
        if (!fresh.isEmpty()) {
            remind(client, fresh.size() + " of your cakes just expired!", outdated);
            lastRemindMillis = now;
        } else if (!outdated.isEmpty() && cfg().cakeRepeatMinutes > 0
                && now - lastRemindMillis >= cfg().cakeRepeatMinutes * 60_000L) {
            remind(client, "You have " + outdated.size() + " expired cakes!", outdated);
            lastRemindMillis = now;
        }
        lastOutdated.clear();
        lastOutdated.addAll(outdated);
    }

    private static List<String> outdated() {
        List<String> out = new ArrayList<>();
        Map<String, Long> cakes = cfg().cakes;
        if (cakes == null) return out;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : cakes.entrySet()) {
            if (entry.getValue() != null && now - entry.getValue() > CAKE_MILLIS) out.add(entry.getKey());
        }
        return out;
    }

    /** "Century Cakes: 5h (12/12)" in der Tab-Liste heisst: alle wieder aktiv */
    private static boolean allCakesActiveInTab() {
        for (String line : TabListScanner.lastLines()) {
            Matcher m = CENTURY.matcher(line);
            if (!m.find()) continue;
            try {
                return Integer.parseInt(m.group(1)) == Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static void remind(Minecraft client, String text, List<String> outdated) {
        client.player.sendSystemMessage(message(text, outdated));
        if (cfg().cakeSound) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 1.2f);
        }
    }

    /** Die Zeile: Text, dann der anklickbare Teil, der den Befehl ausfuehrt */
    static Component message(String text, List<String> outdated) {
        String command = cfg().cakeCommand == null ? "" : cfg().cakeCommand.trim();
        StringBuilder hover = new StringBuilder("Expired cakes:");
        for (String name : outdated) hover.append("\n- ").append(name);

        MutableComponent line = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GOLD)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover.toString())))))
                .append(Component.literal(" "));
        MutableComponent click = Component.literal(CLICK_TEXT).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        if (!command.isEmpty()) {
            String run = command.startsWith("/") ? command : "/" + command;
            click = click.withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand(run))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run " + run))));
        }
        return line.append(click);
    }

    /** Knopf in den Einstellungen: die Zeile mit einem Beispielkuchen */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        remind(client, "1 of your cakes just expired!", List.of("Test Cake"));
    }

    /** Knopf in den Einstellungen und Tab-Abgleich: alles vergessen */
    public static void clear() {
        if (cfg().cakes != null) cfg().cakes.clear();
        lastOutdated.clear();
        ModConfig.INSTANCE.saveNow();
    }
}
