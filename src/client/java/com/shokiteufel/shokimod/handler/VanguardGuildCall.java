package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.GameState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein Vanguard-Schacht ruft die Gilde: "Vanguard + wenn ihr wollt".
 *
 * Wer im Gildenchat darauf mit einem Plus antwortet, will mit. Fuer diese Meldung gibt es
 * dann eine Zeile mit einem Klick, der ihn in die Party einlaedt - aber nur eine Weile lang
 * nach dem Ruf, damit ein Plus von vorhin keinen Knopf mehr bekommt.
 */
public final class VanguardGuildCall {

    /** So lange nach dem Ruf zaehlen Pluszeichen */
    private static final long WINDOW_MILLIS = 30_000L;
    private static final String TEXT = "Vanguard + wenn ihr wollt";

    /** "Guild > [MVP+] Name [Rang]: +" - Rang und Titel sind optional */
    private static final Pattern PLUS = Pattern.compile(
            "^Guild > (?:\[[^\]]+\]\s*)?(?<name>[A-Za-z0-9_]{1,16})(?:\s*\[[^\]]+\])?:\s*\+\s*$");

    private static long windowUntil = 0L;
    /** Der Schacht, fuer den schon gerufen wurde */
    private static String calledFor = "";
    private static final java.util.Set<String> invited = new java.util.HashSet<>();

    private VanguardGuildCall() {
    }

    /** Der Schacht hat eine Vanguard-Leiche: einmal je Schacht die Gilde rufen */
    public static void onVanguardShaft() {
        if (!ModConfig.INSTANCE.mining.mineshaft.vanguardGuildCall) return;
        String shaft = GameState.Server.id;
        if (shaft == null || shaft.isBlank() || shaft.equals(calledFor)) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) return;

        calledFor = shaft;
        windowUntil = System.currentTimeMillis() + WINDOW_MILLIS;
        invited.clear();
        ShokiMod.LOGGER.info("[Mining] telling the guild: {}", TEXT);
        client.getConnection().sendCommand("gc " + TEXT);
    }

    /** Ein Plus im Gildenchat, solange der Ruf frisch ist, bekommt den Einladen-Knopf */
    public static void onChatMessage(String plain) {
        if (plain == null || System.currentTimeMillis() > windowUntil) return;
        Matcher m = PLUS.matcher(plain.trim());
        if (!m.matches()) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        String name = m.group("name");
        if (name.equalsIgnoreCase(client.player.getName().getString())) return;
        // Wer zweimal ein Plus schreibt, bekommt keine zweite Zeile
        if (!invited.add(name.toLowerCase(java.util.Locale.ROOT))) return;

        String run = "/party invite " + name;
        Component click = Component.literal("[Click to invite " + name + "]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(run))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run " + run))));
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(name + " wants to join the Vanguard shaft ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(click));
    }
}
