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
 * Kampfes; der Chat ("SLAYER BOSS! The X spawned!") kam in den Mitschnitten nicht bei jeder Quest. Das Ende ist, wenn da, die Zeile "Your Slayer
 * Kill gave you ..." (der Moment des Todes), sonst die erste Todesmeldung im Chat wie bei Skyblocker - siehe {@link SlayerKillClock}.
 */
public final class SlayerKillTime {

    /**
     * Jeden Tick die Seitenleiste lesen (50 ms Raster, wie Skyblocker): Mit "alle fuenf Ticks" (0,25 s) lagen die Zeiten auf ,00 ,25 ,99, denn der
     * Beginn kam aus diesem Raster. Der Aufruf kostet wenig: SlayerSpawnCost liest dieselbe Seitenleiste ohnehin jeden Tick.
     */
    private static final int READ_EVERY_TICKS = 1;
    /**
     * Die Zeilen kommen kurz nach der Abschlussmeldung: Hypixel schreibt dazu den Kasten "SLAYER QUEST COMPLETE ..." mit mehreren Zeilen, und
     * ein Einschub mittendrin sahe falsch aus. Dreiviertel Sekunde danach steht der Kasten.
     */
    private static final int OUTPUT_DELAY_TICKS = 15;
    /**
     * Wurde der Kampf an der Zeile "Slayer Kill" gezaehlt, kommt der Kasten erst 1 bis 7 s spaeter: Die Zeilen warten darauf (und stehen dann dahinter),
     * hoechstens aber so lange, falls er ausbleibt - zwoelf Sekunden.
     */
    private static final int WAIT_FOR_BOX_TICKS = 240;

    private static final Pattern QUEST_START = Pattern.compile("^SLAYER QUEST STARTED!");
    /**
     * "Your Slayer Kill gave you 200 HP healing for 10 seconds!": im Moment des Todes (bei allen 69 Kaempfen der Mitschnitte vom 20./21.11.; im
     * Zombie-Log in derselben Sekunde, in der das Boss-Schild "0/10M" zeigt). Die genaueste Marke, aber ein Vorteil der Slayer-Stufe: nicht jeder sieht sie.
     */
    static final Pattern KILL_PERK = Pattern.compile("^Your Slayer Kill gave you");
    /** "YOU COCOONED YOUR SLAYER BOSS": bei 15 von 20 Kaempfen im Moment des Todes, bei 5 aber 8 bis 9 s davor - allein kein sicheres Ende */
    static final Pattern BOSS_COCOON = Pattern.compile("^YOU COCOONED YOUR SLAYER BOSS");
    /** Die endgueltige Todesmeldung: 1 bis 2 s nach dem Tod, nach "COCOONED" 5 bis 7 s; "SLAIN" kam in keinem Kampf */
    static final Pattern BOSS_FINAL = Pattern.compile("^(?:NICE! SLAYER BOSS SLAIN!|SLAYER QUEST COMPLETE!)");
    /** Das Ende der Quest, wie bei SkyHanni ({@code SlayerApi.questCompletePattern}): nur diese Zeile, nicht "SLAYER BOSS SLAIN!" */
    static final Pattern QUEST_COMPLETE = Pattern.compile("^SLAYER QUEST COMPLETE!");
    static final Pattern QUEST_FAILED = Pattern.compile("^(?:SLAYER QUEST FAILED!|Your Slayer Quest has been cancelled!)");
    /** "SLAYER BOSS! The ☠ Packmaster Soul spawned!" - das Zeichen davor gibt es nicht bei jedem Boss */
    static final Pattern BOSS_SPAWN = Pattern.compile("^SLAYER BOSS!\\s+The\\s+(?:\\S{1,2}\\s+)?(.+?)\\s+spawned!");

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
        // Eine Todesmeldung, die vergeblich auf die Abschlussmeldung wartete (Lobbywechsel), zaehlt trotzdem
        finish(CLOCK.timeout(now), false);
    }

    /** Aus dem Chat: der Beginn der Quest, das Erscheinen des Bosses, sein Tod (die Zeile "Slayer Kill" oder die Todesmeldungen), ein Misserfolg */
    public static void onChatMessage(String plain) {
        if (plain == null || !enabled() || !GameState.Server.isSkyblock()) {
            return;
        }
        String line = plain.trim();
        // Alle Zeilen, die hier zaehlen, beginnen mit S (SLAYER ...), N (NICE!) oder Y (YOU COCOONED, Your Slayer Quest ...)
        if (line.isEmpty() || "SNY".indexOf(line.charAt(0)) < 0) {
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
        if (KILL_PERK.matcher(line).find()) {
            finish(CLOCK.killMark(now), false);
            return;
        }
        if (BOSS_COCOON.matcher(line).find()) {
            finish(CLOCK.deathMessage(now, false), false);
            return;
        }
        if (BOSS_FINAL.matcher(line).find()) {
            finish(CLOCK.deathMessage(now, true), true);
            // Die Quest ist zu Ende: ihre Zeit laeuft bis zu dieser Zeile (wie bei SkyHanni), nach dem Boss - erst dort steht fest, welcher es war
            if (QUEST_COMPLETE.matcher(line).find()) {
                finishQuest(CLOCK.questCompleted(now));
            }
            // Der Kasten ist da: die wartenden Zeilen folgen dreiviertel Sekunde danach
            if (!OUTBOX.isEmpty()) {
                outboxDelay = OUTPUT_DELAY_TICKS;
            }
        }
    }

    /** Ein Lobby-Wechsel: die Sitzung beginnt neu, die persoenlichen Zeiten bleiben */
    public static void newLobby() {
        SESSION.clear();
        CLOCK.lobbyChanged();
        ShokiMod.LOGGER.info("[Slayer] new lobby - the kill time session starts over");
    }

    /**
     * {@code /shoki topslayer}: die drei schnellsten Boss-Zeiten je Slayer im Chat, nur von der hoechsten Stufe (beim Vampire IV und V). Gelesen werden die gespeicherten Zeiten der Funktion "Boss Kill time" - der
     * Befehl geht auch, wenn ihr Schalter inzwischen aus ist; ohne gespeicherte Zeit sagt er, woher sie kommen.
     */
    public static void showTop() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records(), SlayerKillStats.TOP_COUNT);
        if (top.isEmpty()) {
            client.player.sendSystemMessage(prefix().append(Component.literal(
                    "No slayer kill times yet. Turn on Slayer > Boss Kill time and kill a boss - every boss gets its own list.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        client.player.sendSystemMessage(prefix().append(Component.literal("Top " + SlayerKillStats.TOP_COUNT + " slayer kill times").withStyle(ChatFormatting.AQUA)));
        for (SlayerKillStats.Top entry : top) {
            MutableComponent line = Component.literal("  " + entry.key() + ": ").withStyle(ChatFormatting.WHITE);
            for (int i = 0; i < entry.best().size(); i++) {
                line.append(Component.literal(i == 0 ? "" : "  ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal("#" + (i + 1) + " ").withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                line.append(Component.literal(SlayerKillStats.format(entry.best().get(i), locale)).withStyle(ChatFormatting.GREEN));
            }
            client.player.sendSystemMessage(line);
        }
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

    /**
     * @param boxSeen ob die Abschlussmeldung ("SLAYER QUEST COMPLETE!") schon da ist: dann folgen die Zeilen gleich, sonst warten sie auf ihren Kasten
     */
    private static void finish(SlayerKillClock.Kill kill, boolean boxSeen) {
        // Keine Zeit: kein Beginn bekannt, der Kampf wartet noch auf seine Zeile, oder er ist schon gezaehlt (die Abschlussmeldung nach "Slayer Kill")
        if (kill == null) {
            return;
        }
        long bossMillis = kill.bossNanos() / 1_000_000L;
        // Die Zeit von der Annahme bis zum Tod - nur fuers Log; gezeigt wird die bis zur Abschlussmeldung (finishQuest)
        long questToDeathMillis = kill.questNanos() < 0L ? -1L : kill.questNanos() / 1_000_000L;
        if (!SlayerKillStats.plausible(bossMillis)) {
            ShokiMod.LOGGER.info("[Slayer] kill time {} ms of '{}' is out of range - ignored", bossMillis, kill.key());
            return;
        }
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        ModConfig.SlayerCategory cfg = ModConfig.INSTANCE.slayer;

        List<Long> times = records().computeIfAbsent(kill.key(), k -> new ArrayList<>());
        SlayerKillStats.Standing standing = SlayerKillStats.record(times, bossMillis);
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Slayer] {}: {} (end: {}; quest accepted to death: {} ms)", kill.key(), SlayerKillStats.bossLine(bossMillis, standing, locale),
                kill.byKillLine() ? "'Your Slayer Kill' line" : "first death message", questToDeathMillis);

        if (cfg.bossKillTime) {
            OUTBOX.add(prefix().append(Component.literal("Boss Kill time: ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(SlayerKillStats.format(bossMillis, locale)).withStyle(ChatFormatting.GREEN))
                    .append(standing.best()
                            ? Component.literal("  " + SlayerKillStats.BEST_TEXT).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            : Component.empty())
                    .append(Component.literal("  (" + SlayerKillStats.rankText(standing) + ")").withStyle(ChatFormatting.GRAY)));
        }
        outboxDelay = boxSeen ? OUTPUT_DELAY_TICKS : WAIT_FOR_BOX_TICKS;
    }

    /**
     * Die Zeit der ganzen Quest: von der Annahme bis zur Zeile "SLAYER QUEST COMPLETE!" (wie bei SkyHanni), mit dem Durchschnitt der Sitzung dahinter.
     * Dauerte die Quest laenger als zwei Minuten, steht sie als "not counted" da und geht nicht in den Durchschnitt ein.
     *
     * @param done die Quest, oder null (kein Beginn bekannt)
     */
    private static void finishQuest(SlayerKillClock.QuestDone done) {
        if (done == null) {
            return;
        }
        long questMillis = done.nanos() / 1_000_000L;
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        SlayerKillStats.Session session = SESSION.computeIfAbsent(done.key(), k -> new SlayerKillStats.Session());
        // Eine Quest ueber zwei Minuten ist "nicht gewertet": Sie steht im Chat, geht aber nicht in den Durchschnitt ein
        boolean counted = session.addQuest(questMillis);
        long average = session.average();
        ShokiMod.LOGGER.info("[Slayer] {}: {}", done.key(), SlayerKillStats.questLine(questMillis, average, counted, locale));
        if (!ModConfig.INSTANCE.slayer.questKillTime) {
            return;
        }
        MutableComponent line = prefix().append(Component.literal("Quest Kill time: ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(SlayerKillStats.format(questMillis, locale)).withStyle(ChatFormatting.GREEN));
        if (!counted) {
            line.append(Component.literal("  (" + SlayerKillStats.NOT_COUNTED_TEXT + ")").withStyle(ChatFormatting.RED));
        }
        if (average >= 0L) {
            line.append(Component.literal("  (Avg. Time this Session: " + SlayerKillStats.format(average, locale) + ")").withStyle(ChatFormatting.GRAY));
        }
        OUTBOX.add(line);
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
