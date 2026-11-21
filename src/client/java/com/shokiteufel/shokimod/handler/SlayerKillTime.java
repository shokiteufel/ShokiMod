package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slayer: wie lange der Boss gedauert hat, in den Chat.
 *
 * <p>Nach jedem Boss kommen zwei Zeilen (jede laesst sich einzeln einschalten):
 * <ul>
 *   <li>"Boss Kill time": vom Erscheinen des Bosses bis zu seinem Tod, mit dem persoenlichen Rang unter allen Zeiten dieses Bosses
 *       und dieser Stufe und "!!NEW PERSONAL BEST!!" bei einem Rekord. Diese Zeiten bleiben ueber die Sitzung hinaus gespeichert.</li>
 *   <li>"Quest Kill time": von der Annahme der Quest bis zum Tod des Bosses, mit dem Durchschnitt dieser Sitzung dahinter. Die Sitzung
 *       beginnt neu, sobald man die Lobby wechselt, damit nichts zu lange mitgeschleppt wird.</li>
 * </ul>
 *
 * <p>Die Uhr selbst ({@link SlayerKillClock}) und die Zahlen ({@link SlayerKillStats}) kennen kein Minecraft; hier liegen nur die Quellen
 * (Seitenleiste, Chat), die Speicherung und die Ausgabe. Wie bei SkyHanni und Skyblocker gilt die Seitenleiste ("Slay the boss!") als Beginn des
 * Kampfes; der Chat ("SLAYER BOSS! The X spawned!") kam in den Mitschnitten nicht bei jeder Quest.
 */
public final class SlayerKillTime {

    /** Alle fuenf Ticks die Seitenleiste lesen: genauer muss ein Kampf von mehr als einer Sekunde nicht sein (hoechstens 0,25 s Fehler) */
    private static final int READ_EVERY_TICKS = 5;
    /**
     * Die Zeilen kommen kurz nach der Abschlussmeldung: Hypixel schreibt dazu den Kasten "SLAYER QUEST COMPLETE ..." mit mehreren Zeilen, und
     * ein Einschub mittendrin sahe falsch aus. Dreiviertel Sekunde danach steht der Kasten.
     */
    private static final int OUTPUT_DELAY_TICKS = 15;

    private static final Pattern QUEST_START = Pattern.compile("^SLAYER QUEST STARTED!");
    private static final Pattern QUEST_COMPLETE = Pattern.compile("^SLAYER QUEST COMPLETE!");
    private static final Pattern QUEST_FAILED = Pattern.compile("^SLAYER QUEST FAILED!");
    /** "SLAYER BOSS! The ☠ Packmaster Soul spawned!" - das Zeichen davor gibt es nicht bei jedem Boss */
    private static final Pattern BOSS_SPAWN = Pattern.compile("^SLAYER BOSS!\\s+The\\s+(?:\\S{1,2}\\s+)?(.+?)\\s+spawned!");

    private static final SlayerKillClock CLOCK = new SlayerKillClock();
    /** Der Durchschnitt dieser Sitzung, je Boss und Stufe */
    private static final Map<String, SlayerKillStats.Session> SESSION = new HashMap<>();
    private static final List<Component> OUTBOX = new ArrayList<>();

    private static int ticks = 0;
    private static int outboxDelay = 0;
    private static Object lastLevel = null;

    private SlayerKillTime() {
    }

    public static boolean enabled() {
        return FeatureGate.slayerKillTime();
    }

    /** Einmal je Tick: die Seitenleiste lesen und die wartenden Zeilen ausgeben */
    public static void tick(Minecraft client) {
        if (client == null || client.level == null || !enabled() || !GameState.Server.isSkyblock()) {
            return;
        }
        flush(client);
        // Eine neue Welt (Insel, Lobby) ist eine neue Sitzung
        if (client.level != lastLevel) {
            if (lastLevel != null) {
                newLobby();
            }
            lastLevel = client.level;
        }
        if (++ticks % READ_EVERY_TICKS != 0) {
            return;
        }
        List<String> lines = ScoreboardUtils.getSidebarLines(client);
        // Ist die Seitenleiste gerade nicht da, ist das keine Antwort
        if (lines.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        int at = SlayerBossSpawn.bossLine(lines);
        if (at >= 0) {
            List<String> plain = new ArrayList<>(lines.size());
            for (String line : lines) {
                plain.add(ScoreboardUtils.stripColor(line));
            }
            CLOCK.bossSeen(SlayerKillClock.keyAbove(plain, at), now);
        } else {
            CLOCK.bossGone(now);
        }
        CLOCK.timeout(now);
    }

    /** Aus dem Chat: der Beginn der Quest, das Erscheinen des Bosses, sein Tod, ein Misserfolg */
    public static void onChatMessage(String plain) {
        if (plain == null || !enabled() || !GameState.Server.isSkyblock()) {
            return;
        }
        String line = plain.trim();
        if (line.isEmpty() || line.charAt(0) != 'S') {
            return;
        }
        long now = System.nanoTime();
        if (QUEST_START.matcher(line).find()) {
            CLOCK.questStarted(now);
            return;
        }
        Matcher spawn = BOSS_SPAWN.matcher(line);
        if (spawn.find()) {
            CLOCK.chatSpawn(spawn.group(1), now);
            return;
        }
        if (QUEST_FAILED.matcher(line).find()) {
            CLOCK.failed();
            return;
        }
        if (QUEST_COMPLETE.matcher(line).find()) {
            finish(CLOCK.finished(now));
        }
    }

    /** Ein Lobby-Wechsel: die Sitzung beginnt neu, die persoenlichen Zeiten bleiben */
    public static void newLobby() {
        SESSION.clear();
        CLOCK.lobbyChanged();
        ShokiMod.LOGGER.info("[Slayer] new lobby - the kill time session starts over");
    }

    /** Der Reset-Knopf: erst fragen, dann alle persoenlichen Zeiten loeschen */
    public static void askReset() {
        com.shokiteufel.shokimod.gui.ResetConfirm.ask("Slayer kill times",
                "Deletes every personal kill time, so the ranks and the personal bests start from nothing.",
                SlayerKillTime::resetRecords);
    }

    static void resetRecords() {
        records().clear();
        SESSION.clear();
        ModConfig.INSTANCE.saveNow();
    }

    private static Map<String, List<Long>> records() {
        ModConfig.SlayerCategory slayer = ModConfig.INSTANCE.slayer;
        if (slayer.killTimes == null) {
            slayer.killTimes = new LinkedHashMap<>();
        }
        return slayer.killTimes;
    }

    private static void finish(SlayerKillClock.Kill kill) {
        if (kill == null) {
            ShokiMod.LOGGER.info("[Slayer] quest complete, but no boss start was seen - no kill time");
            return;
        }
        long bossMillis = kill.bossNanos() / 1_000_000L;
        long questMillis = kill.questNanos() < 0L ? -1L : kill.questNanos() / 1_000_000L;
        if (!SlayerKillStats.plausible(bossMillis)) {
            ShokiMod.LOGGER.info("[Slayer] kill time {} ms of '{}' is out of range - ignored", bossMillis, kill.key());
            return;
        }
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        ModConfig.SlayerCategory cfg = ModConfig.INSTANCE.slayer;

        List<Long> times = records().computeIfAbsent(kill.key(), k -> new ArrayList<>());
        SlayerKillStats.Standing standing = SlayerKillStats.record(times, bossMillis);
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Slayer] {}: {}", kill.key(), SlayerKillStats.bossLine(bossMillis, standing, locale));

        if (cfg.bossKillTime) {
            OUTBOX.add(prefix().append(Component.literal("Boss Kill time: ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(SlayerKillStats.format(bossMillis, locale)).withStyle(ChatFormatting.GREEN))
                    .append(standing.best()
                            ? Component.literal("  " + SlayerKillStats.BEST_TEXT).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            : Component.empty())
                    .append(Component.literal("  (" + SlayerKillStats.rankText(standing) + ")").withStyle(ChatFormatting.GRAY)));
        }
        if (questMillis >= 0L) {
            SlayerKillStats.Session session = SESSION.computeIfAbsent(kill.key(), k -> new SlayerKillStats.Session());
            session.add(questMillis);
            if (cfg.questKillTime) {
                String average = SlayerKillStats.format(session.average(), locale);
                OUTBOX.add(prefix().append(Component.literal("Quest Kill time: ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(SlayerKillStats.format(questMillis, locale)).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("  (Avg. Time this Session: " + average + ")").withStyle(ChatFormatting.GRAY)));
                ShokiMod.LOGGER.info("[Slayer] {}", SlayerKillStats.questLine(questMillis, session.average(), locale));
            }
        }
        outboxDelay = OUTPUT_DELAY_TICKS;
    }

    private static MutableComponent prefix() {
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA);
    }

    private static void flush(Minecraft client) {
        if (OUTBOX.isEmpty() || --outboxDelay > 0) {
            return;
        }
        if (client.player != null) {
            for (Component line : OUTBOX) {
                client.player.sendSystemMessage(line);
            }
        }
        OUTBOX.clear();
    }
}
