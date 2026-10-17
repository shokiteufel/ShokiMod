package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ReminderCategory;
import com.shokiteufel.shokimod.scanner.PestTraps;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Die Erinnerung, wenn die Pest-Fallen voll sind.
 *
 * Eine volle Falle faengt nichts mehr - sie steht herum, bis jemand sie leert.
 *
 * Gelesen wird der Stand aus der Tab-Liste, die Hypixel selbst schreibt. Und dort
 * steht er nur im Garden: Die drei Zeilen gehoeren zu den Garden-Widgets, und die
 * schickt der Server ausserhalb nicht - auch Skysoft und SkyHanni kommen dort an
 * keine Zahl. Eine Warnung "die Fallen sind gerade voll geworden" ist beim Angeln
 * also nicht zu haben, ohne sie zu erfinden.
 *
 * Was sich dagegen sagen laesst, ist das hier, und beides deckt den teuren Fall ab:
 *
 * <ul>
 *   <li><b>Im Garden:</b> sobald genug Fallen voll sind, mit Wiederholung.</li>
 *   <li><b>Beim Verlassen:</b> einmal, wenn man den Garden mit vollen Fallen
 *       zurueckgelassen hat. Genau das ist der Fehler, der Stunden kostet -
 *       weggehen und es nicht merken.</li>
 * </ul>
 */
public final class PestReminder {

    /** Nur alle paar Sekunden nachsehen - die Zahl aendert sich nicht im Tick-Takt */
    private static final long CHECK_INTERVAL_MILLIS = 3_000L;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private static long lastCheck = 0L;
    private static long lastReminder = 0L;
    private static boolean armed = true;

    /** Der letzte Stand aus dem Garden - er ueberlebt den Warp, die Fallen ja auch */
    private static int lastFull = 0;
    private static int lastPlaced = 0;
    private static long lastSeenAt = 0L;
    private static boolean leftWarned = false;
    private static boolean widgetWarned = false;

    /**
     * Der Garten wurde mit nicht vollen Fallen verlassen: seit wann, und wie viele waren voll.
     * Null heisst: kein Wecker gestellt. Ueberlebt den Weltwechsel - der Garten zu verlassen
     * ist ja einer.
     */
    private static long awayAt = 0L;
    private static int awayFull = 0;
    /** War beim letzten Blick der Garten das Ziel? Daran erkennt man den Moment des Gehens */
    private static boolean wasInGarden = false;

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

    /**
     * Nach einem Weltwechsel gilt der gelesene Stand nicht mehr - der gemerkte schon.
     *
     * Der Warp aus dem Garden ist ein Weltwechsel: Wer hier alles vergisst, kann
     * hinterher nicht mehr sagen, wie es beim Weggehen aussah, und genau das ist die
     * Warnung, auf die es ankommt.
     */
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

        int threshold = Math.max(1, cfg().pestTrapAt);
        if (!inGarden()) {
            // Draussen gibt es keine Zahl. Aber wenn die letzte aus dem Garden sagt,
            // dass dort volle Fallen stehen, gehoert das einmal gesagt
            if (!leftWarned && lastSeenAt > 0 && lastFull >= threshold) {
                leftWarned = true;
                remind(client, lastFull, true);
            }
            armAway(now, threshold);
            checkAway(client, now);
            return;
        }

        // Wieder im Garden: Hier gibt es echte Zahlen, der Wecker ist nicht mehr noetig
        awayAt = 0L;
        wasInGarden = true;

        PestTraps.hintIfMissing();
        hintInChat(client);
        if (!PestTraps.available()) return;

        int full = PestTraps.fullCount();
        lastFull = full;
        lastPlaced = PestTraps.placed();
        lastSeenAt = now;
        // Im Garden faengt die Warnung fuers Weggehen wieder von vorn an
        leftWarned = false;

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
        remind(client, full, false);
    }

    /**
     * Ist der Spieler im Garden? Nur dort schickt Hypixel die Fallen-Zeilen.
     *
     * Zwei Wege, und einer genuegt: der Gebietsname aus der Tab-Liste, oder die Fallen-Zeilen
     * selbst. Letztere gibt es nirgends sonst - wer sie sieht, steht im Garden, auch wenn die
     * "Area:"-Zeile in seiner Tab-Liste fehlt oder anders heisst.
     */
    private static boolean inGarden() {
        if (PestTraps.available()) return true;
        String area = GameState.Server.map;
        if (area == null) return false;
        String lower = area.toLowerCase(Locale.ROOT);
        return lower.equals("garden") || lower.equals("the garden");
    }

    /**
     * Beim Verlassen des Gartens: Waren die Fallen noch nicht voll, wird ein Wecker gestellt.
     * Nach Ablauf der eingestellten Zeit sind sie es voraussichtlich - und von draussen sieht man
     * das nicht.
     */
    private static void armAway(long now, int threshold) {
        if (!wasInGarden) return;
        wasInGarden = false;

        long minutes = cfg().pestTrapAwayMinutes;
        // Ohne gelesene Zahl oder ohne gestellte Fallen gibt es nichts, was voll werden koennte
        if (minutes <= 0 || lastSeenAt <= 0 || lastPlaced <= 0 || lastFull >= threshold) return;

        awayAt = now;
        awayFull = lastFull;
        ShokiMod.LOGGER.info("[Pests] Left the garden with {} of {} traps full - warning again in {} min",
                lastFull, lastPlaced, minutes);
    }

    /** Ist die Zeit seit dem Verlassen um, kommt die Warnung - einmal */
    private static void checkAway(Minecraft client, long now) {
        if (awayAt <= 0) return;

        long minutes = cfg().pestTrapAwayMinutes;
        if (minutes <= 0) {
            awayAt = 0L;
            return;
        }
        if (now - awayAt < minutes * 60_000L) return;

        awayAt = 0L;
        client.player.sendSystemMessage(awayMessage(minutes, awayFull, lastPlaced));
        sound(client);
        ShokiMod.LOGGER.info("[Pests] Away alert after {} min", minutes);
    }

    /**
     * Der Hinweis auf das fehlende Widget - im Chat, nicht nur im Log.
     *
     * Ohne die Zeilen kann die Warnung nie kommen, und woran das liegt, sieht man
     * nirgends. Einmal je Sitzung, und nur im Garden: dort laesst es sich auch
     * einschalten, draussen bietet Hypixel die Garden-Widgets gar nicht an.
     */
    private static void hintInChat(Minecraft client) {
        if (widgetWarned || PestTraps.available() || !PestTraps.waitingForWidget()) return;

        widgetWarned = true;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("The pest trap warning needs Hypixel's Pest Traps widget. "
                                + "Switch it on here in the garden - the tab list settings only offer it here.")
                        .withStyle(ChatFormatting.YELLOW)));
    }

    private static void remind(Minecraft client, int full, boolean afterLeaving) {
        client.player.sendSystemMessage(message(full, PestTraps.fullTraps(), afterLeaving));
        sound(client);
        ShokiMod.LOGGER.info("[Pests] {} traps full{}", full, afterLeaving ? " (left the garden)" : "");
    }

    /** Die Zeile: wie viele voll sind, welche das sind, und ein Knopf zum Hinreisen */
    static Component message(int full, List<Integer> traps, boolean afterLeaving) {
        StringBuilder hover = new StringBuilder("Full traps:");
        if (traps.isEmpty()) hover.append(" ").append(full);
        for (int number : traps) hover.append("\n- #").append(number);
        if (lastPlaced > 0) hover.append("\nPlaced: ").append(lastPlaced);

        List<Integer> hungry = PestTraps.trapsWithoutBait();
        if (!hungry.isEmpty()) hover.append("\nNo bait: ").append(hungry);
        if (afterLeaving && lastSeenAt > 0) {
            hover.append("\nRead at ").append(LocalTime.now().withNano(0).format(CLOCK))
                    .append(" - the garden does not report while you are away");
        }

        String text = afterLeaving
                ? "You left the garden with " + full + (full == 1 ? " full trap" : " full traps")
                : (full == 1 ? "1 pest trap is full" : full + " pest traps are full");

        MutableComponent line = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GOLD)
                        .withStyle(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Component.literal(hover.toString())))))
                .append(Component.literal(" "));

        return line.append(goButton());
    }

    /** Die Zeile nach der Rueckkehr-Frist: Die Fallen sind voraussichtlich voll */
    static Component awayMessage(long minutes, int full, int placed) {
        String hover = "When you left: " + full + " of " + placed + " traps full"
                + "\nThe garden does not report while you are away - go and look";
        String text = "You left the garden " + minutes + (minutes == 1 ? " minute" : " minutes")
                + " ago - the pest traps are probably full by now";

        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GOLD)
                        .withStyle(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Component.literal(hover)))))
                .append(Component.literal(" "))
                .append(goButton());
    }

    /** [Go]: fuehrt den eingestellten Befehl aus, falls einer eingestellt ist */
    private static MutableComponent goButton() {
        String command = cfg().pestTrapCommand == null ? "" : cfg().pestTrapCommand.trim();
        MutableComponent click = Component.literal("[Go]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        if (!command.isEmpty()) {
            String run = command.startsWith("/") ? command : "/" + command;
            click = click.withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand(run))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run " + run))));
        }
        return click;
    }

    /** Knopf in den Einstellungen: die Zeile mit einem Beispiel */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(message(3, List.of(1, 2, 3), false));
        client.player.sendSystemMessage(awayMessage(Math.max(1, cfg().pestTrapAwayMinutes), 1, 3));
        sound(client);
    }

    /**
     * Der Ton zur Warnung: die gewaehlte Datei, sonst das eingebaute Pling.
     *
     * Fehlt die gewaehlte Datei (umbenannt, geloescht), faellt es aufs Pling zurueck - eine
     * Warnung, die stumm bleibt, ist schlimmer als eine mit dem falschen Ton.
     */
    private static void sound(Minecraft client) {
        if (!cfg().pestTrapSound) return;

        String file = cfg().pestTrapSoundFile;
        if (file != null && !file.isBlank() && soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), PestReminder.class);
            return;
        }
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
    }

    private static boolean soundExists(String file) {
        try {
            return Files.isRegularFile(CustomSoundPlayer.SOUND_DIRECTORY.resolve(file));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
