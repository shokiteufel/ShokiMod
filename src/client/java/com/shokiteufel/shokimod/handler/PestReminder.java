package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.GardenTrapsCategory;
import com.shokiteufel.shokimod.scanner.PestTraps;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Die Erinnerung, wenn die Pest-Fallen voll sind.
 *
 * Eine volle Falle faengt nichts mehr - sie steht herum, bis jemand sie leert. Wann gewarnt wird,
 * und warum nur im Garten und beim Verlassen, steht in {@link GardenReminder}; hier steht, was
 * nur die Fallen betrifft: welche Zeilen gelesen werden und wie die Warnung klingt.
 */
public final class PestReminder extends GardenReminder {

    private static final PestReminder INSTANCE = new PestReminder();

    private PestReminder() {
    }

    private static GardenTrapsCategory cfg() {
        return ModConfig.INSTANCE.garden.traps;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
    }

    public static void reset() {
        INSTANCE.clear();
    }

    /** Knopf in den Einstellungen: die Zeilen mit einem Beispiel */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(INSTANCE.message(3, List.of(1, 2, 3), false));
        client.player.sendSystemMessage(INSTANCE.awayMessage(AWAY_MINUTES, 1, 3));
        INSTANCE.sound(client);
    }

    @Override
    String tag() {
        return "[Pests]";
    }

    @Override
    boolean isOn() {
        return cfg().alert;
    }

    @Override
    boolean available() {
        return PestTraps.available();
    }

    @Override
    int count() {
        return PestTraps.fullCount();
    }

    /** Gestellte Fallen. Ohne gestellte gibt es nichts, was voll werden koennte */
    @Override
    int total() {
        return PestTraps.placed();
    }

    @Override
    boolean reached() {
        return PestTraps.fullCount() >= Math.max(1, cfg().warnAt);
    }

    @Override
    boolean awayOn() {
        return cfg().alertOutside;
    }

    @Override
    long repeatMillis() {
        return Math.max(0, cfg().repeatMinutes) * 60_000L;
    }

    @Override
    boolean soundOn() {
        return cfg().sound;
    }

    @Override
    String soundFile() {
        return cfg().soundFile;
    }

    @Override
    String command() {
        return cfg().command;
    }

    @Override
    boolean waitingForWidget() {
        return PestTraps.waitingForWidget();
    }

    @Override
    void hintIfMissing() {
        PestTraps.hintIfMissing();
    }

    @Override
    String widgetHint() {
        return "The pest trap warning needs Hypixel's Pest Traps widget. "
                + "Switch it on here in the garden - the tab list settings only offer it here.";
    }

    @Override
    void resetScanner() {
        PestTraps.reset();
    }

    @Override
    Component message(int count, boolean afterLeaving) {
        return message(count, PestTraps.fullTraps(), afterLeaving);
    }

    /** Die Zeile: wie viele voll sind, welche das sind, und ein Knopf zum Hinreisen */
    Component message(int full, List<Integer> traps, boolean afterLeaving) {
        StringBuilder hover = new StringBuilder("Full traps:");
        if (traps.isEmpty()) hover.append(" ").append(full);
        for (int number : traps) hover.append("\n- #").append(number);
        if (lastTotal > 0) hover.append("\nPlaced: ").append(lastTotal);

        List<Integer> hungry = PestTraps.trapsWithoutBait();
        if (!hungry.isEmpty()) hover.append("\nNo bait: ").append(hungry);
        if (afterLeaving && lastSeenAt > 0) hover.append(readAt());

        String text = afterLeaving
                ? "You left the garden with " + full + (full == 1 ? " full trap" : " full traps")
                : (full == 1 ? "1 pest trap is full" : full + " pest traps are full");

        return line(text, hover.toString());
    }

    /** Die Zeile nach der Frist: Die Fallen sind voraussichtlich voll */
    @Override
    Component awayMessage(long minutes, int full, int placed) {
        String hover = "When you left: " + full + " of " + placed + " traps full"
                + "\nThe garden does not report while you are away - go and look";
        String text = "You left the garden " + minutes + (minutes == 1 ? " minute" : " minutes")
                + " ago - the pest traps are probably full by now";

        return line(text, hover);
    }
}
