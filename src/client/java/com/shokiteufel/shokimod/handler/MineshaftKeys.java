package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.scanner.CorpseKeys;
import com.shokiteufel.shokimod.scanner.MiningState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Beim Betreten eines Glacite Mineshafts: die Schluessel fuer die Leichen aus dem Sack holen.
 *
 * Steht in der Tab-Liste, dass im Schacht zwei Umber- und eine Tungsten-Leiche liegen, kommt
 * einmal je Schacht eine Chatzeile mit einem Klick: {@code [Get Keys from Sacks]}. Der Klick
 * fuehrt {@code /gfs UMBER_KEY 2} und {@code /gfs TUNGSTEN_KEY 1} aus, eine Sekunde auseinander
 * (siehe {@link CommandSequence}).
 *
 * Gezaehlt wird, was noch zu holen ist: Wer schon einen Umber-Schluessel im Inventar hat, holt bei
 * zwei Leichen nur einen. Lapis braucht keinen Schluessel; Vanguard (Skeleton Key) war nicht
 * gewuenscht und bleibt aussen vor.
 *
 * Der Klick fuehrt zu {@code /shoki sackkeys}, nicht zu den Befehlen selbst: Was geholt wird, steht
 * in dieser Klasse, nicht in der Chatzeile, und die Zeile gilt nur fuer den Schacht, in dem sie kam
 * und nur einmal - ein zweiter Klick holt nichts doppelt.
 */
public final class MineshaftKeys {

    /** Das Kommando, das die Chatzeile ausfuehrt */
    public static final String CLICK_COMMAND = "/shoki sackkeys";
    private static final String CLICK_TEXT = "[Get Keys from Sacks]";

    /** Eine Schluesselsorte im Schacht: wie viele Leichen, wie viele Schluessel man schon dabei hat */
    record Need(String keyId, String label, int corpses, int carried) {
        int missing() {
            return Math.max(0, corpses - carried);
        }
    }

    /** Der Schacht, fuer den die Zeile schon kam - einmal je Schacht */
    private static String announcedFor = "";
    /** Was der Klick holt, und fuer welchen Schacht */
    private static List<Need> pending = List.of();
    private static String pendingFor = "";

    private MineshaftKeys() {
    }

    /**
     * Was in diesem Schacht an Schluesseln gebraucht wird: Umber, dann Tungsten - nur die Sorten, die vorkommen.
     * Offene Leichen zaehlen, nicht die schon geleerten: fuer die braucht es nichts mehr.
     */
    static List<Need> plan(List<MiningState.Corpse> found, ToIntFunction<String> carried) {
        int umber = 0;
        int tungsten = 0;
        for (MiningState.Corpse c : found) {
            String type = c.type().toLowerCase(Locale.ROOT);
            if (type.equals("umber")) umber += c.open();
            else if (type.equals("tungsten")) tungsten += c.open();
        }
        List<Need> out = new ArrayList<>(2);
        if (umber > 0) out.add(new Need("UMBER_KEY", "Umber", umber, carried.applyAsInt("UMBER_KEY")));
        if (tungsten > 0) out.add(new Need("TUNGSTEN_KEY", "Tungsten", tungsten, carried.applyAsInt("TUNGSTEN_KEY")));
        return out;
    }

    /** Die Befehle, die der Klick ausfuehrt: je Sorte mit fehlenden Schluesseln einer, ohne Schraegstrich */
    static List<String> commands(List<Need> plan) {
        List<String> out = new ArrayList<>(plan.size());
        for (Need need : plan) {
            if (need.missing() > 0) out.add("gfs " + need.keyId() + " " + need.missing());
        }
        return out;
    }

    /** "2 Umber, 1 Tungsten" - was im Schacht liegt */
    static String corpsesText(List<Need> plan) {
        StringBuilder out = new StringBuilder();
        for (Need need : plan) {
            if (out.length() > 0) out.append(", ");
            out.append(need.corpses()).append(' ').append(need.label());
        }
        return out.toString();
    }

    /** "1 Umber" - was man schon im Inventar hat, oder leer */
    static String carriedText(List<Need> plan) {
        StringBuilder out = new StringBuilder();
        for (Need need : plan) {
            if (need.carried() <= 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append(need.carried()).append(' ').append(need.label());
        }
        return out.toString();
    }

    /** Die Liste aus der Tab-Liste, bei jedem Lesen gerufen (MiningState). Meldet einmal je Schacht */
    public static void onCorpses(List<MiningState.Corpse> found) {
        if (!FeatureGate.mineshaftKeys() || found.isEmpty()) return;
        String shaft = GameState.Server.id;
        if (shaft == null || shaft.isBlank() || shaft.equals(announcedFor)) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        List<Need> plan = plan(found, CorpseKeys::count);
        // Keine Umber- und keine Tungsten-Leiche (oder die Liste ist noch nicht ganz da): nichts zu melden, beim naechsten Lesen nochmal
        if (plan.isEmpty()) return;
        // Erst merken, dann senden: Die Zeile kommt hoechstens einmal je Schacht
        announcedFor = shaft;
        // Alle Schluessel schon im Inventar: nichts zu holen, nur ein Vermerk im Log
        if (commands(plan).isEmpty()) {
            ShokiMod.LOGGER.info("[Mineshaft] keys: nothing to fetch ({} corpses, carrying {})", corpsesText(plan), carriedText(plan));
            return;
        }
        pending = plan;
        pendingFor = shaft;
        ShokiMod.LOGGER.info("[Mineshaft] keys offered: {} (carrying {})", commands(plan), carriedText(plan).isEmpty() ? "none" : carriedText(plan));
        client.player.sendSystemMessage(message(plan));
    }

    /** Die Zeile: was im Schacht liegt, und der Klick */
    static Component message(List<Need> plan) {
        StringBuilder hover = new StringBuilder("Runs, one second apart:");
        for (String command : commands(plan)) hover.append("\n/").append(command);
        String carried = carriedText(plan);
        if (!carried.isEmpty()) hover.append("\nYou already carry: ").append(carried);

        MutableComponent click = Component.literal(CLICK_TEXT).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(CLICK_COMMAND))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover.toString()))));
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("Corpses here: " + corpsesText(plan) + " ").withStyle(ChatFormatting.GOLD))
                .append(click);
    }

    /** Der Klick: die Schluessel holen - einmal, und nur im Schacht, in dem die Zeile kam */
    public static void run() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        if (pending.isEmpty() || !pendingFor.equals(GameState.Server.id)) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal("Nothing to fetch - that line was used already or belongs to an earlier shaft.")
                            .withStyle(ChatFormatting.GRAY)));
            return;
        }
        List<String> commands = commands(pending);
        pending = List.of();
        ShokiMod.LOGGER.info("[Mineshaft] fetching keys: {}", commands);
        CommandSequence.enqueue(commands);
    }

    /** Nur fuer die Tests */
    static void resetForTests() {
        announcedFor = "";
        pending = List.of();
        pendingFor = "";
    }
}
