package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

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
import java.util.Locale;

/**
 * Die gemeinsame Mechanik der Garden-Warnungen: volle Pest-Fallen und wartende Besucher.
 *
 * Beide folgen derselben Regel. Gelesen wird der Stand aus der Tab-Liste, die Hypixel selbst
 * schreibt, und dort steht er nur im Garden - die Widgets schickt der Server ausserhalb nicht.
 * Eine Warnung "es ist gerade voll geworden" ist beim Angeln also nicht zu haben, ohne sie zu
 * erfinden. Was sich sagen laesst, deckt den teuren Fall ab:
 *
 * <ul>
 *   <li><b>Im Garden:</b> sobald der Stand reicht, mit Wiederholung.</li>
 *   <li><b>Beim Verlassen:</b> einmal, wenn man den Garden mit vollem Stand zurueckgelassen hat.
 *       Genau das ist der Fehler, der Stunden kostet - weggehen und es nicht merken.</li>
 *   <li><b>Nach {@value #AWAY_MINUTES} Minuten draussen:</b> einmal, wenn der Stand beim Gehen
 *       noch nicht reichte. Von draussen sieht man ihn nicht; nach der Frist ist er es
 *       voraussichtlich.</li>
 * </ul>
 *
 * Was sich unterscheidet - welche Zeilen gelesen werden, was "reicht" heisst, der Wortlaut -
 * steht in den Unterklassen. Der Zustand gehoert je Warnung dazu, deshalb keine statischen Felder.
 */
abstract class GardenReminder {

    /** Nur alle paar Sekunden nachsehen - die Zahlen aendern sich nicht im Tick-Takt */
    private static final long CHECK_INTERVAL_MILLIS = 3_000L;
    /** So lange nach dem Verlassen des Gartens, bis die Warnung von draussen kommt */
    static final long AWAY_MINUTES = 60L;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private long lastCheck = 0L;
    private long lastReminder = 0L;
    private boolean armed = true;

    /** Der letzte Stand aus dem Garden - er ueberlebt den Warp, der Garden ja auch */
    int lastCount = 0;
    int lastTotal = 0;
    long lastSeenAt = 0L;
    boolean lastReached = false;
    private boolean leftWarned = false;
    private boolean widgetWarned = false;

    /**
     * Der Garten wurde mit nicht vollem Stand verlassen: seit wann, wie viel war es, und wie viel
     * haette es sein koennen. Null heisst: kein Wecker gestellt. Ueberlebt den Weltwechsel - den
     * Garten zu verlassen ist ja einer.
     */
    long awayAt = 0L;
    int awayCount = 0;
    int awayTotal = 0;
    /** War beim letzten Blick der Garten das Ziel? Daran erkennt man den Moment des Gehens */
    boolean wasInGarden = false;

    // ==========================================
    // Was die Unterklassen liefern
    // ==========================================

    /** Kurzname fuers Log, etwa "[Pests]" */
    abstract String tag();

    /** Ist die Warnung eingeschaltet? */
    abstract boolean isOn();

    /** Wurden die Zeilen schon gelesen? */
    abstract boolean available();

    /** Der Stand jetzt: wie viele Fallen voll, wie viele Besucher da */
    abstract int count();

    /** Wie viel es hoechstens sein kann: gestellte Fallen, Platz in der Warteschlange. 0: nichts zu fuellen */
    abstract int total();

    /** Reicht der Stand jetzt fuer eine Warnung? */
    abstract boolean reached();

    /** Soll nach dem Verlassen des Gartens gewarnt werden? */
    abstract boolean awayOn();

    abstract long repeatMillis();

    abstract boolean soundOn();

    abstract String soundFile();

    /** Der Befehl hinter [Go] */
    abstract String command();

    /** Hat das Widget lange genug gefehlt, um es im Chat zu sagen? */
    abstract boolean waitingForWidget();

    /** Einmal je Welt: der Hinweis im Log, dass das Widget fehlt */
    abstract void hintIfMissing();

    /** Der Satz im Chat, wenn das Widget fehlt */
    abstract String widgetHint();

    /** Die Warnzeile: so viele sind es, und ob man schon weg ist */
    abstract Component message(int count, boolean afterLeaving);

    /** Die Zeile nach der Frist: so viele waren es beim Gehen, von so vielen moeglichen */
    abstract Component awayMessage(long minutes, int count, int total);

    /** Die gelesenen Zeilen vergessen */
    abstract void resetScanner();

    // ==========================================
    // Die Mechanik
    // ==========================================

    /**
     * Nach einem Weltwechsel gilt der gelesene Stand nicht mehr - der gemerkte schon.
     *
     * Der Warp aus dem Garden ist ein Weltwechsel: Wer hier alles vergisst, kann hinterher nicht
     * mehr sagen, wie es beim Weggehen aussah, und genau das ist die Warnung, auf die es ankommt.
     */
    final void clear() {
        armed = true;
        lastReminder = 0L;
        resetScanner();
    }

    final void tick(Minecraft client) {
        if (!isOn() || client.player == null || !GameState.Server.isSkyblock()) return;

        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MILLIS) return;
        lastCheck = now;

        if (!inGarden()) {
            // Draussen gibt es keine Zahl. Aber wenn die letzte aus dem Garden sagt, dass dort
            // alles voll ist, gehoert das einmal gesagt
            if (!leftWarned && lastSeenAt > 0 && lastReached) {
                leftWarned = true;
                remind(client, lastCount, true);
            }
            armAway(now);
            checkAway(client, now);
            return;
        }

        // Wieder im Garden: Hier gibt es echte Zahlen, der Wecker ist nicht mehr noetig
        awayAt = 0L;
        wasInGarden = true;

        hintIfMissing();
        hintInChat(client);
        if (!available()) return;

        lastCount = count();
        lastTotal = total();
        lastReached = reached();
        lastSeenAt = now;
        // Im Garden faengt die Warnung fuers Weggehen wieder von vorn an
        leftWarned = false;

        if (!lastReached) {
            // Geleert: der naechste volle Stand darf wieder melden
            armed = true;
            return;
        }

        long repeat = repeatMillis();
        boolean again = repeat > 0 && lastReminder > 0 && now - lastReminder >= repeat;
        if (!armed && !again) return;

        armed = false;
        lastReminder = now;
        remind(client, lastCount, false);
    }

    /**
     * Ist der Spieler im Garden? Nur dort schickt Hypixel die Zeilen.
     *
     * Zwei Wege, und einer genuegt: der Gebietsname aus der Tab-Liste, oder die Zeilen selbst.
     * Letztere gibt es nirgends sonst - wer sie sieht, steht im Garden, auch wenn die "Area:"-Zeile
     * in seiner Tab-Liste fehlt oder anders heisst.
     */
    final boolean inGarden() {
        if (available()) return true;
        String area = GameState.Server.map;
        if (area == null) return false;
        String lower = area.toLowerCase(Locale.ROOT);
        return lower.equals("garden") || lower.equals("the garden");
    }

    /**
     * Beim Verlassen des Gartens: Reichte der Stand noch nicht, wird ein Wecker gestellt. Nach
     * Ablauf der Frist reicht er voraussichtlich - und von draussen sieht man das nicht.
     */
    final void armAway(long now) {
        if (!wasInGarden) return;
        wasInGarden = false;

        // Ohne gelesene Zahl oder ohne etwas, das voll werden koennte, gibt es nichts zu warnen
        if (!awayOn() || lastSeenAt <= 0 || lastTotal <= 0 || lastReached) return;

        awayAt = now;
        awayCount = lastCount;
        awayTotal = lastTotal;
        ShokiMod.LOGGER.info("{} Left the garden with {} of {} - warning again in {} min",
                tag(), lastCount, lastTotal, AWAY_MINUTES);
    }

    /** Ist die Frist seit dem Verlassen um, kommt die Warnung - einmal */
    final void checkAway(Minecraft client, long now) {
        if (awayAt <= 0) return;

        if (!awayOn()) {
            awayAt = 0L;
            return;
        }
        if (now - awayAt < AWAY_MINUTES * 60_000L) return;

        awayAt = 0L;
        client.player.sendSystemMessage(awayMessage(AWAY_MINUTES, awayCount, awayTotal));
        sound(client);
        ShokiMod.LOGGER.info("{} Away alert after {} min", tag(), AWAY_MINUTES);
    }

    /**
     * Der Hinweis auf das fehlende Widget - im Chat, nicht nur im Log.
     *
     * Ohne die Zeilen kann die Warnung nie kommen, und woran das liegt, sieht man nirgends.
     * Einmal je Sitzung, und nur im Garden: dort laesst es sich auch einschalten, draussen bietet
     * Hypixel die Garden-Widgets gar nicht an.
     */
    private void hintInChat(Minecraft client) {
        if (widgetWarned || available() || !waitingForWidget()) return;

        widgetWarned = true;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(widgetHint()).withStyle(ChatFormatting.YELLOW)));
    }

    private void remind(Minecraft client, int count, boolean afterLeaving) {
        client.player.sendSystemMessage(message(count, afterLeaving));
        sound(client);
        ShokiMod.LOGGER.info("{} {} reached{}", tag(), count, afterLeaving ? " (left the garden)" : "");
    }

    // ==========================================
    // Zeile und Ton
    // ==========================================

    /** "[ShokiMod] Text" - der Text mit Hover - und dahinter der Knopf */
    final MutableComponent line(String text, String hover) {
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GOLD)
                        .withStyle(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Component.literal(hover)))))
                .append(Component.literal(" "))
                .append(goButton());
    }

    /** Wann der Stand gelesen wurde - fuer den Hover der Zeile nach dem Verlassen */
    final String readAt() {
        return "\nRead at " + LocalTime.now().withNano(0).format(CLOCK)
                + " - the garden does not report while you are away";
    }

    /** [Go]: fuehrt den eingestellten Befehl aus, falls einer eingestellt ist */
    private MutableComponent goButton() {
        String command = command() == null ? "" : command().trim();
        MutableComponent click = Component.literal("[Go]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        if (!command.isEmpty()) {
            String run = command.startsWith("/") ? command : "/" + command;
            click = click.withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand(run))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run " + run))));
        }
        return click;
    }

    /**
     * Der Ton zur Warnung: die gewaehlte Datei, sonst das eingebaute Pling.
     *
     * Fehlt die gewaehlte Datei (umbenannt, geloescht), faellt es aufs Pling zurueck - eine
     * Warnung, die stumm bleibt, ist schlimmer als eine mit dem falschen Ton.
     */
    final void sound(Minecraft client) {
        if (!soundOn()) return;

        String file = soundFile();
        if (file != null && !file.isBlank() && soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), getClass());
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
